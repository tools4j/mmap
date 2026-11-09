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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import static org.tools4j.mmap.core.jmh.Ffm.ALL;
import static org.tools4j.mmap.core.jmh.Ffm.HEAP_LONG;
import static org.tools4j.mmap.core.jmh.Ffm.LONG;
import static org.tools4j.mmap.core.jmh.Ffm.LONG_UNALIGNED;

/**
 * Direct or heap memory in one class, branching on the array (null for direct memory) on every access.
 */
final class FfmBranchBuffer implements BenchBuffer {
    private byte[] array;
    private long address;
    private int capacity;

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long getLong(final int index) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        final byte[] heap = array;
        return heap != null ? (long) HEAP_LONG.get(heap, index) : (long) LONG_UNALIGNED.get(ALL, address + index);
    }

    @Override
    public void putLong(final int index, final long value) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        final byte[] heap = array;
        if (heap != null) {
            HEAP_LONG.set(heap, index, value);
        } else {
            LONG_UNALIGNED.set(ALL, address + index, value);
        }
    }

    @Override
    public long getLongAcquire(final int index) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        final byte[] heap = array;
        return heap != null ? Ffm.getLongAcquire(heap, index) : (long) LONG.getAcquire(ALL, address + index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        final byte[] heap = array;
        if (heap != null) {
            Ffm.putLongRelease(heap, index, value);
        } else {
            LONG.setRelease(ALL, address + index, value);
        }
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        if (array != null) {
            throw Ffm.noHeapCompareAndSet();
        }
        return LONG.compareAndSet(ALL, address + index, expectedValue, updateValue);
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        final FfmBranchBuffer src = (FfmBranchBuffer) source;
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(sourceIndex, length, src.capacity);
        final byte[] srcArray = src.array;
        final byte[] dstArray = array;
        if (srcArray == null) {
            if (dstArray == null) {
                MemorySegment.copy(ALL, src.address + sourceIndex, ALL, address + index, length);
            } else {
                MemorySegment.copy(ALL, ValueLayout.JAVA_BYTE, src.address + sourceIndex, dstArray, index, length);
            }
        } else if (dstArray == null) {
            MemorySegment.copy(srcArray, sourceIndex, ALL, ValueLayout.JAVA_BYTE, address + index, length);
        } else {
            System.arraycopy(srcArray, sourceIndex, dstArray, index, length);
        }
    }

    @Override
    public void wrap(final long address, final int capacity) {
        this.array = null;
        this.address = address;
        this.capacity = capacity;
    }

    @Override
    public void wrap(final byte[] array) {
        this.array = array;
        this.address = 0;
        this.capacity = array.length;
    }
}
