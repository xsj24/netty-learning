package com.xsj.demo.echo;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by dengx on 2018/3/25.
 */
public class LineBasedEchoClientTest {
    @Test
    public void connect() throws Exception {
        LineBasedEchoClient echoClient = new LineBasedEchoClient();
        echoClient.connect(5555, "localhost");
    }

}