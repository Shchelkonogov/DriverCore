package ru.tecon.server;

import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.exception.MySocketException;
import ru.tecon.model.DataModel;
import ru.tecon.model.ParseDataModel;
import ru.tecon.model.ValueModel;

import javax.naming.NamingException;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Logger;

public class EchoThread extends Thread {

    private static final Logger LOG = Logger.getLogger(EchoThread.class.getName());

    private Socket socket;
    private String serverName;
    private String objectName = null;
    private LoadOPCRemote opc;

    private byte[] markV2 = new byte[0];
    private int protocolVersion = 2;

    EchoThread(Socket clientSocket, String serverName) {
        this.socket = clientSocket;
        this.serverName = serverName;
    }

    public void run() {
        BufferedInputStream in;
        DataOutputStream out;

        try {
            in = new BufferedInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
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

                    int readBytes = 0;
                    if (size == 0) {
                        socket.close();
                        LOG.warning("run Error read message Socket close! Thread: " + this.getId() +
                                " ObjectName: " + objectName);
                        return;
                    }
                    //Из-за передачи по TCP может не в первом покете прийти данные,
                    // так что приходится читать в цикле, пока все не прочитется
                    while (readBytes != size) {
                        readBytes += in.read(data, readBytes, size - readBytes);
                    }
                    LOG.info("Thread: " + this.getId() + " Read bytes: " + readBytes + " Message body: " + Arrays.toString(data));

                    int packageType = data[0] & 0xff;

                    LOG.info("run Data size: " + size + " packageType " + packageType +
                            " Thread: " + this.getId() + " ObjectName: " + objectName);

                    switch (packageType) {
                        case 1: {
                            identify(data, out);
                            break;
                        }
                        case 3: {
                            ArrayList<DataModel> dataModels = opc.loadObjectParameters(serverName + '_' + objectName, serverName);
                            Collections.sort(dataModels);

                            List<ParseDataModel> result = new ArrayList<>();
                            int messagesCount;
                            int messageConfirmSize;

                            switch (protocolVersion) {
                                case 1:
                                    try {
                                        messageConfirmSize = 2;
                                        messagesCount = Utils.parse(data, result, 2, protocolVersion);
                                    } catch (Exception e) {
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

                                        messagesCount = Utils.parse(data, result, 4 + markSize, protocolVersion);
                                    } catch (Exception e) {
                                        socket.close();
                                        LOG.warning("run Parse version 2 exception. Socket close! " + e.getMessage() +
                                                " Data: " + Arrays.toString(data) +
                                                " Thread: " + this.getId() + " ObjectName: " + objectName);
                                        return;
                                    }
                                    break;
                                default:
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
                            socket.close();
                            LOG.warning("run Unknown packageType. Socket close! Data: " + Arrays.toString(data) +
                                    " Thread: " + this.getId() + " ObjectName: " + objectName);
                            return;
                        }
                    }
                } else {
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
    private void identify(byte[] data, DataOutputStream out) throws MySocketException, IOException {
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
                socket.close();
                throw new MySocketException("identify failure");
            }
        } catch (NamingException e) {
            LOG.warning("identify remote server error " + e.getMessage() +
                    " Thread: " + this.getId() + " objectName: " + objectName);
            out.write(getIdentificationFailureMessage());
            out.flush();
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
}
