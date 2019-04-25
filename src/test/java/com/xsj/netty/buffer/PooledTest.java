package com.xsj.netty.buffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;


public class PooledTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testPooledByteBufAllocator() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false);
        assertThat(allocator.isDirectBufferPooled(), is(true));
        assertThat(allocator.metric().numHeapArenas(), is(16));
        assertThat(allocator.metric().chunkSize(), is(16 * 1024 * 1024)); // 16MB

        ByteBuf buffer = allocator.buffer(600);

        buffer.release();
    }
}
