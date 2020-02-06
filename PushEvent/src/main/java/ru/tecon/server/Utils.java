package ru.tecon.server;

import ru.tecon.ControllerConfig;
import ru.tecon.ProjectProperty;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.model.ParseDataModel;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Utils {

    public static LoadOPCRemote loadRMI() throws NamingException {
        Hashtable<String, String> ht = new Hashtable<>();
        ht.put(Context.INITIAL_CONTEXT_FACTORY, "weblogic.jndi.WLInitialContextFactory");
        ht.put(Context.PROVIDER_URL, "t3://" + ProjectProperty.getServerURI() + ":" + ProjectProperty.getServerPort());

        Context ctx = new InitialContext(ht);

        return  (LoadOPCRemote) ctx.lookup("ejb.LoadOPC#ru.tecon.beanInterface.LoadOPCRemote");
    }

    static int parse(byte[] data, List<ParseDataModel> parseDataModels, int startIndex, int protocolVersion) throws Exception {
        List<String> result = new ArrayList<>();
        List<Object> parseData;

        System.out.println("data length: " + data.length);

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

                int eventCode = ((data[i + 9 + increment] & 0xff) << 8) | ((data[i + 10 + increment] & 0xff) << 8) |
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
                        case 255:
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

                List<String> parameters = ControllerConfig.getConfigNames(String.valueOf(bufferNumber), String.valueOf(eventCode));

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
            System.out.println("IndexOutOfBoundsException " + e.getMessage());
        }

        System.out.println("repeatCount " + repeatCount);

        Files.write(Paths.get(ProjectProperty.getLogFolder() + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss_SSS")) + ".txt"), result);

        return repeatCount;
    }
}
