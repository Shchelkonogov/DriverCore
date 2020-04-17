package ru.tecon.server;

import ru.tecon.*;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.exception.MySocketException;
import ru.tecon.model.DataModel;
import ru.tecon.server.model.ParseDataModel;
import ru.tecon.model.ValueModel;
import ru.tecon.traffic.MonitorInputStream;
import ru.tecon.traffic.MonitorOutputStream;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EchoThread extends Thread {

    private static final Logger LOG = Logger.getLogger(EchoThread.class.getName());

    private Socket socket;
    private String serverName;
    private String objectName = null;
    private LoadOPCRemote opc;

    private Statistic statistic;

    private byte[] markV2 = new byte[0];
    private int protocolVersion = 2;

    EchoThread(Socket clientSocket, String serverName) {
        this.socket = clientSocket;
        this.serverName = serverName;
    }

    public void run() {
        DataInputStream in;
        MonitorOutputStream out;

        try {
            statistic = EchoSocketServer.getStatistic(socket.getInetAddress().getHostAddress());
            statistic.setSocket(socket);
            statistic.setThread(this);

            MonitorInputStream monitorIn = new MonitorInputStream(socket.getInputStream());
            monitorIn.setStatistic(statistic);
            in = new DataInputStream(new BufferedInputStream(monitorIn));

            out = new MonitorOutputStream(socket.getOutputStream());
            out.setStatistic(statistic);
        } catch (IOException e) {
            LOG.warning("run Error create data streams Message: " + e.getMessage()
                    + " Thread: " + this.getId() + " ObjectName: " + objectName);
            return;
        }

        while (true) {
            try {
                // Читаем первые 2 байта в которых указанн размер сообщения
                byte[] data = new byte[2];
                if (in.read(data, 0, 2) != -1) {
                    LOG.info("Thread: " + this.getId() + " Message size: " + Arrays.toString(data));
                    int size = (((data[0] & 0xff) << 8) | (data[1] & 0xff));

                    // Читаем сообщение
                    data = new byte[size];

                    if (size == 0) {
                        statistic.serverErrorBlock();

                        socket.close();
                        LOG.warning("run Error read message Socket close! Thread: " + this.getId() +
                                " ObjectName: " + objectName);
                        return;
                    }
                    in.readFully(data);
                    LOG.info("Thread: " + this.getId() + " Message body: " + Arrays.toString(data));

                    int packageType = data[0] & 0xff;

                    LOG.info("run Data size: " + size + " packageType " + packageType +
                            " Thread: " + this.getId() + " ObjectName: " + objectName);

                    switch (packageType) {
                        case 1: {
                            identify(data, out);
                            break;
                        }
                        case 3: {
                            if (!opc.checkObject(serverName + '_' + objectName, serverName)) {
                                LOG.info("check object return false Thread: " + this.getId() + " objectName: " + objectName);
                                statistic.linkedBlock();
                                socket.close();
                                return;
                            }

                            ArrayList<DataModel> dataModels = opc.loadObjectParameters(serverName + '_' + objectName, serverName);
                            Collections.sort(dataModels);

                            List<ParseDataModel> result = new ArrayList<>();
                            int messagesCount;
                            int messageConfirmSize;

                            switch (protocolVersion) {
                                case 1:
                                    try {
                                        messageConfirmSize = 2;
                                        messagesCount = parse(data, result, 2, protocolVersion);
                                    } catch (Exception e) {
                                        statistic.serverErrorBlock();

                                        socket.close();
                                        LOG.warning("run Parse version 1 exception. Socket close! " + e.getMessage() +
                                                " Data: " + Arrays.toString(data) +
                                                " Thread: " + this.getId() + " ObjectName: " + objectName);
                                        return;
                                    }
                                    break;
                                case 2:
                                    try {
                                        messageConfirmSize = 3;

                                        int markSize = ByteBuffer.wrap(data, 3, 1).get();
                                        markV2 = new byte[markSize];
                                        System.arraycopy(data, 4, markV2, 0, markSize);

                                        messagesCount = parse(data, result, 4 + markSize, protocolVersion);
                                    } catch (Exception e) {
                                        statistic.serverErrorBlock();

                                        socket.close();
                                        LOG.warning("run Parse version 2 exception. Socket close! " + e.getMessage() +
                                                " Data: " + Arrays.toString(data) +
                                                " Thread: " + this.getId() + " ObjectName: " + objectName);
                                        return;
                                    }
                                    break;
                                default:
                                    statistic.serverErrorBlock();

                                    socket.close();
                                    LOG.warning("run Unknown protocolVersion. Socket close! Data: " + Arrays.toString(data) +
                                            " Thread: " + this.getId() + " ObjectName: " + objectName);
                                    return;
                            }

                            Collections.sort(result);

                            dataModels.forEach(dataModel -> {
                                for (ParseDataModel model: result) {
                                    if (Objects.nonNull(model.getValue(dataModel.getParamName())) &&
                                            ((dataModel.getStartTime() == null) ||
                                                    model.getDate().isAfter(dataModel.getStartTime()))) {
                                        dataModel.addData(new ValueModel(String.valueOf(model.getValue(dataModel.getParamName())),
                                                model.getDate()));
                                    }
                                }
                            });

                            dataModels.removeIf(dataModel -> dataModel.getData().isEmpty());

                            opc.putDataWithCalculateIntegrator(dataModels);

                            byte[] response = new byte[messageConfirmSize + 2];
                            response[0] = 0;
                            response[1] = (byte) messageConfirmSize;
                            response[2] = (byte) 4;

                            byte[] messageCountArray = ByteBuffer.allocate(4).putInt(messagesCount).array();

                            switch (messageConfirmSize) {
                                case 3:
                                    response[3] = messageCountArray[2];
                                    response[4] = messageCountArray[3];
                                    break;
                                case 2:
                                default:
                                    response[3] = messageCountArray[3];
                            }

                            LOG.info("run send load message ok: " + Arrays.toString(response) +
                                    " Thread: " + this.getId() + " objectName: " + objectName);

                            out.write(response);
                            out.flush();
                            break;
                        }
                        case 10: {
                            byte[] response = new byte[4 + markV2.length];
                            byte[] responseSize = ByteBuffer.allocate(4).putInt(2 + markV2.length).array();

                            response[0] = responseSize[2];
                            response[1] = responseSize[3];
                            response[2] = 11 & 0xff;
                            response[3] = (byte) (markV2.length & 0xff);
                            System.arraycopy(markV2, 0, response, 4, markV2.length);

                            LOG.info("run send mark Message: " + Arrays.toString(response) +
                                    " Thread: " + this.getId() + " objectName: " + objectName);

                            out.write(response);
                            out.flush();
                            break;
                        }
                        default: {
                            statistic.serverErrorBlock();

                            socket.close();
                            LOG.warning("run Unknown packageType. Socket close! Data: " + Arrays.toString(data) +
                                    " Thread: " + this.getId() + " ObjectName: " + objectName);
                            return;
                        }
                    }
                } else {
                    statistic.serverErrorBlock();

                    socket.close();
                    LOG.warning("run Can`t read head of message Socket close! Thread: " + this.getId()
                            + " ObjectName: " + objectName);
                    return;
                }
            } catch (MySocketException e) {
                LOG.warning("run My error " + e.getMessage() + " Thread: " + this.getId() + " objectName: " + objectName);
                return;
            } catch (IOException e) {
                LOG.warning("run Error when read messages Error: " + e.getMessage() + " Thread: " + this.getId() +
                        " ObjectName: " + objectName);
                return;
            }
        }
    }

    /**
     * Метод проверяет идентификацию подключаемого клиента
     * @param data данные запроса
     * @param out поток для ответа
     * @throws IOException если произошла ошибка
     */
    private void identify(byte[] data, OutputStream out) throws MySocketException, IOException {
        String version = Integer.toBinaryString(data[1] & 0xff);

        switch (version) {
            case "100000":
                protocolVersion = 2;
                break;
            case "10000":
            default:
                protocolVersion = 1;
        }

        int direction = data[2] & 0xff;
        if (!(direction == 0 || direction == 1)) {
            LOG.warning("identify duration error Thread: " + this.getId() + " objectName: " + objectName);
            out.write(getIdentificationFailureMessage());
            out.flush();

            statistic.serverErrorBlock();

            socket.close();
            throw new MySocketException("Duration error");
        }

        int controllerNumber = data[3] & 0xff;

        String model = new String(Arrays.copyOfRange(data, 4, data.length));

        LOG.info("identify version: " + version + " direction: " + direction +
                " controllerNumber: " + controllerNumber + " model: " + model +
                " Thread: " + this.getId() + " objectName: " + objectName);

        try {
            opc = Utils.loadRMI();
            objectName = socket.getInetAddress().getHostAddress() +
                    '_' +
                    controllerNumber +
                    '_' +
                    model;

            if (opc.checkObject(serverName + '_' + objectName, serverName)) {
                LOG.info("identify Check object return true Thread: " + this.getId() + " objectName: " + objectName);
                out.write(getConfirmIdentifyMessage());
                out.flush();
            } else {
                LOG.info("identify Check object return false Thread: " + this.getId() + " objectName: " + objectName);
                out.write(getIdentificationFailureMessage());
                out.flush();

                statistic.linkedBlock();

                socket.close();
                throw new MySocketException("identify failure");
            }
        } catch (NamingException e) {
            LOG.warning("identify remote server error " + e.getMessage() +
                    " Thread: " + this.getId() + " objectName: " + objectName);
            out.write(getIdentificationFailureMessage());
            out.flush();

            statistic.serverErrorBlock();

            socket.close();
            throw new MySocketException("identify failure");
        }
    }

    /**
     * Метод формирует массив byte для подтверждения идентификации
     * @return массив byte
     */
    private byte[] getConfirmIdentifyMessage() {
        byte[] serverNameBytes = serverName.getBytes();
        byte[] response = new byte[6 + serverNameBytes.length];
        byte[] byteSize = ByteBuffer.allocate(4).putInt(4 + serverNameBytes.length).array();

        response[0] = byteSize[2];
        response[1] = byteSize[3];
        response[2] = 2 & 0xff;
        response[3] = 40 & 0xff; // 0010 1000 это версия 2
//        response[3] = 24 & 0xff; // 0001 1000 это версия 1
        response[4] = 1 & 0xff;
        response[5] = 1 & 0xff; // TODO Номер сервера в системе (для теста написал 1)
        System.arraycopy(serverNameBytes, 0, response, 6, serverNameBytes.length);

        LOG.info("getConfirmIdentifyMessage Message: " + Arrays.toString(response) +
                " Thread: " + this.getId() + " objectName: " + objectName);
        return response;
    }

    /**
     * Метод формирует массив byte для отказа идентификации
     * @return массив byte
     */
    private byte[] getIdentificationFailureMessage() {
        byte[] response = new byte[6];

        response[0] = 0;
        response[1] = 4 & 0xff;
        response[2] = 3 & 0xff;
        response[3] = 40 & 0xff; // 0010 1000 это версия 2
//        response[3] = 24 & 0xff; // 0001 1000 это версия 1
        response[4] = 1 & 0xff;
        response[5] = 1 & 0xff; // TODO Номер сервера в системе (для теста написал 1)

        LOG.info("getIdentificationFailureMessage Message: " + Arrays.toString(response) +
                " Thread: " + this.getId() + " objectName: " + objectName);
        return response;
    }

    private int parse(byte[] data, List<ParseDataModel> parseDataModels, int startIndex, int protocolVersion) throws Exception {
        List<String> result = new ArrayList<>();
        List<Object> parseData;

        int increment;
        int messagesCount;
        switch (protocolVersion) {
            case 2:
                messagesCount = ByteBuffer.wrap(data, 1, 2).getShort();
                increment = 1;
                break;
            case 1:
            default:
                messagesCount = ByteBuffer.wrap(data, 1, 1).get();
                increment = 0;
        }

        result.add("messages count: " + messagesCount);
        result.add("");

        int add;

        int repeatCount = 0;

        try {
            for (int i = startIndex; i < data.length; i += add) {
                if (repeatCount == messagesCount) {
                    break;
                }

                parseData = new ArrayList<>();

                int seconds = ((data[i] & 0xff) << 24) | ((data[i + 1] & 0xff) << 16) | ((data[i + 2] & 0xff) << 8) | (data[i + 3] & 0xff);
                int nanoSeconds = ((data[i + 4] & 0xff) << 24) | ((data[i + 5] & 0xff) << 16) | ((data[i + 6] & 0xff) << 8) | (data[i + 7] & 0xff);
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(seconds, nanoSeconds, ZoneOffset.UTC);
                result.add("date: " + dateTime);

                if (protocolVersion == 2) {
                    int controllerStatus = data[i + 8] & 0xff;
                    result.add("controller status: " + controllerStatus);
                }

                int bufferNumber = data[i + 8 + increment] & 0xff;
                result.add("buffer number: " + bufferNumber);

                int eventCode = ((data[i + 9 + increment] & 0xff) << 24) | ((data[i + 10 + increment] & 0xff) << 16) |
                        ((data[i + 11 + increment] & 0xff) << 8) | (data[i + 12 + increment] & 0xff);
                result.add("event code: " + eventCode);

                int additionalDataSize = (data[i + 13 + increment] & 0xff);
                result.add("additional data size: " + additionalDataSize);

                add = 4 + 4 + increment + 1 + 4 + 1; // заголовок сообщения

                int additionalDataStartIndex = i + add;

                int addIndex = 0;
                for (int j = 0; j < additionalDataSize; j++) {
                    int type = (data[additionalDataStartIndex + addIndex] & 0xff);
                    int count = (data[additionalDataStartIndex + 1 + addIndex] & 0xff);
                    result.add("type: " + type + " count: " + count);

                    add += 2; // заголовок дополнительных данных
                    addIndex += 2;

                    switch (type) {
                        case 254: {
                            Integer[] additionalData = new Integer[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = Byte.toUnsignedInt(data[additionalDataStartIndex + addIndex + k]);
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += count;
                            addIndex += count;
                            break;
                        }
                        case 253: {
                            Integer[] additionalData = new Integer[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = ((data[additionalDataStartIndex + addIndex + (4 * k)] & 0xff) << 24) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 1)] & 0xff) << 16) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 2)] & 0xff) << 8) |
                                        (data[additionalDataStartIndex + addIndex + ((4 * k) + 3)] & 0xff);
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += 4 * count;
                            addIndex += 4 * count;
                            break;
                        }
                        case 252: {
                            Long[] additionalData = new Long[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = Integer.toUnsignedLong(((data[additionalDataStartIndex + addIndex + (4 * k)] & 0xff) << 24) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 1)] & 0xff) << 16) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 2)] & 0xff) << 8) |
                                        (data[additionalDataStartIndex + addIndex + ((4 * k) + 3)] & 0xff));
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += 4 * count;
                            addIndex += 4 * count;
                            break;
                        }
                        case 251: {
                            Long[] additionalData = new Long[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = ByteBuffer.wrap(data, additionalDataStartIndex + addIndex + (8 * k), 8).getLong();
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += 8 * count;
                            addIndex += 8 * count;
                            break;
                        }
                        case 250: {
                            Float[] additionalData = new Float[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = Float.intBitsToFloat(((data[additionalDataStartIndex + addIndex + (4 * k)] & 0xff) << 24) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 1)] & 0xff) << 16) |
                                        ((data[additionalDataStartIndex + addIndex + ((4 * k) + 2)] & 0xff) << 8) |
                                        (data[additionalDataStartIndex + addIndex + ((4 * k) + 3)] & 0xff));
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += 4 * count;
                            addIndex += 4 * count;
                            break;
                        }
                        case 249: {
                            String[] additionalData = new String[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = new String(Arrays.copyOfRange(data, additionalDataStartIndex + addIndex, additionalDataStartIndex + addIndex + 1));
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += count;
                            addIndex += count;
                            break;
                        }
                        case 255: {
                            int boolCount = count;
                            count = (int) Math.ceil(count / 8d);
                            Integer[] additionalData = new Integer[count * 8];
                            for (int k = 0; k < count; k++) {
                                for (int m = 0; m < 8; m++) {
                                    additionalData[8 * k + m] = (data[additionalDataStartIndex + addIndex + k] & ((int) Math.pow(2, m))) >> m;
                                }
                            }
                            result.add("additional data: " + Arrays.toString(Arrays.copyOfRange(additionalData, 0, boolCount)));
                            parseData.addAll(Arrays.asList(Arrays.copyOfRange(additionalData, 0, boolCount)));
                            add += count;
                            addIndex += count;
                            break;
                        }
                        case 64:
                        default: {
                            Byte[] additionalData = new Byte[count];
                            for (int k = 0; k < count; k++) {
                                additionalData[k] = data[additionalDataStartIndex + addIndex + k];
                            }
                            result.add("additional data: " + Arrays.toString(additionalData));
                            parseData.addAll(Arrays.asList(additionalData));
                            add += count;
                            addIndex += count;
                        }
                    }
                }
                result.add("");

                List<String> parameters = ControllerConfig.getConfigNames(bufferNumber, eventCode, parseData.size());

                if ((parameters != null) && (parameters.size() == parseData.size())) {
                    Map<String, Object> mapData = new HashMap<>();

                    for (int j = 0; j < parameters.size(); j++) {
                        mapData.put(parameters.get(j), parseData.get(j));
                    }

                    parseDataModels.add(new ParseDataModel(dateTime, mapData));
                }

                repeatCount++;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.log(Level.WARNING, "IndexOutOfBoundsException:", e);
        }

        String path = ProjectProperty.getLogFolder().toAbsolutePath().toString() + "/" + socket.getInetAddress().getHostAddress();
        if (!Files.exists(Paths.get(path))) {
            Files.createDirectory(Paths.get(path));
        }

        Files.write(Paths.get(path + "/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss_SSS")) + ".txt"), result);

        return repeatCount;
    }
}
