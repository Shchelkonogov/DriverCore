package ru.tecon.mfk1500Server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.util.NettyRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tecon.mfk1500Server.handler.CommandHandler;

/**
 * @author Maksim Shchelkonogov
 */
public class MFK1500Server {

    private static Logger logger = LoggerFactory.getLogger(MFK1500Server.class);

    private ChannelFuture channelFuture;

    public void run() {
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup();


        NettyRuntime.availableProcessors();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            int localPort = ch.localAddress().getPort();
                            if (localPort == DriverProperty.getInstance().getMessageServicePort()) {
                                ch.pipeline().addLast(new ObjectDecoder(ClassResolvers.cacheDisabled(null)));
                                ch.pipeline().addLast(new CommandHandler());
                            }

//                            if (localPort == DriverProperty.getInstance().getListeningPort()) {
//                                ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65537, 0, 2, 0, 0));
//                                ch.pipeline().addLast(new PushEventHandler());
//                            }
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            logger.info("server start");
            channelFuture = bootstrap.bind(DriverProperty.getInstance().getMessageServicePort()).sync();

            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.warn("Mfk1500Server is interrupted", e);
        } catch (Exception e) {
            logger.warn("Mfk1500Server problem", e);
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
        logger.info("server end");
    }

    public void stop() throws InterruptedException {
        channelFuture.channel().close().sync().awaitUninterruptibly();
    }
}
