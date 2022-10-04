package ru.tecon.isacom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.mfk1500Server.DriverProperty;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Класс реализации функций протокола isacom, разработанный ТЕКОНом
 */
public class IsacomProtocol {

    private static Logger logger = LoggerFactory.getLogger(IsacomProtocol.class);

    private static final Map<String, String> methodsMap;

    // Блок для инициализации map методов разбора реализованных простых типов
    static {
        methodsMap = new HashMap<>();
        Method[] method = IsacomProtocol.class.getDeclaredMethods();

        for (Method md: method) {
            if (md.isAnnotationPresent(Isacom.class)) {
                methodsMap.put(md.getAnnotation(Isacom.class).name().toString(), md.getName());
            }
        }
    }

    /**
     * Реализация функции 5 протокола "Запрос клиентом версии протокола"
     * @param in входной поток
     * @param out выходной поток
     * @return версия протокола
     * @throws IOException в случае проблем работы с потоками
     * @throws IsacomException в случае ошибки чтения версии протокола
     */
    public static int getProtocolVersion(InputStream in, OutputStream out) throws IOException, IsacomException {
        ByteBuffer head = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(5)
                .putInt(Integer.valueOf("1000001", 2) | DriverProperty.getInstance().getResourceNumber() << 8)
                .putInt(0)
                .putInt(0);

        out.write(head.array());
        out.flush();

        byte[] response = new byte[16];

        if ((in.read(response) != 16) ||
                (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
            errorLog(ByteBuffer.wrap(Arrays.copyOfRange(response, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt());
            logger.warn("Problem reading protocol version {}", Arrays.toString(response));
            throw new IsacomException("Problem reading protocol version");
        }

        return ByteBuffer.wrap(Arrays.copyOfRange(response, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * Реализация функции 6 протокола "Создание списка переменных"
     * @param in входной поток
     * @param out выходной поток
     * @param data список переменных для создания
     * @throws IOException в случае проблем работы с потоками
     * @throws IsacomException в случае ошибки создания списка переменных
     */
    public static void createVariableList(InputStream in, OutputStream out, List<IsacomModel> data)
            throws IOException, IsacomException {
        int size = 0;
        for (IsacomModel isacomModel: data) {
            size += isacomModel.getName().getBytes().length + 1 + isacomModel.getTypeName().getBytes().length + 1 + 4;
        }

        ByteBuffer head = ByteBuffer.allocate(16 + size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(6)
                .putInt(Integer.valueOf("1000001", 2) | DriverProperty.getInstance().getResourceNumber() << 8)
                .putInt(size)
                .putInt(0);

        for (IsacomModel instantDataModel: data) {
            head.put(instantDataModel.getName().getBytes())
                    .put((byte) 0)
                    .put(instantDataModel.getTypeName().getBytes())
                    .put((byte) 0)
                    .putInt(instantDataModel.getTypeSize());
        }

        out.write(head.array());
        out.flush();

        byte[] response = new byte[16];
        if ((in.read(response) != 16) ||
                (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
            errorLog(ByteBuffer.wrap(Arrays.copyOfRange(response, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt());
            logger.warn("Problem reading response to creating variables {}", Arrays.toString(response));
            throw new IsacomException("Problem reading response to creating variables");
        }
    }

    /**
     * Частичная реализация функции 7 "Чтение переменных по списку"
     * @param in входной поток
     * @param out выходной поток
     * @param data список переменных для чтения
     * @throws IOException в случае проблем работы с потоками
     * @throws IsacomException в случае ошибки чтения списка переменных
     */
    public static void readVariableList(InputStream in, OutputStream out, List<IsacomModel> data)
            throws IOException, IsacomException {
        byte[] response = new byte[16];
        byte[] request = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(7)
                .putInt(1)
                .putInt(0)
                .putInt(0).array();

        out.write(request);
        out.flush();

        if ((in.read(response) != 16) ||
                (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
            errorLog(ByteBuffer.wrap(Arrays.copyOfRange(response, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt());
            logger.warn("Problem reading response to reading variables by list {}", Arrays.toString(response));
            throw new IsacomException("Problem reading response to reading variables by list");
        }

        byte[] resultData = new byte[ByteBuffer.wrap(Arrays.copyOfRange(response, 8, 12)).order(ByteOrder.LITTLE_ENDIAN).getInt()];

        DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(in));

        dataInputStream.readFully(resultData);

        int index = 0;
        for (IsacomModel modelItem: data) {
            if (resultData[index] == 0) {
                ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOfRange(resultData, index + 1, index + 1 + modelItem.getTypeSize()))
                        .order(ByteOrder.BIG_ENDIAN);

                try {
                    if (modelItem.getSubModel() != null) {
                        buffer = ByteBuffer.wrap(Arrays.copyOfRange(buffer.array(), modelItem.getOffset(),
                                modelItem.getOffset() + IsacomSimpleTypes.valueOf(modelItem.getSubModel().getTypeName()).getSize()))
                                .order(ByteOrder.BIG_ENDIAN);
                        modelItem.setValue(loadSimpleType(modelItem.getSubModel().getTypeName(), buffer));
                    } else {
                        modelItem.setValue(loadSimpleType(modelItem.getTypeName(), buffer));
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    logger.warn("Type parse error {}", modelItem.getTypeName(), e);
                }

                index += modelItem.getTypeSize();
            }
            index += 1;
        }
    }

    /**
     * Реализации функции 9 "Расширенная запись переменных"
     * @param in входной поток
     * @param out выходной поток
     * @param data список данных для записи
     * @throws IOException в случае проблем работы с потоками
     * @throws IsacomException в случае ошибки записи переменных
     */
    public static void extendedVariableWriting(InputStream in, OutputStream out, List<IsacomModel> data)
            throws IOException, IsacomException {
        int size = 0;
        for (IsacomModel isacomModel: data) {
            size += isacomModel.getName().getBytes().length + 1 + isacomModel.getTypeName().getBytes().length + 1 + 4 + 4 + 4
                    + isacomModel.getTypeSize();
        }

        ByteBuffer head = ByteBuffer.allocate(16 + size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(9)
                .putInt(2)
                .putInt(size)
                .putInt(0);

        for (IsacomModel isacomModel: data) {
            head.put(isacomModel.getName().getBytes())
                    .put((byte) 0)
                    .put(isacomModel.getTypeName().getBytes())
                    .put((byte) 0)
                    .putInt(isacomModel.getTypeSize())
                    .putInt(0)
                    .putInt(isacomModel.getTypeSize());

            ByteBuffer valueBuffer = ByteBuffer.allocate(isacomModel.getTypeSize()).order(ByteOrder.BIG_ENDIAN);
            switch (isacomModel.getTypeName()) {
                case "DINT":
                case "TIME":
                    valueBuffer.putInt(Integer.valueOf(isacomModel.getValue()));
                    break;
                default:
                    logger.warn("Unsupported type for value entry {}", isacomModel.getTypeName());
                    throw new IsacomException("Unsupported type for value entry " + isacomModel.getTypeName());
            }
            head.put(valueBuffer.array());
        }

        out.write(head.array());
        out.flush();

        byte[] response = new byte[16];
        if ((in.read(response) != 16) ||
                (ByteBuffer.wrap(Arrays.copyOfRange(response, 0, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt() != 1)) {
            errorLog(ByteBuffer.wrap(Arrays.copyOfRange(response, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt());
            logger.warn("Problem writing new values of system variables {}", Arrays.toString(response));
            throw new IsacomException("Problem writing new values of system variables");
        }
    }

    /**
     * Метод обертка разбирает простые типы
     * @param type имя простого типа
     * @param buffer буффер с данными
     * @throws NoSuchMethodException ошибка
     * @throws InvocationTargetException ошибка
     * @throws IllegalAccessException ошибка
     */
    private static String loadSimpleType(String type, ByteBuffer buffer)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (methodsMap.get(type) != null) {
            return String.valueOf(IsacomProtocol.class
                    .getDeclaredMethod(methodsMap.get(type), ByteBuffer.class)
                    .invoke(null, buffer));
        } else {
            logger.warn("Missing method to parse instant value {}", type);
            return null;
        }
    }

    private static void errorLog(int code) {
        switch (code) {
            case 1:
                logger.warn("Wrong message");
                break;
            case 2:
                logger.warn("Internal server error");
                break;
            case 3:
                logger.warn("No data");
                break;
            case 4:
                logger.warn("Symbol table missing");
                break;
            default:
                logger.warn("unknown error {}", code);
        }
    }

    @Isacom(name = IsacomSimpleTypes.REAL)
    private static float readReal(ByteBuffer buffer) {
        return buffer.getFloat();
    }

    @Isacom(name = IsacomSimpleTypes.DINT)
    private static int readDint(ByteBuffer buffer) {
        return buffer.getInt();
    }

    @Isacom(name = IsacomSimpleTypes.INT)
    private static short readInt(ByteBuffer buffer) {
        return buffer.getShort();
    }

    @Isacom(name = IsacomSimpleTypes.BOOL)
    private static byte readBool(ByteBuffer buffer) {
        return buffer.get();
    }

    @Isacom(name = IsacomSimpleTypes.TIME)
    private static int readTIME(ByteBuffer buffer) {
        return buffer.getInt();
    }
}
