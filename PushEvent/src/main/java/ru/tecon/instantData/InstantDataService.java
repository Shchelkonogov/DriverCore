package ru.tecon.instantData;

import ru.tecon.ProjectProperty;
import ru.tecon.model.DataModel;
import ru.tecon.model.ValueModel;
import ru.tecon.Utils;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.MonitorInputStream;
import ru.tecon.traffic.MonitorOutputStream;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Класс для работы с мгновенными данными
 */
public class InstantDataService {

    private static final Logger LOG = Logger.getLogger(InstantDataService.class.getName());
    private static final Map<String, String> methodsMap;

    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

    // Блок для инициализации map методов разбора реализованных простых типов
    static {
        methodsMap = new HashMap<>();
        Method[] method = InstantDataService.class.getMethods();

        for(Method md: method){
            if (md.isAnnotationPresent(InstantTypes.class)) {
                methodsMap.put(md.getAnnotation(InstantTypes.class).name().toString(), md.getName());
            }
        }
    }

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

        try {
            if (EchoSocketServer.isBlocked(url)) {
                LOG.info("traffic block");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Превышение трафика по объекту '" + errorPath + "'");
                return;
            }

            List<DataModel> parameters = Utils.loadRMI().loadObjectInstantParameters(ProjectProperty.getServerName(), url);

            LOG.info("load instantData for url: " + url + " parameters count: " + parameters.size());

            int size = 0;
            List<InstantDataModel> dataModelList = new ArrayList<>();
            for (DataModel model : parameters) {
                String[] infoSplit = model.getParamName().split("::");
                if ((infoSplit.length == 2) && (infoSplit[0].split(":").length == 2)) {
                    String[] paramSplit = infoSplit[1].split(":");
                    switch (paramSplit.length) {
                        case 1:
                            if (InstantDataTypes.isContains(paramSplit[0])) {
                                dataModelList.add(new InstantDataModel(infoSplit[0].split(":")[0], paramSplit[0],
                                        InstantDataTypes.valueOf(paramSplit[0]).getSize()));
                                size += infoSplit[0].split(":")[0].getBytes().length + 1 + paramSplit[0].getBytes().length + 1 + 4;
                            }
                            break;
                        case 4:
                            if (InstantDataTypes.isContains(paramSplit[3])) {
                                String split = infoSplit[0].split(":")[0];
                                String paramName = split.substring(0, split.lastIndexOf("_"));
                                dataModelList.add(new InstantDataModel(paramName, paramSplit[0], Integer.parseInt(paramSplit[1]),
                                        Integer.parseInt(paramSplit[2]), paramSplit[3], split));
                                size += paramName.getBytes().length + 1 + paramSplit[0].getBytes().length + 1 + 4;
                            }
                            break;
                        case 5:
                            // TODO реализовать вариант для String размер string указан в sysInfo и открыть доступ к String в InstantTypes
                            break;
                    }
                }
            }

            if (dataModelList.isEmpty()) {
                LOG.warning("Есть запрос на мгновенные данные, но не удалось распознать параметры");
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath,
                        "По объекту '" + errorPath + "' не подписан ни один параметр");
                return;
            }

            // Открываю подключение к прибору
            byte[] response = new byte[16];

            Socket socket = new Socket(InetAddress.getByName(url), ProjectProperty.getInstantPort());

            Statistic st = EchoSocketServer.getStatistic(url);

            MonitorInputStream monitorIn = new MonitorInputStream(socket.getInputStream());
            monitorIn.setStatistic(st);

            MonitorOutputStream out = new MonitorOutputStream(socket.getOutputStream());
            out.setStatistic(st);

            DataInputStream in = new DataInputStream(new BufferedInputStream(monitorIn));

