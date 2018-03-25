package com.xsj.demo.echo;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by dengx on 2018/3/25.
 */
public class EchoClientTest {
    @Test
    public void connect() throws Exception {
        EchoClient client = new EchoClient();
        client.connect(5555, "localhost");
    }

}