package ru.tecon.mfk1500Server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.Utils;
import ru.tecon.controllerData.ControllerConfig;
import ru.tecon.instantData.InstantDataService;
import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.MFK1500Server;
import ru.tecon.mfk1500Server.message.MessageService;
import ru.tecon.model.Command;
import ru.tecon.traffic.BlockType;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Maksim Shchelkonogov
 */
public class CommandHandler extends SimpleChannelInboundHandler<Command> {

    private static Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Command command) {
        logger.info("Receive message {}", command);

        if ((command.getParameter("server") != null) &&
                (command.getParameter("server").equalsIgnoreCase(DriverProperty.getInstance().getServerName()))) {
            switch (command.getName()) {
                case "loadConfig":
                    ControllerConfig.uploadConfig(command.getParameter("url"));
                    break;
                case "loadInstantData":
                    InstantDataService.uploadInstantData(command.getParameter("url"), command.getParameter("rowID"));
                    break;
                case "block":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).block(BlockType.USER);
                    break;
                case "unblock":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).unblockAll();
                    break;
                case "info":
                    try {
                        Utils.loadRMI().sendInfo(command.getParameter("sessionID"),
                                ControllerConfig.getControllerInfo(command.getParameter("url")));
                    } catch (NamingException ex) {
                        logger.warn("Error load RMI", ex);
                    }
                    break;
                case "writeInfo":
                    ControllerConfig.setControllerInfo(command.getParameter("url"),command.getParameter("info"));
                    break;
                case "blockTraffic":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).setIgnoreTraffic(false);
                    break;
                case "unblockTraffic":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).setIgnoreTraffic(true);
                    break;
                case "synchronizeDate":
                    ControllerConfig.synchronizeDate(command.getParameter("url"));
                    break;
                case "remove":
                    MFK1500Server.removeStatistic(command.getParameter("url"));
                    break;
                case "getLastConfigNames":
                    try {
                        Utils.loadRMI().uploadLogData(command.getParameter("sessionID"),
                                MFK1500Server.getStatistic().get(command.getParameter("url")).getLastDayDataGroups());
                    } catch (NamingException | NullPointerException e) {
                        logger.warn("Error send last config names to client", e);
                    }
                    break;
                case "getConfigGroup":
                    try {
                        int bufferNumber = Integer.parseInt(command.getParameter("bufferNumber"));
                        int eventCode = Integer.parseInt(command.getParameter("eventCode"));
                        int size = Integer.parseInt(command.getParameter("size"));

                        Utils.loadRMI().uploadConfigNames(command.getParameter("sessionID"),
                                ControllerConfig.getConfigNames(bufferNumber, eventCode, size));
                    } catch (NumberFormatException e) {
                        logger.warn("Error parse group identifier", e);
                    } catch (NamingException e) {
                        logger.warn("Error upload config names", e);
                    }
                    break;
                case "clearMark":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).clearMarkV2();
                    break;
                case "clearModel":
                    MFK1500Server.getStatistic().get(command.getParameter("url")).clearObjectModel();
                    break;
            }
        } else {
            switch (command.getName()) {
                case "requestStatistic":
                    try {
                        Utils.loadRMI().uploadStatistic(command.getParameter("sessionID"),
                                MFK1500Server.getStatistic().values()
                                        .stream()
                                        .map(Statistic::getWebStatistic)
                                        .collect(Collectors.toList())
                        );
                    } catch (NamingException e) {
                        logger.warn("Error load RMI", e);
                    }
                    break;
                case "reSubDriver":
                    MFK1500Server.WORKER_SERVICE.schedule(MessageService::subscriptService, 5, TimeUnit.MINUTES);
                    break;
            }
        }
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Command error", cause);
        ctx.close();
    }
}
