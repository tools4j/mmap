/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.tools4j.mmap.perf.jmh;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

import java.lang.foreign.MemorySegment;

/**
 * Netty's {@link ByteBuf}, unpooled, accessed little endian like the other implementations. It has no ordered access
 * and no compare-and-set, and cannot be moved to other memory, so wrapping creates a new one: around direct memory
 * through a byte buffer of a memory segment, around a byte array with {@link Unpooled#wrappedBuffer(byte[])}.
 * <p>
 * Whether Netty uses Unsafe is chosen once per JVM by system property {@code io.netty.noUnsafe}, see
 * {@link Implementation#NETTY_UNSAFE}: direct memory then goes through Unsafe, otherwise, the default on Java 24 and
 * later, through var handles on the byte buffer. Wrapped byte arrays always go through var handles on the array.
 */
final class NettyBuffer implements BenchBuffer {
    private ByteBuf buffer = Unpooled.EMPTY_BUFFER;

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public long getLong(final int index) {
        return buffer.getLongLE(index);
    }

    @Override
    public void putLong(final int index, final long value) {
        buffer.setLongLE(index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        throw new UnsupportedOperationException("No ordered access for Netty's ByteBuf");
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        throw new UnsupportedOperationException("No ordered access for Netty's ByteBuf");
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        throw new UnsupportedOperationException("No compare-and-set for Netty's ByteBuf");
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        buffer.setBytes(index, ((NettyBuffer) source).buffer, sourceIndex, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        buffer = Unpooled.wrappedBuffer(MemorySegment.ofAddress(address).reinterpret(capacity).asByteBuffer());
    }

    @Override
    public void wrap(final byte[] array, final MutableMemoryBuffer longArrayMemory) {
        buffer = Unpooled.wrappedBuffer(array);
    }
}
