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

import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

/**
 * The memory buffers of mmap-core: a flyweight wrapping the memory of the benchmark, or an expandable buffer, accessed
 * through the interface {@link MutableMemoryBuffer}, as code using them does.
 */
final class MemoryBufferBench implements BenchBuffer {
    private final MutableMemoryBuffer buffer;
    private final MemoryBufferFlyweight flyweight;

    private MemoryBufferBench(final MutableMemoryBuffer buffer, final MemoryBufferFlyweight flyweight) {
        this.buffer = buffer;
        this.flyweight = flyweight;
    }

    /** Returns a flyweight, wrapping the memory passed to the wrap methods */
    static MemoryBufferBench flyweight() {
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        return new MemoryBufferBench(flyweight, flyweight);
    }

    /** Returns an expandable buffer with the given initial capacity, which cannot wrap other memory */
    static MemoryBufferBench expandable(final BenchMemory kind, final int capacity) {
        return new MemoryBufferBench(kind == BenchMemory.DIRECT ? MemoryBuffers.expandableDirect(capacity) :
                MemoryBuffers.expandable(capacity), null);
    }

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
        return buffer.getLongAcquire(index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        buffer.putLongRelease(index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        return buffer.compareAndSetLong(index, expectedValue, updateValue);
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        buffer.putBytes(index, ((MemoryBufferBench) source).buffer, sourceIndex, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        flyweight.wrap(address, capacity);
    }

    @Override
    public void wrap(final byte[] array, final MutableMemoryBuffer longArrayMemory) {
        flyweight.wrap(longArrayMemory);
    }
}
