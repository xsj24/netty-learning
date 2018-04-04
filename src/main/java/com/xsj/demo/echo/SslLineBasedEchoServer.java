package com.xsj.demo.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SslLineBasedEchoServer {

    private final static Logger LOG = LoggerFactory.getLogger(SslLineBasedEchoServer.class);

    public void bind(int port) throws Exception {
        ClassLoader loader = this.getClass().getClassLoader();
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        SslContext sslContext = null;
        try (InputStream keyCertChainInputStream = loader.getResourceAsStream("security/cert.crt");
             InputStream keyInputStream = loader.getResourceAsStream("security/private.pem")){
            sslContext = SslContextBuilder.forServer(keyCertChainInputStream, keyInputStream).build();
        }
        ServerBootstrap bootstrap = new ServerBootstrap();
        SslContext finalSslContext = sslContext;
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // SslHandler要放在最前面
                        SslHandler sslHandler = finalSslContext.newHandler(ch.alloc());
                        pipeline.addLast(sslHandler)
                                .addLast(new LineBasedFrameDecoder(2048))
                                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                                .addLast(new ChildChannelHandler());
                    }
                });

        try {
            ChannelFuture cf = bootstrap.bind(port).sync();
            cf.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }


    static class ChildChannelHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            String body = (String) msg;
            LOG.info(body);
            // response 回去的数据需加换行符
            ctx.writeAndFlush(Unpooled.wrappedBuffer((body + "\n").getBytes(StandardCharsets.UTF_8)))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }


    public static void main(String[] args) throws Exception {
        SslLineBasedEchoServer echoServer = new SslLineBasedEchoServer();
        echoServer.bind(5555);
    }

}
