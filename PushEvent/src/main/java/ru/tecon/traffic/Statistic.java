package ru.tecon.traffic;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.Utils;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.driverCoreClient.model.LastData;
import ru.tecon.exception.MyStatisticException;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.ObjectModelMutex;
import ru.tecon.model.DataModel;
import ru.tecon.model.WebStatistic;

import javax.naming.NamingException;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;

/**
 * Класс статистики работы сервера MFK1500
 * @author Maksim Shchelkonogov
 */
public class Statistic implements Serializable {

    private static final long serialVersionUID = 5218038278503278808L;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static final String serverName = DriverProperty.getInstance().getServerName();

    private final ObjectModelMutex objectModelMutex = new ObjectModelMutex();

    private static Logger logger = LoggerFactory.getLogger(Statistic.class);

    private String ip;
    private String objectName;

    private AtomicInteger chanelCount = new AtomicInteger(0);
    private AtomicLong inputTraffic = new AtomicLong(0);
    private AtomicLong outputTraffic = new AtomicLong(0);
    private AtomicLong monthTraffic = new AtomicLong(0);
    private AtomicLong inputTrafficReal = new AtomicLong(0);
    private AtomicLong outputTrafficReal = new AtomicLong(0);
    private AtomicLong monthTrafficReal = new AtomicLong(0);

    private LocalDateTime lastRequestTime = LocalDateTime.now();

    private boolean informWebConsole;

    private boolean block = false;
    private Set<BlockType> blockTypes = new HashSet<>();

    private boolean ignoreTraffic = false;

    private Map<String, LastDayData> lastDayData = new HashMap<>();

    private transient String controllerIdent;
    private List<DataModel> objectModel;

    private byte[] markV2;
    private transient byte[] markV2Temp;

    private transient Channel channel;

    private transient Event event;

    public Statistic(String ip, Event event) {
        this(ip, event, true);
    }

    public Statistic(String ip, Event event, boolean informWebConsole) {
        this.ip = ip;
        this.informWebConsole = informWebConsole;
        this.event = event;
        if (event != null) {
            event.addItem(this);
        }
    }

    public void updateObjectName() {
        updateObjectName(true);
    }

    /**
     * Метод выгружает имя объекта из базы
     */
    private void updateObjectName(boolean update) {
        try {
            objectName = Utils.loadRMI().loadObjectName(DriverProperty.getInstance().getServerName(), ip);
            logger.info("Update object name for ip {}", ip);
            if (update) {
                update();
            }
        } catch (NamingException e) {
            logger.warn("RMI load error", e);
        }
    }

    /**
     * Проверка открыт ли socket
     * @return true - открыт, false - закрыт
     */
    public boolean isChanelOpen() {
        return Objects.nonNull(channel) && channel.isOpen();
    }

    /**
     * Устанавливаем socket
     * @param channel socket
     */
    public void setChannel(Channel channel) {
        close();
        chanelCount.addAndGet(1);
        this.channel = channel;
        update();
    }

    /**
     * @return количество сокетов, которые открывал контроллер
     */
    public int getChanelCount() {
        return chanelCount.get();
    }

    /**
     * Сбрасывает количество сокетов, которые открывал контроллер.
     * Если socket в данный момент открыт, то новое количество будет 1 иначе 0
     */
    public void clearSocketCount() {
        if (!isChanelOpen()) {
            chanelCount.set(0);
        } else {
            chanelCount.set(1);
        }
        update();
    }

    /**
     * Закрытие открытого socket, если такой открыт в данный момент
     */
    public void close() {
        if (isChanelOpen()) {
            try {
                boolean awaitStatus = channel.close().await(5, TimeUnit.SECONDS);
                if (!awaitStatus) {
                    logger.warn("can't close channel for {}", getIp());
                    channel = null;
                }
            } catch (InterruptedException e) {
                logger.warn("Close chanel error", e);
            }
        }
    }

    /**
     * Метод проверяет не завис ли socket соединение с контроллером, по причине работы контроллера
     * @return true если завис
     */
    public boolean isSocketHung() {
        return isChanelOpen() && (Math.abs(HOURS.between(lastRequestTime, LocalDateTime.now())) >= 2);
    }

    /**
     * Метод увеличивает количество входящего трафика
     * @param count количество байт на сколько надо увеличить трафик
     */
    public void updateInputTraffic(long count) {
        inputTrafficReal.addAndGet(count);
        monthTrafficReal.addAndGet(count);

        inputTraffic.addAndGet(roundTraffic(count));
        monthTraffic.addAndGet(roundTraffic(count));

        lastRequestTime = LocalDateTime.now();

        checkTraffic();
        update();
    }

    /**
     * Метод увеличивает количество исходящего трафика
     * @param count количество байт на сколько надо увеличить трафик
     */
    public void updateOutputTraffic(long count) {
        outputTrafficReal.addAndGet(count);
        monthTrafficReal.addAndGet(count);

        outputTraffic.addAndGet(roundTraffic(count));
        monthTraffic.addAndGet(roundTraffic(count));

        lastRequestTime = LocalDateTime.now();

        checkTraffic();
        update();
    }

