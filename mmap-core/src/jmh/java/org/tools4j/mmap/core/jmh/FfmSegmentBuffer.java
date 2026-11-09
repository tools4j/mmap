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
import java.util.Objects;

import static org.tools4j.mmap.core.jmh.Ffm.LONG;
import static org.tools4j.mmap.core.jmh.Ffm.LONG_UNALIGNED;

/**
 * Direct memory only, accessed through a segment per wrap sized to the buffer, so that the var handle's bounds check
 * matches the buffer's own; wrapping allocates the segment, so this variant only serves to diagnose the cost of the
 * bounds check against the global segment in {@link FfmDirectBuffer}.
 */
final class FfmSegmentBuffer implements BenchBuffer {
    private MemorySegment segment = MemorySegment.NULL;
    private int capacity;

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long getLong(final int index) {
        Bounds.checkLong(index, capacity);
        return (long) LONG_UNALIGNED.get(segment, (long) index);
    }

    @Override
    public void putLong(final int index, final long value) {
        Bounds.checkLong(index, capacity);
        LONG_UNALIGNED.set(segment, (long) index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        Bounds.checkLong(index, capacity);
        return (long) LONG.getAcquire(segment, (long) index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        Bounds.checkLong(index, capacity);
        LONG.setRelease(segment, (long) index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        Bounds.checkLong(index, capacity);
        return LONG.compareAndSet(segment, (long) index, expectedValue, updateValue);
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        final FfmSegmentBuffer src = (FfmSegmentBuffer) source;
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(sourceIndex, length, src.capacity);
        MemorySegment.copy(src.segment, sourceIndex, segment, index, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        this.segment = MemorySegment.ofAddress(address).reinterpret(capacity);
        this.capacity = capacity;
    }

    @Override
    public void wrap(final byte[] array) {
        throw new UnsupportedOperationException("Direct memory only");
    }
}
