package ru.tecon.mfk1500Server.handler;

import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import ru.tecon.traffic.Statistic;

/**
 * Подсчет входящего и исходящего трафика
 * @author Maksim Shchelkonogov
 */
public class MyChannelTrafficShapingHandler extends ChannelTrafficShapingHandler {

    private Statistic statistic;

    public MyChannelTrafficShapingHandler(Statistic statistic, long checkInterval) {
        super(checkInterval);
        this.statistic = statistic;
    }

    @Override
    protected void doAccounting(TrafficCounter counter) {
        if (counter.lastReadBytes() != 0) {
            statistic.updateInputTraffic(counter.lastReadBytes());
        }
        if (counter.lastWrittenBytes() != 0) {
            statistic.updateOutputTraffic(counter.lastWrittenBytes());
        }
        super.doAccounting(counter);
    }
}
