package ru.tecon.traffic;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

class StatisticSer implements Serializable {

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
    private boolean block = false;
    private boolean useRMI;
    private boolean linkedBlock = false;
    private boolean serverErrorBlock = false;

    String getIp() {
        return ip;
    }

    void setIp(String ip) {
        this.ip = ip;
    }

    String getObjectName() {
        return objectName;
    }

    void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    AtomicInteger getSocketCount() {
        return socketCount;
    }

    AtomicInteger getInputTraffic() {
        return inputTraffic;
    }

    AtomicInteger getOutputTraffic() {
        return outputTraffic;
    }

    AtomicInteger getMonthTraffic() {
        return monthTraffic;
    }

    LocalDateTime getLastRequestTime() {
        return lastRequestTime;
    }

    void setLastRequestTime(LocalDateTime lastRequestTime) {
        this.lastRequestTime = lastRequestTime;
    }

    AtomicInteger getInputTrafficReal() {
        return inputTrafficReal;
    }

    AtomicInteger getOutputTrafficReal() {
        return outputTrafficReal;
    }

    AtomicInteger getMonthTrafficReal() {
        return monthTrafficReal;
    }

    boolean isBlock() {
        return block;
    }

    void setBlock(boolean block) {
        this.block = block;
    }

    boolean isUseRMI() {
        return useRMI;
    }

    void setUseRMI(boolean useRMI) {
        this.useRMI = useRMI;
    }

    boolean isLinkedBlock() {
        return linkedBlock;
    }

    void setLinkedBlock(boolean linkedBlock) {
        this.linkedBlock = linkedBlock;
    }

    boolean isServerErrorBlock() {
        return serverErrorBlock;
    }

    void setServerErrorBlock(boolean serverErrorBlock) {
        this.serverErrorBlock = serverErrorBlock;
    }
}
