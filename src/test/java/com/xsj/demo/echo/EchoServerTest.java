package com.xsj.demo.echo;

import org.junit.Test;

import static org.junit.Assert.*;

public class EchoServerTest {
    @Test
    public void bind() throws Exception {
        EchoServer server = new EchoServer();
        server.bind(5555);
    }

}