            // Реализую запрос на создание списка переменных
            ByteBuffer head = ByteBuffer.allocate(16 + size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(6)
                    .putInt(Integer.valueOf("0000001001000001", 2))
                    .putInt(size)
                    .putInt(0);

            for (InstantDataModel instantDataModel : dataModelList) {
                head.put(instantDataModel.getName().getBytes())
                        .put((byte) 0)
                        .put(instantDataModel.getType().getBytes())
                        .put((byte) 0)
                        .putInt(instantDataModel.getTypeSize());
            }

            out.write(head.array());
            out.flush();

            if ((in.read(response) != 16) &&
                    (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
                LOG.warning("Проблема чтения ответа на создание переменных: " + Arrays.toString(response));
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Проблема чтения ответа на создание переменных");
                return;
            }

            // Реализую запрос на чтение переменных по списку
            byte[] request = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(7)
                    .putInt(1)
                    .putInt(0)
                    .putInt(0).array();

            out.write(request);
            out.flush();

            if ((in.read(response) != 16) &&
                    (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
                LOG.warning("Проблема чтения ответа на чтение переменных по списку: " + Arrays.toString(response));
                Utils.loadRMI().errorExecuteAsyncRefreshCommand(errorPath, "Проблема чтения ответа на чтение переменных по списку");
                return;
            }

            byte[] data = new byte[ByteBuffer.wrap(Arrays.copyOfRange(response, 8, 12)).order(ByteOrder.LITTLE_ENDIAN).getInt()];

            in.readFully(data);

            int index = 0;
            for (InstantDataModel instantDataModel : dataModelList) {
                if (data[index] == 0) {
                    ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(data, index + 1, index + 1 + instantDataModel.getTypeSize()))
                            .order(ByteOrder.BIG_ENDIAN);

                    if (instantDataModel.isFunctionType()) {
                        buffer = ByteBuffer.wrap(Arrays.copyOfRange(buffer.array(), instantDataModel.getOffset(),
                                instantDataModel.getOffset() + InstantDataTypes.valueOf(instantDataModel.getSubType()).getSize()))
                                .order(ByteOrder.BIG_ENDIAN);
                        loadSimpleType(instantDataModel.getSubType(), instantDataModel.getFullName(), buffer, parameters);
                    } else {
                        loadSimpleType(instantDataModel.getType(), instantDataModel.getName(), buffer, parameters);
                    }

                    index += instantDataModel.getTypeSize();
                }
                index += 1;
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
        } catch (NamingException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException e) {
            LOG.log(Level.WARNING, "error with load instant data", e);
        }
    }

    /**
     * Метод обертка разбирает простые типы
     * @param type имя простого типа
     * @param paramName имя параметра
     * @param buffer буффер с данными
     * @param parameters массив параметров куда кладутся значения
     * @throws NoSuchMethodException ошибка
     * @throws InvocationTargetException ошибка
     * @throws IllegalAccessException ошибка
     */
    private static void loadSimpleType(String type, String paramName, ByteBuffer buffer, List<DataModel> parameters)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (methodsMap.get(type) != null) {
            String value = String.valueOf(InstantDataService.class
                    .getDeclaredMethod(methodsMap.get(type), ByteBuffer.class)
                    .invoke(null, buffer));

            for (DataModel model: parameters) {
                if (model.getParamName().startsWith(paramName + ":Текущие данные")) {
                    model.addData(new ValueModel(value, LocalDateTime.now()));
                    break;
                }
            }
        } else {
            LOG.warning("Отсутствует метод для разбора мгновенного значения " + type);
        }
    }

    @InstantTypes(name = InstantDataTypes.REAL)
    public static float readReal(ByteBuffer buffer) {
        return buffer.getFloat();
    }

    @InstantTypes(name = InstantDataTypes.DINT)
    public static int readDint(ByteBuffer buffer) {
        return buffer.getInt();
    }

    @InstantTypes(name = InstantDataTypes.INT)
    public static short readInt(ByteBuffer buffer) {
        return buffer.getShort();
    }

    @InstantTypes(name = InstantDataTypes.BOOL)
    public static byte readBool(ByteBuffer buffer) {
        return buffer.get();
    }
}
