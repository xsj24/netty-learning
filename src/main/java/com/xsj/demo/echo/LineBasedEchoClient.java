package com.xsj.demo.echo;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class LineBasedEchoClient {

    public void connect(int port, String host) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline()
                                .addLast(new LineBasedFrameDecoder(2048))
                                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                                .addLast(new EchoChannelHandler());
                    }
                });
        try {
            ChannelFuture c = b.connect(host, port).sync();
            c.channel().closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }

    }


    private static class EchoChannelHandler extends ChannelInboundHandlerAdapter {

        private final Logger LOG = LoggerFactory.getLogger(EchoChannelHandler.class);

        private final ByteBuf firstMessage;

        public EchoChannelHandler() {
            byte[] req = "测试数据：QUERY SEND TO SERVER\n".getBytes();
            firstMessage = Unpooled.buffer(req.length);
            firstMessage.writeBytes(req);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(firstMessage);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String res = (String) msg;
            LOG.info(res);
            ctx.disconnect();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            LOG.info(cause.getMessage(), cause);
            ctx.close();
        }
    }

}
