package ru.tecon.traffic;

import ru.tecon.server.EchoThread;

import java.io.IOException;
import java.net.Socket;
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
    private LocalDateTime trafficStartTime = LocalDateTime.now();

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
        update();
    }

    public void updateOutputTraffic(int count) {
        outputTraffic.addAndGet(count);
        update();
    }

    public int getInputTraffic() {
        return inputTraffic.get();
    }

    public int getOutputTraffic() {
        return outputTraffic.get();
    }

    public int getTraffic() {
        return inputTraffic.get() + outputTraffic.get();
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

    private void update() {
        if (event != null) {
            event.update();
        }
    }
}
