package ru.tecon.mfk1500Server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.Utils;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.exception.MySocketException;
import ru.tecon.exception.MyStatisticException;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.model.DataModel;
import ru.tecon.model.ValueModel;
import ru.tecon.server.model.ParseDataModel;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * @author Maksim Shchelkonogov
 */
public class PushEventHandler extends ChannelInboundHandlerAdapter {

    private Logger logger = LoggerFactory.getLogger(PushEventHandler.class);

    private String objectName = "";
    private String serverName;
    private byte protocolVersion;
    private LoadOPCRemote opc;

    private Statistic statistic;

    public PushEventHandler(Statistic statistic) {
        this.statistic = statistic;
        this.serverName = DriverProperty.getInstance().getServerName();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        statistic.setChannel(ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            ByteBuf in = (ByteBuf) msg;
            int length = in.readUnsignedShort();
            logger.info("Message size {} {}", length, getObjectData());

            if (length == 0) {
                logger.warn("Error read message {}", getObjectData());
                statistic.block(BlockType.SERVER_ERROR);
                ctx.close();
            }

            byte[] data = new byte[length];
            in.readBytes(data);
            logger.info("Message body {} {}", Arrays.toString(data), getObjectData());

            int packageType = data[0] & 0xff;

            logger.info("Data size: {} packageType: {} {}", length, packageType, getObjectData());
            outer: switch (packageType) {
                case 1: {
                    identify(data, ctx);
                    break;
                }
                case 3: {
                    if (!opc.checkObject(serverName + '_' + objectName, serverName)) {
                        logger.info("Object is not linked, block client {}", getObjectData());
                        statistic.block(BlockType.LINKED);
                        ctx.close();
                        break;
                    }

                    List<DataModel> dataModels = statistic.getObjectModel();

                    logger.info("Loaded parameters from db {}", getObjectData());

                    List<ParseDataModel> result = new ArrayList<>();
                    int messagesCount;
                    int messageConfirmSize;

                    switch (protocolVersion) {
                        case 1:
                            try {
                                messageConfirmSize = 2;
                                messagesCount = parse(data, result, 2, protocolVersion);
                            } catch (Exception e) {
                                logger.warn("Parse version 1 exception. Data: {} {}",
                                        Arrays.toString(data), getObjectData(), e);
                                statistic.block(BlockType.SERVER_ERROR);
                                ctx.close();
                                break outer;
                            }
                            break;
                        case 2:
                            try {
                                messageConfirmSize = 3;

                                int markSize = ByteBuffer.wrap(data, 3, 1).get();
                                byte[] markV2 = new byte[markSize];
                                System.arraycopy(data, 4, markV2, 0, markSize);
                                statistic.setMarkV2(markV2);
                                logger.info("Update mark {} {}", Arrays.toString(markV2), getObjectData());

                                messagesCount = parse(data, result, 4 + markSize, protocolVersion);
                            } catch (Exception e) {
                                logger.warn("Parse version 2 exception. Data: {} {}",
                                        Arrays.toString(data), getObjectData(), e);
                                statistic.block(BlockType.SERVER_ERROR);
                                ctx.close();
                                break outer;
                            }
                            break;
                        default:
                            logger.warn("Unknown protocolVersion. Data: {} {}",
                                    Arrays.toString(data), getObjectData());
                            statistic.block(BlockType.SERVER_ERROR);
                            ctx.close();
                            break outer;
                    }

                    logger.info("Message parsed. {}", getObjectData());

                    Collections.sort(result);

                    dataModels.forEach(dataModel -> {
                        for (ParseDataModel model : result) {
                            if (Objects.nonNull(model.getValue(dataModel.getParamName())) &&
                                    ((dataModel.getStartTime() == null) ||
                                            model.getDate().isAfter(dataModel.getStartTime()))) {
                                dataModel.addData(new ValueModel(String.valueOf(model.getValue(dataModel.getParamName())),
                                        model.getDate()));
                            }
                        }
                    });

                    dataModels.removeIf(dataModel -> dataModel.getData().isEmpty());

                    if (dataModels.isEmpty()) {
                        logger.info("No data to put into database {}", getObjectData());
                    } else {
                        logger.info("Put data to DB size: {} {}", dataModels.size(), getObjectData());

                        opc.putDataWithCalculateIntegrator(dataModels);
                    }

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

                    logger.info("Send load message ok: {} {}", Arrays.toString(response), getObjectData());

                    ctx.writeAndFlush(response);

                    if (protocolVersion == 2) {
                        statistic.updateMarkV2();
                    }
                    break;
                }
                case 10: {
                    byte[] markV2 = statistic.getMarkV2();
                    logger.info("get mark {} {}", Arrays.toString(markV2), getObjectData());
                    byte[] response = new byte[4 + markV2.length];
                    byte[] responseSize = ByteBuffer.allocate(4).putInt(2 + markV2.length).array();

                    response[0] = responseSize[2];
                    response[1] = responseSize[3];
                    response[2] = 11 & 0xff;
                    response[3] = (byte) (markV2.length & 0xff);
                    System.arraycopy(markV2, 0, response, 4, markV2.length);

                    logger.info("Send mark message: {} {}", Arrays.toString(response), getObjectData());

                    ctx.writeAndFlush(response);
                    break;
                }
                default: {
                    logger.warn("Unknown packageType. data: {} {}", Arrays.toString(data), getObjectData());
                    statistic.block(BlockType.SERVER_ERROR);
                    ctx.close();
                }
            }
        } catch (MySocketException e) {
            logger.warn("My pushEvent handler error {}", getObjectData(), e);
        } catch (MyStatisticException e) {
            statistic.block(BlockType.SERVER_ERROR);
            logger.warn("My statistic handler error {}", getObjectData(), e);
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Error with object {}", getObjectData(), cause);
        statistic.block(BlockType.SERVER_ERROR);
        ctx.close();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        logger.info("Client unregistered {}", getObjectData());
    }

    private String getObjectData() {
        return "host: " + statistic.getIp() + (objectName.equals("") ? "" : " name: " + objectName);
    }

    /**
     * Метод проверяет идентификацию подключаемого клиента
     * @param data данные запроса
     * @param ctx поток для ответа
     */
    private void identify(byte[] data, ChannelHandlerContext ctx) throws MySocketException {
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
            logger.warn("Identify duration error {}", getObjectData());
            ctx.writeAndFlush(getIdentificationFailureMessage());

            statistic.block(BlockType.SERVER_ERROR);
            ctx.close();
            throw new MySocketException("Duration error");
        }

        int controllerNumber = data[3] & 0xff;

        String model = new String(Arrays.copyOfRange(data, 4, data.length));

        logger.info("Identify version: {} direction: {} controllerNumber: {} model: {} {}",
                version, direction, controllerNumber, model, getObjectData());

        try {
            opc = Utils.loadRMI();
            objectName = statistic.getIp() +
                    '_' +
                    controllerNumber +
                    '_' +
                    model;
            statistic.setControllerIdent(objectName);

            if (opc.checkObject(serverName + '_' + objectName, serverName)) {
                logger.info("Object is linked {}", getObjectData());
                ctx.writeAndFlush(getConfirmIdentifyMessage());
            } else {
                logger.info("Object is not linked {}", getObjectData());
                ctx.writeAndFlush(getIdentificationFailureMessage());
                statistic.block(BlockType.LINKED);

                ctx.close();
                throw new MySocketException("identify failure");
            }
        } catch (NamingException e) {
            logger.warn("Identify remote server error {}", getObjectData(), e);
            ctx.writeAndFlush(getIdentificationFailureMessage());

            statistic.block(BlockType.SERVER_ERROR);

            ctx.close();
            throw new MySocketException("identify failure");
        }
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

        logger.info("Send identify failure message {} {}", Arrays.toString(response), getObjectData());
        return response;
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

        logger.info("Send confirm identify message {} {}", Arrays.toString(response), getObjectData());
        return response;
    }

