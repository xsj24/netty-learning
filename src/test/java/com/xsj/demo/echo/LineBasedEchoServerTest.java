package com.xsj.demo.echo;

import org.junit.Test;

import static org.junit.Assert.*;

public class LineBasedEchoServerTest {
    @Test
    public void bind() throws Exception {
        LineBasedEchoServer echoServer = new LineBasedEchoServer();
        echoServer.bind(5555);
    }

}