    /**
     * @return текстовое описание входящего трафика
     */
    public String getInputTraffic() {
        return Utils.humanReadableByteCountBin(inputTraffic.get()) + " (" +
                Utils.humanReadableByteCountBin(inputTrafficReal.get()) + ")";
    }

    /**
     * @return текстовое описание исходящего трафика
     */
    public String getOutputTraffic() {
        return Utils.humanReadableByteCountBin(outputTraffic.get()) + " (" +
                Utils.humanReadableByteCountBin(outputTrafficReal.get()) + ")";
    }

    /**
     * @return текстовое описание общего трафика
     */
    public String getTraffic() {
        return Utils.humanReadableByteCountBin(inputTraffic.get() + outputTraffic.get()) + " (" +
                Utils.humanReadableByteCountBin(inputTrafficReal.get() + outputTrafficReal.get()) + ") из " +
                (ignoreTraffic ? "no limit" : Utils.humanReadableByteCountBin(DriverProperty.getInstance().getTrafficLimit()));
    }

    /**
     * @return текстовое описание общего месячного трафика
     */
    public String getMonthTraffic() {
        return Utils.humanReadableByteCountBin(monthTraffic.get()) + " (" +
                Utils.humanReadableByteCountBin(monthTrafficReal.get()) + ") из " +
                (ignoreTraffic ? "no limit" : Utils.humanReadableByteCountBin(DriverProperty.getInstance().getTrafficLimit() * LocalDate.now().lengthOfMonth()));
    }

    /**
     * Проверка на привышение допустимого трафика,
     * в случае превышение включается блокировка по трафику
     */
    private void checkTraffic() {
        if (ignoreTraffic) {
            return;
        }
        if ((inputTraffic.get() + outputTraffic.get()) > DriverProperty.getInstance().getTrafficLimit()) {
            block(BlockType.TRAFFIC);
        }
    }

    /**
     * Очистка суточного трафика
     */
    public void clearDayTraffic() {
        inputTrafficReal.set(0);
        outputTrafficReal.set(0);

        inputTraffic.set(0);
        outputTraffic.set(0);

        update();
    }

    /**
     * Очистка месячного трафика
     */
    public void clearMonthTraffic() {
        monthTrafficReal.set(0);
        monthTraffic.set(0);

        update();
    }

    /**
     * Метод для округления трафика округляется вверх до ближайщего килобайта
     * @param count значение трафика
     * @return округленное значение
     */
    private long roundTraffic(long count) {
        return ((long) Math.ceil(count / 1024d)) * 1024;
    }

    public String getLastRequestTime() {
        return lastRequestTime == null ? "" : lastRequestTime.format(FORMATTER);
    }

    public String getIp() {
        return ip;
    }

    public String getObjectName() {
        return objectName;
    }

    public void block(BlockType blockType) {
        block = true;
        if ((blockType == BlockType.LINKED) &&
                !(Objects.isNull(objectName) || (objectName.equals("")))) {
            blockType = BlockType.DEVICE_CHANGE;
        }
        blockTypes.add(blockType);
        close();
        update();
    }

    /**
     * @param blockType Снятие переданной блокировки
     * @return статус блокировки объекта
     */
    public boolean unblock(BlockType... blockType) {
        for (BlockType type: blockType) {
            blockTypes.remove(type);
        }

        if (blockTypes.isEmpty() && block) {
            block = false;
            update();
        }

        return block;
    }

    /**
     * Снятие всех блокировок с объекта
     */
    public void unblockAll() {
        if (block) {
            block = false;
            blockTypes.clear();
            update();
        }
    }

    /**
     * Проверка заблокирован ли объект.
     * @param blockType тип блокировки, если передается null то проверяется общая блокировка
     * @return статус блокировки объекта
     */
    public boolean isBlock(BlockType blockType) {
        if (blockType == null) {
            return block;
        }
        return blockTypes.contains(blockType);
    }

    /**
     * @return текстовое описание статуса блокировки
     */
    public String getBlockToString() {
        StringJoiner result = new StringJoiner(", ");
        result.setEmptyValue("свободно");
        blockTypes.forEach(blockType -> result.add(blockType.getName()));
        return result.toString();
    }

    public void update() {
        if (event != null) {
            event.update();
        }
        if (Objects.isNull(objectName) || objectName.equals("")) {
            updateObjectName(false);
        }
        if (informWebConsole) {
            try {
                Utils.loadRMI().uploadStatistic(getWebStatistic());
            } catch (NamingException e) {
                logger.warn("RMI load error", e);
            }
        }
    }

    public WebStatistic getWebStatistic() {
        WebStatistic statistic = new WebStatistic(DriverProperty.getInstance().getServerName(), getIp(), getObjectName(),
                String.valueOf(getChanelCount()), getBlockToString(), getLastRequestTime(),
                getInputTraffic(), getOutputTraffic(), getTraffic(), getMonthTraffic());
        statistic.setClosed(!isChanelOpen());
        return statistic;
    }

