package ru.tecon.instantData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.Utils;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.isacom.*;
import ru.tecon.model.DataModel;
import ru.tecon.model.ValueModel;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.ControllerSocket;

import javax.naming.NamingException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Класс для работы с мгновенными данными
 */
public final class InstantDataService {

    private static Logger logger = LoggerFactory.getLogger(InstantDataService.class);

    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

    private InstantDataService() {

    }

    /**
     * Метод закрывает службу проверки запроса на мгновенные данные
     */
    public static void stopService() {
        try {
            Utils.getInstantEJB().removeServer(DriverProperty.getInstance().getServerName());
        } catch (NamingException e) {
            logger.warn("Error stop instant data service", e);
        }
        if (Objects.nonNull(service)) {
            future.cancel(true);
            service.shutdown();
        }
    }

    /**
     * Метод запускает службу которая обрабатывает запросы на мгновенные данные
     */
    public static void startService() {
        service = Executors.newSingleThreadScheduledExecutor();
        future = service.scheduleWithFixedDelay(() -> {
            try {
                if (!Utils.getInstantEJB().isSubscribed(DriverProperty.getInstance().getServerName())) {
                    Utils.getInstantEJB().addServer(DriverProperty.getInstance().getServerName(),
                            DriverProperty.getInstance().getServerURI(),
                            DriverProperty.getInstance().getServerPort(),
                            "ejb/MFK1500InstantData");
                }
            } catch (NamingException e) {
                logger.warn("Error check instant data service", e);
            }
        }, 0, 30, TimeUnit.MINUTES);
    }

    /**
     * Метод для выгрузки мгновенных данных от контроллера и отправки их в базу
     * @param url ip адрес контроллера
     * @param rowID идентификатор строки запроса на мгновенные данные.
     *              Требуется для проставления статуса выполнения запроса.
     */
    public static void uploadInstantData(String url, String rowID) {
        String errorPath = DriverProperty.getInstance().getServerName() + "_" + url;

        try (ControllerSocket socket = new ControllerSocket(url);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            if (EchoSocketServer.isBlocked(url, BlockType.TRAFFIC)) {
                logger.warn("Traffic block {}", errorPath);
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "Превышение трафика по объекту '" + errorPath + "'");
                return;
            }

            List<DataModel> parameters = Utils.loadRMI().loadObjectInstantParameters(DriverProperty.getInstance().getServerName(), url);

            logger.info("Load instantData for {} parameters count {}", errorPath, parameters.size());

            List<IsacomModel> isacomModels = new ArrayList<>();
            for (DataModel model : parameters) {
                String[] infoSplit = model.getParamName().split("::");
                if ((infoSplit.length == 2) && (infoSplit[0].split(":").length == 2)) {
                    String[] paramSplit = infoSplit[1].split(":");
                    switch (paramSplit.length) {
                        case 1:
                            if (IsacomSimpleTypes.isContains(paramSplit[0])) {
                                isacomModels.add(new IsacomModel(infoSplit[0].split(":")[0], new IsacomType() {
                                    @Override
                                    public String getName() {
                                        return paramSplit[0];
                                    }

                                    @Override
                                    public int getSize() {
                                        return IsacomSimpleTypes.valueOf(paramSplit[0]).getSize();
                                    }
                                }));
                            }
                            break;
                        case 4:
                            if (IsacomSimpleTypes.isContains(paramSplit[3])) {
                                String split = infoSplit[0].split(":")[0];
                                String paramName = split.substring(0, split.lastIndexOf("_"));
                                IsacomModel isacomModel = new IsacomModel(paramName, new IsacomType() {
                                    @Override
                                    public String getName() {
                                        return paramSplit[0];
                                    }

                                    @Override
                                    public int getSize() {
                                        return Integer.parseInt(paramSplit[1]);
                                    }

                                    @Override
                                    public int getOffset() {
                                        return Integer.parseInt(paramSplit[2]);
                                    }
                                });

                                isacomModel.setSubModel(new IsacomModel(split, new IsacomType() {
                                    @Override
                                    public String getName() {
                                        return paramSplit[3];
                                    }

                                    @Override
                                    public int getSize() {
                                        return IsacomSimpleTypes.valueOf(paramSplit[3]).getSize();
                                    }
                                }));

                                isacomModels.add(isacomModel);
                            }
                            break;
                        case 5:
                            // TODO Реализовать вариант для String размер string указан в sysInfo.
                            //  Переменные типа String пока нигде не требуются.
                            break;
                    }
                }
            }

            if (isacomModels.isEmpty()) {
                logger.warn("There is a request for instant data, but the parameters could not be recognized {}", errorPath);
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "По объекту '" + errorPath + "' не подписан ни один параметр");
                return;
            }

            try {
                IsacomProtocol.createVariableList(in, out, isacomModels);
            } catch (IsacomException e) {
                logger.warn("Problem reading response to creating variables {}", errorPath);
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "Проблема чтения ответа на создание переменных");
                return;
            }

            try {
                IsacomProtocol.readVariableList(in, out, isacomModels);
            } catch (IsacomException e) {
                logger.warn("Problem reading response to reading variables by list {}", errorPath);
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "Проблема чтения ответа на чтение переменных по списку");
                return;
            }

            for (IsacomModel isacomModel: isacomModels) {
                String paramName;

                if (isacomModel.getSubModel() != null) {
                    paramName = isacomModel.getSubModel().getName();
                } else {
                    paramName = isacomModel.getName();
                }

                for (DataModel model: parameters) {
                    if (model.getParamName().startsWith(paramName + ":Текущие данные")) {
                        model.addData(new ValueModel(isacomModel.getValue(), LocalDateTime.now()));
                        break;
                    }
                }
            }

            int parametersCount = parameters.size();
            parameters.removeIf(dataModel -> dataModel.getData().isEmpty());

            logger.info("Upload instantData for {} parameters with data count {}", errorPath, parameters.size());

            if (parameters.isEmpty()) {
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "Полученно 0 мгновенных значений");
            } else {
                Utils.loadRMI().putInstantData(parameters);

                String objectName = String.valueOf(parameters.get(0).getObjectId());

                Utils.getDataUploaderAppEJB().updateCommand(1, rowID, String.valueOf(parameters.get(0).getObjectId()),
                        "Получено " + parameters.size() + " значений из " + parametersCount +
                                " слинкованных параетров по объекту " + objectName);
            }
        } catch (ConnectException e) {
            logger.warn("Socket connect exception", e);
            try {
                Utils.getDataUploaderAppEJB().updateCommand(0, rowID, "Error",
                        "Невозможно подключиться к прибору");
            } catch (NamingException ex) {
                logger.warn("load RMI exception", ex);
            }
        } catch (NamingException | IOException e) {
            logger.warn("Exception loading instant data", e);
        }
    }
}
