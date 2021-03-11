package ru.tecon.instantData;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.isacom.*;
import ru.tecon.model.DataModel;
import ru.tecon.model.ValueModel;
import ru.tecon.server.EchoSocketServer;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс для работы с мгновенными данными
 */
public final class InstantDataService {

    private static final Logger LOG = Logger.getLogger(InstantDataService.class.getName());

    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

    private InstantDataService() {

    }

    /**
     * Метод закрывает службу проверки запроса на мгновенные данные
     */
    public static void stopService() {
        if (Objects.nonNull(service)) {
            future.cancel(true);
            service.shutdown();
        }
    }

    /**
     * Метод запускает службу которая обрабатывает запросы на мгновенные данные
     */
    public static void startService() {
        if (ProjectProperty.isCheckRequestService()) {
            service = Executors.newSingleThreadScheduledExecutor();
            future = service.scheduleWithFixedDelay(() -> {
                try {
                    Utils.loadRMI().checkInstantRequest(ProjectProperty.getServerName());
                } catch (NamingException e) {
                    LOG.log(Level.WARNING, "error instant data service", e);
                }
            }, 5, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Метод для выгрузки мгновенных данных от контроллера и отправки их в базу
     * @param url ip адрес контроллера
     */
    public static void uploadInstantData(String url) {
        String errorPath = ProjectProperty.getServerName() + "_" + url;

        try (ControllerSocket socket = new ControllerSocket(url);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            if (EchoSocketServer.isBlocked(url)) {
                LOG.info("traffic block");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Превышение трафика по объекту '" + errorPath + "'");
                return;
            }

            List<DataModel> parameters = Utils.loadRMI().loadObjectInstantParameters(ProjectProperty.getServerName(), url);

            LOG.info("load instantData for url: " + url + " parameters count: " + parameters.size());

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
                            // TODO реализовать вариант для String размер string указан в sysInfo
                            break;
                    }
                }
            }

            if (isacomModels.isEmpty()) {
                LOG.warning("Есть запрос на мгновенные данные, но не удалось распознать параметры");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath,
                        "По объекту '" + errorPath + "' не подписан ни один параметр");
                return;
            }

            try {
                IsacomProtocol.createVariableList(in, out, isacomModels);
            } catch (IsacomException e) {
                LOG.warning("Проблема чтения ответа на создание переменных");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Проблема чтения ответа на создание переменных");
                return;
            }

            try {
                IsacomProtocol.readVariableList(in, out, isacomModels);
            } catch (IsacomException e) {
                LOG.warning("Проблема чтения ответа на чтение переменных по списку");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Проблема чтения ответа на чтение переменных по списку");
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

            parameters.removeIf(dataModel -> dataModel.getData().isEmpty());

            LOG.info("upload instantData for url: " + url + " parameters with data count: " + parameters.size());

            Utils.loadRMI().putInstantData(parameters);
        } catch (ConnectException e) {
            LOG.log(Level.WARNING, "socket connect exception", e);
            try {
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Невозможно подключиться к прибору");
            } catch (NamingException ex) {
                LOG.log(Level.WARNING, "load RMI exception", ex);
            }
        } catch (NamingException | IOException e) {
            LOG.log(Level.WARNING, "error with load instant data", e);
        }
    }
}
