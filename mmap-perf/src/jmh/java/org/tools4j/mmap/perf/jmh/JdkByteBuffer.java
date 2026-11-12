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

import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The JDK's {@link ByteBuffer} in native byte order, with its own bounds checks only. It has no ordered access, which
 * is emulated with fences, and no compare-and-set. A byte buffer cannot be moved to other memory, so wrapping creates
 * a new one: around direct memory through a memory segment, around a byte array with {@link ByteBuffer#wrap(byte[])}.
 */
final class JdkByteBuffer implements BenchBuffer {
    private ByteBuffer buffer = ByteBuffer.allocate(0);

    @Override
    public int capacity() {
        return buffer.capacity();
    }

    @Override
    public long getLong(final int index) {
        return buffer.getLong(index);
    }

    @Override
    public void putLong(final int index, final long value) {
        buffer.putLong(index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        final long value = buffer.getLong(index);
        VarHandle.acquireFence();
        return value;
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        VarHandle.releaseFence();
        buffer.putLong(index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        throw new UnsupportedOperationException("No compare-and-set for ByteBuffer");
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        buffer.put(index, ((JdkByteBuffer) source).buffer, sourceIndex, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        buffer = MemorySegment.ofAddress(address).reinterpret(capacity).asByteBuffer().order(ByteOrder.nativeOrder());
    }

    @Override
    public void wrap(final byte[] array, final MutableMemoryBuffer longArrayMemory) {
        buffer = ByteBuffer.wrap(array).order(ByteOrder.nativeOrder());
    }
}
