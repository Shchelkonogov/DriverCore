package ru.tecon.traffic;

import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.server.EchoThread;

import java.io.IOException;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

public class Statistic {

    private String ip;
    private Socket socket;
    private LocalDateTime socketStartTime;
    private AtomicInteger socketCount = new AtomicInteger(0);
    private EchoThread thread;
    private AtomicInteger inputTraffic = new AtomicInteger(0);
    private AtomicInteger outputTraffic = new AtomicInteger(0);
    private AtomicInteger monthTraffic = new AtomicInteger(0);
    private LocalDateTime trafficStartTime = LocalDateTime.now();
    private boolean block = false;

    private Event event;

    public Statistic(String ip, Event event) {
        this.ip = ip;
        this.event = event;
        if (event != null) {
            event.addItem(this);
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

    public LocalDateTime getTrafficStartTime() {
        return trafficStartTime;
    }

    public LocalDateTime getSocketStartTime() {
        return socketStartTime;
    }

    public int getSocketCount() {
        return socketCount.get();
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getIp() {
        return ip;
    }

    public String getMonthTraffic() {
        return Utils.humanReadableByteCountBin(monthTraffic.get()) + " из " +
                Utils.humanReadableByteCountBin(ProjectProperty.getTrafficLimit() * LocalDate.now().lengthOfMonth());
    }

    public void setBlock(boolean block) {
        this.block = block;
        update();
    }

    public boolean isBlock() {
        return block;
    }

    private void update() {
        if (event != null) {
            event.update();
        }
    }

    private void checkTraffic() {
        if ((inputTraffic.get() + outputTraffic.get()) > ProjectProperty.getTrafficLimit()) {
            block = true;
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
        block = false;
    }

    public void clearMonthTraffic() {
        monthTraffic.set(0);
    }
}
