package com.xsj.netty.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledHeapByteBuf;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.nio.charset.StandardCharsets;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

public class UnpooledTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testByteBuf() {
        // 无参构造器 - 堆上存放
        ByteBuf buffer = Unpooled.buffer();
        assertThat(buffer.capacity(), is(256)); // 默认容器初始大小: 256
        assertThat(buffer.maxCapacity(), is((1 << 31) - 1));
        assertThat(UnpooledHeapByteBuf.class.isInstance(buffer), is(true));
        /** 大端存放 **/
        buffer.writeInt(255);
        assertThat(buffer.order(), is(BIG_ENDIAN));
        buffer.markReaderIndex();
        assertThat(buffer.readableBytes(), is(4));
        assertThat(buffer.writableBytes(), is(256 - 4));

        assertThat(buffer.readInt(), is(255));
        buffer.resetReaderIndex();
        assertThat(buffer.readInt(), is(255));
    }


    @Test
    public void testWrappedBuffer() {
        byte[] bytes = "Hello World".getBytes();
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        assertThat(buffer.maxCapacity(), is(bytes.length));
        assertThat(buffer.writerIndex(), is(bytes.length));
        assertThat(buffer.array(), sameInstance(bytes));

        buffer.markReaderIndex();
        assertThat(buffer.readByte(), is((byte) 72));
        buffer.resetReaderIndex();

        CharSequence sequence = buffer.readCharSequence(buffer.maxCapacity(), StandardCharsets.UTF_8);
        assertThat(sequence, is("Hello World"));
        assertThat(buffer.readerIndex(), is(buffer.maxCapacity()));
        /** wrappedBuffer 不能再写 **/
        assertThat(buffer.isWritable(), is(false));
        expectedException.expect(IndexOutOfBoundsException.class);
        buffer.writeByte(23);
    }


    @Test
    public void testEmpty() {
        ByteBuf buf01 = Unpooled.buffer(0, 0);
        ByteBuf buf02 = Unpooled.buffer(0, 0);
        assertThat(buf01, instanceOf(EmptyByteBuf.class));
        assertThat(buf01, sameInstance(buf02));
        assertThat(buf01.isReadable(), is(false));
        assertThat(buf01.isWritable(), is(false));
    }

    @Test
    public void testCopiedBuffer() {
        byte[] bytes = "Hello World".getBytes();
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        assertThat(byteBuf.array(), not(sameInstance(bytes)));
        // 同 wrappedBuffer 一样不可写
        assertThat(byteBuf.isWritable(), is(false));
        byteBuf.release();
    }



}
