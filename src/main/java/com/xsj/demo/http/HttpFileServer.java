package com.xsj.demo.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;

public class HttpFileServer {

    private final static Logger LOG = LoggerFactory.getLogger(HttpFileServer.class);

    private static final String DEFAULT_URL = "/";

    public void run(final int port, final String url) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("http-decoder", new HttpRequestDecoder())
                                .addLast("http-aggregator", new HttpObjectAggregator(65536 * 4))
                                .addLast("http-encoder", new HttpResponseEncoder())
                                .addLast("http-chunked", new ChunkedWriteHandler())
                                .addLast("fileServerHandler", new HttpFileServerHandler(url));
                    }
                });

        try {
            ChannelFuture future = bootstrap.bind(port).sync();
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    public static class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private final String url;
        private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
        private static final MimetypesFileTypeMap MISTYPE_FILESYSTEMS = new MimetypesFileTypeMap();

        public HttpFileServerHandler(String url) {
            this.url = url;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            if (!request.decoderResult().isSuccess()) {
                sendError(ctx, HttpResponseStatus.BAD_REQUEST);
                return;
            }
            if (request.method() != HttpMethod.GET) {
                sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
                return;
            }
            final String uri = request.uri();
            final String path = sanitizeUri(uri);
            if (path == null) {
                sendError(ctx, HttpResponseStatus.FORBIDDEN);
                return;
            }
            File file = new File(path);
            if (file.isHidden() || !file.exists()) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            if (file.isDirectory()) {
                if (uri.endsWith("/")) {
                    sendListing(ctx, file);
                } else {
                    sendRedirect(ctx, uri + "/");
                }
                return;
            }
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "r");
            } catch (FileNotFoundException e) {
                sendError(ctx, HttpResponseStatus.NOT_FOUND);
                return;
            }
            long fileLength = randomAccessFile.length();
            HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpUtil.setContentLength(resp, fileLength);
            setContextTypeHeader(resp, file);
            if(HttpUtil.isKeepAlive(request)){
                resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(resp);
            ChannelFuture cf = ctx.write(new ChunkedFile(randomAccessFile,0, fileLength,8192),
                    ctx.newProgressivePromise());
            cf.addListener(new ChannelProgressiveFutureListener() {

                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                    LOG.info("Transfer complete.");
                }

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) throws Exception {
                    if (total < 0) { // total unknown
                        LOG.info("Transfer progress: " + progress);
                    } else {
                        LOG.info("Transfer progress: " + progress + " / "
                                + total);
                    }
                }
            });
            ChannelFuture fu = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if(!HttpUtil.isKeepAlive(request)) {
                fu.addListener(ChannelFutureListener.CLOSE);
            }
        }


        private static void sendError(ChannelHandlerContext ctx,
                               HttpResponseStatus status) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                    Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            ctx.writeAndFlush(response)
                    .addListener(ChannelFutureListener.CLOSE);
        }

        private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

        private  String sanitizeUri(String uri) {
            try {
                uri = URLDecoder.decode(uri, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                try {
                    uri = URLDecoder.decode(uri, "ISO-8859-1");
                } catch (UnsupportedEncodingException e1) {
                    throw new IllegalStateException("unexpectedException");
                }
            }
            if (!uri.startsWith(url)) {
                return null;
            }
            if (!uri.startsWith("/")) {
                return null;
            }
            // 将硬编码的文件路径分隔符替换为本地系统操作符
            uri = uri.replace('/', File.separatorChar);
            if (uri.contains(File.separator + '.')
                    || uri.contains('.' + File.separator) || uri.startsWith(".")
                    || uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()) {
                return null;
            }
            return System.getProperty("user.dir") + File.separator + uri;
        }

        private static void sendListing(ChannelHandlerContext ctx, File dir) {
            File[] files = dir.listFiles();
            if (files == null) {
                sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                return;
            }
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/html; charset=UTF-8");
            StringBuilder buffer = new StringBuilder();
            String dirPath = dir.getPath();
            buffer.append("<!DOCTYPE html>\r\n");
            buffer.append("<html><head><title>");
            buffer.append(dirPath).append(" 目 录: ");
            buffer.append("</title></head><body>");
            buffer.append("<h3>");
            buffer.append(dirPath).append(" 目 录: ");
            buffer.append("</h3>\r\n");
            buffer.append("<ul>");
            buffer.append("<li>链接：<a href=\"../\">..</a></li>\r\n");
            for(File f : files){
                if(f.isHidden()||!f.canRead()){
                    continue;
                }
                String name = f.getName();
                if(!ALLOWED_FILE_NAME.matcher(name).matches()){
                    continue;
                }
                buffer.append("<li>链接：<a href=\"");
                buffer.append(name);
                buffer.append("\">");
                buffer.append(name);
                buffer.append("</a></li>\r\n");
            }
            buffer.append("</ul></body></html>\r\n");
            ByteBuf buf = Unpooled.copiedBuffer(buffer.toString().getBytes());
            resp.content().writeBytes(buf);
            buf.release();
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }


        private static void sendRedirect(ChannelHandlerContext ctx,String newUri){
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
            resp.headers().set(HttpHeaderNames.LOCATION,newUri);
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }

        private static void setContextTypeHeader(HttpResponse resp,File file){
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, MISTYPE_FILESYSTEMS.getContentType(file.getPath()));
        }

    }


    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        String url = DEFAULT_URL;
        if (args.length > 1)
            url = args[1];
        new HttpFileServer().run(port, url);
    }

}
