package ru.tecon.traffic;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.model.WebStatistic;

import javax.naming.NamingException;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Statistic {

    private static Logger log = Logger.getLogger(Statistic.class.getName());

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private StatisticSer statisticSer = new StatisticSer();
    private Socket socket;

    private Event event;

    public Statistic(String ip, Event event) {
        this(ip, event, true);
    }

    public Statistic(String ip, Event event, boolean useRMI) {
        statisticSer.setIp(ip);
        statisticSer.setUseRMI(useRMI);
        this.event = event;
        updateObjectName();
        if (event != null) {
            event.addItem(this);
        }
    }

    /**
     * Метож выгружает имя объекта из базы
     */
    public void updateObjectName() {
        if (statisticSer.isUseRMI()) {
            try {
                statisticSer.setObjectName(Utils.loadRMI().loadObjectName(ProjectProperty.getServerName(),
                        statisticSer.getIp()));
            } catch (NamingException e) {
                log.log(Level.WARNING, "RMI load error", e);
            }
        }
    }

    public void setSocket(Socket socket) {
        close();
        statisticSer.getSocketCount().addAndGet(1);
        this.socket = socket;
        update();
    }

    public int getSocketCount() {
        return statisticSer.getSocketCount().get();
    }

    public void clearSocketCount() {
        if ((socket == null) || socket.isClosed()) {
            statisticSer.getSocketCount().set(0);
        } else {
            statisticSer.getSocketCount().set(1);
        }
    }

    /**
     * Метод закрувает socket
     */
    public void close() {
        if ((socket != null) && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "close socket error", e);
            }
        }
    }

    public void updateInputTraffic(int count) {
        statisticSer.getInputTrafficReal().addAndGet(count);
        statisticSer.getMonthTrafficReal().addAndGet(count);

        statisticSer.getInputTraffic().addAndGet(roundTraffic(count));
        statisticSer.getMonthTraffic().addAndGet(roundTraffic(count));

        statisticSer.setLastRequestTime(LocalDateTime.now());

        checkTraffic();
        update();
    }

    public void updateOutputTraffic(int count) {
        statisticSer.getOutputTrafficReal().addAndGet(count);
        statisticSer.getMonthTrafficReal().addAndGet(count);

        statisticSer.getOutputTraffic().addAndGet(roundTraffic(count));
        statisticSer.getMonthTraffic().addAndGet(roundTraffic(count));

        statisticSer.setLastRequestTime(LocalDateTime.now());

        checkTraffic();
        update();
    }

    public String getInputTraffic() {
        return Utils.humanReadableByteCountBin(statisticSer.getInputTraffic().get()) + " (" +
                Utils.humanReadableByteCountBin(statisticSer.getInputTrafficReal().get()) + ")";
    }

    public String getOutputTraffic() {
        return Utils.humanReadableByteCountBin(statisticSer.getOutputTraffic().get()) + " (" +
                Utils.humanReadableByteCountBin(statisticSer.getOutputTrafficReal().get()) + ")";
    }

    public String getTraffic() {
        return Utils.humanReadableByteCountBin(statisticSer.getInputTraffic().get() +
                statisticSer.getOutputTraffic().get()) + " (" +
                Utils.humanReadableByteCountBin(statisticSer.getInputTrafficReal().get() +
                statisticSer.getOutputTrafficReal().get()) + ") из " +
                Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit());
    }

    public String getMonthTraffic() {
        return Utils.humanReadableByteCountBin(statisticSer.getMonthTraffic().get()) + " (" +
                Utils.humanReadableByteCountBin(statisticSer.getMonthTrafficReal().get()) + ") из " +
                Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit() * LocalDate.now().lengthOfMonth());
    }

    private void checkTraffic() {
        if ((statisticSer.getInputTraffic().get() + statisticSer.getOutputTraffic().get()) > ProjectProperty.getTrafficLimit()) {
            setBlock(true);
        }
    }

    public void clearDayTraffic() {
        statisticSer.getInputTrafficReal().set(0);
        statisticSer.getOutputTrafficReal().set(0);

        statisticSer.getInputTraffic().set(0);
        statisticSer.getOutputTraffic().set(0);
        setBlock(false);
    }

    public void clearMonthTraffic() {
        statisticSer.getMonthTrafficReal().set(0);
        statisticSer.getMonthTraffic().set(0);
    }

    private int roundTraffic(int count) {
        return ((int) Math.ceil(count / 1024d)) * 1024;
    }

    public String getLastRequestTime() {
        return statisticSer.getLastRequestTime() == null ? "" : statisticSer.getLastRequestTime().format(FORMATTER);
    }

    public String getIp() {
        return statisticSer.getIp();
    }

    public String getObjectName() {
        return statisticSer.getObjectName();
    }

    public void setBlock(boolean block) {
        statisticSer.setBlock(block);
        if (block) {
            close();
        } else {
            statisticSer.setLinkedBlock(false);
            statisticSer.setServerErrorBlock(false);
        }
        update();
    }

    public boolean isBlock() {
        return statisticSer.isBlock();
    }

    public String getBlockToString() {
        if (isBlock()) {
            if (statisticSer.isLinkedBlock()) {
                return "Не слинковано";
            }
            if (statisticSer.isServerErrorBlock()) {
                return "Ошибка сервера";
            }
            return "Заблокировано";
        } else {
            return "Свободно";
        }
    }

    public void linkedBlock() {
        statisticSer.setLinkedBlock(true);
        setBlock(true);
    }

    public void serverErrorBlock() {
        statisticSer.setServerErrorBlock(true);
        setBlock(true);
    }

    private void update() {
        if (event != null) {
            event.update();
        }
        if (statisticSer.isUseRMI()) {
            try {
                Utils.loadRMI().uploadStatistic(getWebStatistic());
            } catch (NamingException e) {
                log.log(Level.WARNING, "RMI load error", e);
            }
        }
    }

    public WebStatistic getWebStatistic() {
        return new WebStatistic(ProjectProperty.getServerName(), getIp(), getObjectName(),
                String.valueOf(getSocketCount()), getBlockToString(), getLastRequestTime(),
                getInputTraffic(), getOutputTraffic(), getTraffic(), getMonthTraffic());
    }

    public void serialize(String path) {
        if (!Files.exists(Paths.get(path + "/statisticSer"))) {
            try {
                Files.createDirectory(Paths.get(path + "/statisticSer"));
            } catch (IOException e) {
                log.log(Level.WARNING, "create dir error", e);
            }
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(path + "/statisticSer/" + getIp().replaceAll("[.]", "_") + ".ser"))) {
            oos.writeObject(statisticSer);
        } catch (IOException e) {
            log.log(Level.WARNING, "serialize error", e);
        }
    }

    public void deserialize(String path) {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            statisticSer = (StatisticSer) ois.readObject();

            update();
        } catch (IOException | ClassNotFoundException e) {
            log.log(Level.WARNING, "deserialize error", e);
        }
    }
}