    /**
     * Изменяем статус проверки по трафику
     * @param ignoreTraffic true - игнорировать трафик, false - проверять трафик
     */
    public void setIgnoreTraffic(boolean ignoreTraffic) {
        this.ignoreTraffic = ignoreTraffic;
        if (ignoreTraffic) {
            if (unblock(BlockType.TRAFFIC)) {
                update();
            }
        } else {
            checkTraffic();
        }
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    /**
     * Метод добавляет статистику по последним пришедшим данным от контроллера.
     * Добавляется информации о конфигурации группы переданных данных
     */
    public void addData(int bufferNumber, int eventCode, List<Object> data, LocalDateTime dateTime) {
        String configGroupIdentifier = bufferNumber + ":" + eventCode + ":" + data.size();

        if (lastDayData == null) {
            lastDayData = new HashMap<>();
        }

        if (lastDayData.containsKey(configGroupIdentifier)) {
            LastDayData dayData = lastDayData.get(configGroupIdentifier);
            dayData.getCount().addAndGet(1);
            dayData.setDateTime(dateTime);
            dayData.setData(data);
        } else {
            lastDayData.put(configGroupIdentifier, new LastDayData(new AtomicInteger(1), dateTime, data));
        }
    }

    /**
     * @return список последних переданных групп данных
     */
    public List<LastData> getLastDayDataGroups() {
        List<LastData> lastDayDataGroups = new ArrayList<>();

        logger.info("Last day group data is {}", lastDayData);

        if (Objects.nonNull(lastDayData)) {
            lastDayData.forEach((key, value) -> {
                String[] split = key.split(":");
                int bufferNumber = Integer.parseInt(split[0]);
                int eventCode = Integer.parseInt(split[1]);
                int size = Integer.parseInt(split[2]);

                lastDayDataGroups.add(
                        new LastData(key,
                                String.join("/", ControllerConfig.getConfigNames(bufferNumber, eventCode, size)
                                        .stream()
                                        .map(param -> param.split(":")[0])
                                        .collect(Collectors.toSet())),
                                value.getCount().get(),
                                value.getDateTime(),
                                value.getData()));
            });
        }

        return lastDayDataGroups;
    }

    /**
     * Метод очищает информацию о последних группах
     */
    public void clearLastDayDataGroups() {
        if (Objects.nonNull(lastDayData)) {
            lastDayData.clear();
        } else {
            lastDayData = new HashMap<>();
        }
    }

    public byte[] getMarkV2() {
        if (markV2 == null) {
            markV2 = new byte[0];
        }
        return copyMark(markV2);
    }

    public void setMarkV2(byte[] markV2) {
        this.markV2Temp = copyMark(markV2);
    }

    public void updateMarkV2() {
        if (markV2Temp != null) {
            markV2 = copyMark(markV2Temp);
            markV2Temp = null;
        }
    }

    private byte[] copyMark(byte[] data) {
        byte[] result = new byte[data.length];
        System.arraycopy(data, 0, result, 0, data.length);
        return result;
    }

    public void clearMarkV2() {
        markV2 = null;
    }

    public void clearObjectModel() {
        synchronized (objectModelMutex) {
            objectModel = null;
        }
    }

    public List<DataModel> getObjectModel() throws MyStatisticException {
        synchronized (objectModelMutex) {
            LoadOPCRemote remote;
            try {
                remote = Utils.loadRMI();
            } catch (NamingException e) {
                throw new MyStatisticException("error load RMI", e);
            }

            if (objectModel == null) {
                objectModel = remote.loadObjectModel(serverName + "_" + controllerIdent, serverName);
                logger.info("Load object model from database {} model size {}", controllerIdent, objectModel.size());
            }

            List<DataModel> tempObjectModel = new ArrayList<>();
            for (DataModel model: objectModel) {
                tempObjectModel.add(new DataModel(model));
            }

            tempObjectModel = remote.loadObjectModelStartTimes(tempObjectModel);
            Collections.sort(tempObjectModel);

            logger.info("Object model for {} is: {}", controllerIdent, objectModel);
            logger.info("Object model with start time for {} is: {}", controllerIdent, tempObjectModel);
            return tempObjectModel;
        }
    }

    public void setControllerIdent(String controllerIdent) {
        this.controllerIdent = controllerIdent;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Statistic.class.getSimpleName() + "[", "]")
                .add("ip='" + ip + "'")
                .add("objectName='" + objectName + "'")
                .add("chanelCount=" + chanelCount)
                .add("inputTraffic=" + inputTraffic)
                .add("outputTraffic=" + outputTraffic)
                .add("monthTraffic=" + monthTraffic)
                .add("inputTrafficReal=" + inputTrafficReal)
                .add("outputTrafficReal=" + outputTrafficReal)
                .add("monthTrafficReal=" + monthTrafficReal)
                .add("lastRequestTime=" + lastRequestTime)
                .add("informWebConsole=" + informWebConsole)
                .add("block=" + block)
                .add("blockTypes=" + blockTypes)
                .add("ignoreTraffic=" + ignoreTraffic)
                .add("chanel=" + channel)
                .add("lastDayData=" + lastDayData)
                .toString();
    }
}
