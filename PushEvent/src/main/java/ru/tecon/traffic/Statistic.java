package ru.tecon.traffic;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.driverCoreClient.model.LastData;
import ru.tecon.model.WebStatistic;

import javax.naming.NamingException;
import java.io.*;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;

/**
 * Класс статистики работы сервера MFK1500
 * @author Maksim Shchelkonogov
 */
public class Statistic implements Serializable {

    private static final long serialVersionUID = 5218038278503278808L;

    private static final Logger LOGGER = Logger.getLogger(Statistic.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private String ip;
    private String objectName;

    private AtomicInteger socketCount = new AtomicInteger(0);
    private AtomicInteger inputTraffic = new AtomicInteger(0);
    private AtomicInteger outputTraffic = new AtomicInteger(0);
    private AtomicInteger monthTraffic = new AtomicInteger(0);
    private AtomicInteger inputTrafficReal = new AtomicInteger(0);
    private AtomicInteger outputTrafficReal = new AtomicInteger(0);
    private AtomicInteger monthTrafficReal = new AtomicInteger(0);

    private LocalDateTime lastRequestTime = LocalDateTime.now();

    private boolean informWebConsole;

    private boolean block = false;
    private Set<BlockType> blockTypes = new HashSet<>();

    private boolean ignoreTraffic = false;

    private Map<String, AtomicInteger> lastDayData = new HashMap<>();

    private transient Socket socket;

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
            objectName = Utils.loadRMI().loadObjectName(ProjectProperty.getServerName(), ip);
            LOGGER.info("updated object name for ip: " + ip);
            if (update) {
                update();
            }
        } catch (NamingException e) {
            LOGGER.log(Level.WARNING, "RMI load error", e);
        }
    }

    /**
     * Проверка открыт ли socket
     * @return true - открыт, false - закрыт
     */
    public boolean isSocketOpen() {
        return Objects.nonNull(socket) && !socket.isClosed();
    }

    /**
     * Устанавливаем socket
     * @param socket socket
     */
    public void setSocket(Socket socket) {
        close();
        socketCount.addAndGet(1);
        this.socket = socket;
        update();
    }

    /**
     * @return количество сокетов, которые открывал контроллер
     */
    public int getSocketCount() {
        return socketCount.get();
    }

    /**
     * Сбрасывает количество сокетов, которые открывал контроллер.
     * Если socket в данный момент открыт, то новое количество будет 1 иначе 0
     */
    public void clearSocketCount() {
        if (!isSocketOpen()) {
            socketCount.set(0);
        } else {
            socketCount.set(1);
        }
        update();
    }

    /**
     * Закрытие открытого socket, если такой открыт в данный момент
     */
    public void close() {
        if (isSocketOpen()) {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "close socket error", e);
            }
        }
    }

    /**
     * Метод проверяет не завис ли socket соединение с контроллером, по причине работы контроллера
     * @return true если завис
     */
    public boolean isSocketHung() {
        return isSocketOpen() && (Math.abs(HOURS.between(lastRequestTime, LocalDateTime.now())) > 2);
    }

    /**
     * Метод увеличивает количество входящего трафика
     * @param count количество байт на сколько надо увеличить трафик
     */
    public void updateInputTraffic(int count) {
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
    public void updateOutputTraffic(int count) {
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
                (ignoreTraffic ? "no limit" : Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit()));
    }

    /**
     * @return текстовое описание общего месячного трафика
     */
    public String getMonthTraffic() {
        return Utils.humanReadableByteCountBin(monthTraffic.get()) + " (" +
                Utils.humanReadableByteCountBin(monthTrafficReal.get()) + ") из " +
                (ignoreTraffic ? "no limit" : Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit() * LocalDate.now().lengthOfMonth()));
    }

    /**
     * Проверка на привышение допустимого трафика,
     * в случае превышение включается блокировка по трафику
     */
    private void checkTraffic() {
        if (ignoreTraffic) {
            return;
        }
        if ((inputTraffic.get() + outputTraffic.get()) > ProjectProperty.getTrafficLimit()) {
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
    private int roundTraffic(int count) {
        return ((int) Math.ceil(count / 1024d)) * 1024;
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
                LOGGER.log(Level.WARNING, "RMI load error", e);
            }
        }
    }

    public WebStatistic getWebStatistic() {
        return new WebStatistic(ProjectProperty.getServerName(), getIp(), getObjectName(),
                String.valueOf(getSocketCount()), getBlockToString(), getLastRequestTime(),
                getInputTraffic(), getOutputTraffic(), getTraffic(), getMonthTraffic());
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
    public void addData(int bufferNumber, int eventCode, int size) {
        String configGroupIdentifier = bufferNumber + ":" + eventCode + ":" + size;

        if (lastDayData == null) {
            lastDayData = new HashMap<>();
        }

        if (lastDayData.containsKey(configGroupIdentifier)) {
            lastDayData.get(configGroupIdentifier).addAndGet(1);
        } else {
            lastDayData.put(configGroupIdentifier, new AtomicInteger(1));
        }
    }

    /**
     * @return список последних переданных групп данных
     */
    public List<LastData> getLastDayDataGroups() {
        List<LastData> lastDayDataGroups = new ArrayList<>();

        LOGGER.info("last day group data is: " + lastDayData);

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
                                value.get()));
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

    @Override
    public String toString() {
        return new StringJoiner(", ", Statistic.class.getSimpleName() + "[", "]")
                .add("ip='" + ip + "'")
                .add("objectName='" + objectName + "'")
                .add("socketCount=" + socketCount)
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
                .add("socket=" + socket)
                .add("lastDayData=" + lastDayData)
                .toString();
    }
}
