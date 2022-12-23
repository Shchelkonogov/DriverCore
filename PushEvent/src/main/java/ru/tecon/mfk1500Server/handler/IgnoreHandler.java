package ru.tecon.mfk1500Server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Maksim Shchelkonogov
 */
public class IgnoreHandler extends ChannelInboundHandlerAdapter {

    private Logger logger = LoggerFactory.getLogger(IgnoreHandler.class);

    private String host;

    public IgnoreHandler(String host) {
        this.host = host;
    }

//    Часть контроллеров спамит соединениями. Экспериментальным методом выяснил,
//    что лучше игнорировать, чем отключать эти соединения
//
//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) {
//        try {
//            ctx.close();
//        } finally {
//            ReferenceCountUtil.release(msg);
//        }
//    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Ignore object {} with message {}", host, cause.getMessage());
        ctx.close();
    }
}