    private int parse(byte[] data, List<ParseDataModel> parseDataModels, int startIndex, int protocolVersion) throws Exception {
        Set<String> configNames = new HashSet<>();
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

                if ((!parameters.isEmpty()) && (parameters.size() == parseData.size())) {
                    statistic.addData(bufferNumber, eventCode, parseData, dateTime);

                    configNames.addAll(parameters.stream()
                            .map(s -> s.split(":")[0])
                            .collect(Collectors.toSet()));

                    Map<String, Object> mapData = new HashMap<>();

                    for (int j = 0; j < parameters.size(); j++) {
                        mapData.put(parameters.get(j), parseData.get(j));
                    }

                    parseDataModels.add(new ParseDataModel(dateTime, mapData));
                }

                repeatCount++;
            }
        } catch (IndexOutOfBoundsException e) {
            logger.warn("IndexOutOfBoundsException", e);
        }

        // Проверяем существует ли директория для логов, создаем если такой нет
        Path path = DriverProperty.getInstance().getPushEventLogPath().resolve(statistic.getIp());
        if (!Files.exists(path)) {
            Files.createDirectory(path);
        }

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss_SSS"));
        // Сохраняем файл логов с данными
        Files.write(path.resolve(currentTime + ".txt"), result);
        // Создается или дописывается в существующий файл группы переданных данных
        Files.write(path.resolve(DriverProperty.getInstance().getPushEventLastConfig()), (currentTime + System.lineSeparator()).getBytes(), APPEND, CREATE);
        Files.write(path.resolve(DriverProperty.getInstance().getPushEventLastConfig()), configNames, APPEND, CREATE);

        return repeatCount;
    }
}
