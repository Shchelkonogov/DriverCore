package ru.tecon.traffic;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.model.WebStatistic;
import ru.tecon.server.EchoThread;

import javax.naming.NamingException;
import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class Statistic implements Serializable {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private String ip;
    private String objectName;
    private Socket socket;
    private LocalDateTime socketStartTime;
    private AtomicInteger socketCount = new AtomicInteger(0);
    private EchoThread thread;
    private AtomicInteger inputTraffic = new AtomicInteger(0);
    private AtomicInteger outputTraffic = new AtomicInteger(0);
    private AtomicInteger monthTraffic = new AtomicInteger(0);
    private LocalDateTime trafficStartTime = LocalDateTime.now();
    private boolean block = false;
    private boolean useRMI;

    private boolean linkedBlock = false;
    private boolean serverErrorBlock = false;

    private Event event;

    public Statistic(String ip, Event event, boolean useRMI) {
        this.ip = ip;
        this.event = event;
        this.useRMI = useRMI;
        updateObjectName();
        if (event != null) {
            event.addItem(this);
        }
    }

    public Statistic(String ip, Event event) {
        this(ip, event, true);
    }

    public void updateObjectName() {
        if (useRMI) {
            try {
                objectName = Utils.loadRMI().loadObjectName(ProjectProperty.getServerName(), ip);
            } catch (NamingException e) {
                e.printStackTrace();
            }
        }
    }

    public void setSocket(Socket socket) {
        if ((this.socket != null) && !this.socket.isClosed()) {
            try {
                this.socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socketStartTime = LocalDateTime.now();
        socketCount.addAndGet(1);
        this.socket = socket;
        update();
    }

    public void setThread(EchoThread thread) {
        this.thread = thread;
    }

    public void close() {
        if ((socket != null) && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        thread = null;
    }

    public void updateInputTraffic(int count) {
        inputTraffic.addAndGet(count);
        monthTraffic.addAndGet(count);
        checkTraffic();
        update();
    }

    public void updateOutputTraffic(int count) {
        outputTraffic.addAndGet(count);
        monthTraffic.addAndGet(count);
        checkTraffic();
        update();
    }

    public String getInputTraffic() {
        return Utils.humanReadableByteCountBin(inputTraffic.get());
    }

    public String  getOutputTraffic() {
        return Utils.humanReadableByteCountBin(outputTraffic.get());
    }

    public String getTraffic() {
        return Utils.humanReadableByteCountBin(inputTraffic.get() + outputTraffic.get()) + " из " +
                Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit());
    }

    public String getSocketStartTimeString() {
        return socketStartTime == null ? "" : socketStartTime.format(FORMATTER);
    }

    public String getTrafficStartTimeString() {
        return trafficStartTime == null ? "" : trafficStartTime.format(FORMATTER);
    }

    public int getSocketCount() {
        return socketCount.get();
    }

    public String getIp() {
        return ip;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getMonthTraffic() {
        return Utils.humanReadableByteCountBin(monthTraffic.get()) + " из " +
                Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit() * LocalDate.now().lengthOfMonth());
    }

    public void setBlock(boolean block) {
        this.block = block;
        if (!block) {
            linkedBlock = false;
            serverErrorBlock = false;
        }
        update();
    }

    public boolean isBlock() {
        return block;
    }

    public String getBlockToString() {
        if (isBlock()) {
            if (linkedBlock) {
                return "Не слинковано";
            }
            if (serverErrorBlock) {
                return "Ошибка сервера";
            }
            return "Заблокировано";
        } else {
            return "Свободно";
        }
    }

    public void linkedBlock() {
        this.linkedBlock = true;
        setBlock(true);
    }

    public void serverErrorBlock() {
        this.serverErrorBlock = true;
        setBlock(true);
    }

    private void update() {
        if (event != null) {
            event.update();
        }
        if (useRMI) {
            try {
                Utils.loadRMI().uploadStatistic(getWebStatistic());
            } catch (NamingException e) {
                e.printStackTrace();
            }
        }
    }

    private void checkTraffic() {
        if ((inputTraffic.get() + outputTraffic.get()) > ProjectProperty.getTrafficLimit()) {
            setBlock(true);
            if ((socket != null) && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void clearDayTraffic() {
        inputTraffic.set(0);
        outputTraffic.set(0);
        setBlock(false);
    }

    public void clearMonthTraffic() {
        monthTraffic.set(0);
    }

    public WebStatistic getWebStatistic() {
        return new WebStatistic(ProjectProperty.getServerName(), getIp(), getObjectName(), getSocketStartTimeString(),
                String.valueOf(getSocketCount()), getBlockToString(), getTrafficStartTimeString(),
                getInputTraffic(), getOutputTraffic(), getTraffic(), getMonthTraffic());
    }
}
