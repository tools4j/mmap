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
package org.tools4j.mmap.core.jmh;

import java.util.Objects;

import static org.tools4j.mmap.core.jmh.Ffm.HEAP_LONG;

/**
 * Heap memory only, accessed through a byte array view var handle, see {@link Ffm}.
 */
final class FfmHeapBuffer implements BenchBuffer {
    private byte[] array = new byte[0];
    private int capacity;

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long getLong(final int index) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        return (long) HEAP_LONG.get(array, index);
    }

    @Override
    public void putLong(final int index, final long value) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        HEAP_LONG.set(array, index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        return Ffm.getLongAcquire(array, index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        Ffm.putLongRelease(array, index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        throw Ffm.noHeapCompareAndSet();
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        final FfmHeapBuffer src = (FfmHeapBuffer) source;
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(sourceIndex, length, src.capacity);
        System.arraycopy(src.array, sourceIndex, array, index, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        throw new UnsupportedOperationException("Heap memory only");
    }

    @Override
    public void wrap(final byte[] array) {
        this.array = array;
        this.capacity = array.length;
    }
}
