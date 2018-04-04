package com.xsj.demo.echo;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import static org.junit.Assert.*;

public class SslLineBasedEchoServerTest {

    @Test
    public void bind() throws Exception {
        SslLineBasedEchoServer echoServer = new SslLineBasedEchoServer();
        echoServer.bind(5555);
    }

    public static void connect() throws Exception {
        Certificate certificate = null;
        // 公钥证书
        try (InputStream keyCertChainInputStream = SslLineBasedEchoServer.class.getClassLoader().getResourceAsStream("security/cert.crt")) {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificate = certificateFactory.generateCertificate(keyCertChainInputStream);
        }
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("cert", certificate);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("sunx509");
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        SSLSocketFactory socketFactory = sslContext.getSocketFactory();

        Socket socket = null;
        OutputStream out = null;
        InputStream in = null;
        try {
            socket = socketFactory.createSocket("localhost", 5555);
            out = socket.getOutputStream();
            // 请求服务器
            String lines = "床前明月光\r\n疑是地上霜\r\n举头望明月\r\n低头思故乡\r\n";
            byte[] outputBytes = lines.getBytes("UTF-8");
            out.write(outputBytes);
            out.flush();
            in = socket.getInputStream();
            byte[] bytes = new byte[1024];
            int len = 0;
            while ((len = in.read(bytes))  > 0) {
                System.out.println(new String(bytes, 0, len, StandardCharsets.UTF_8));
            }
        } finally {
            // 关闭连接
            IOUtils.closeQuietly(socket);
            IOUtils.closeQuietly(out);
            IOUtils.closeQuietly(in);
        }

    }

    @Test
    public void testConnect() throws Exception {
        System.setProperty("javax.net.debug", "ssl,handshake");
        connect();
    }


}