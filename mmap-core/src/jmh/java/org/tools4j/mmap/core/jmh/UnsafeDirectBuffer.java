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

import org.agrona.UnsafeApi;

import java.util.Objects;

/**
 * Direct memory only, accessed through Unsafe with a constant null base object, so every access is a raw memory
 * access rather than one that may address either the heap or raw memory.
 */
final class UnsafeDirectBuffer implements BenchBuffer {
    private long address;
    private int capacity;

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long getLong(final int index) {
        Bounds.checkLong(index, capacity);
        return UnsafeApi.getLong(null, address + index);
    }

    @Override
    public void putLong(final int index, final long value) {
        Bounds.checkLong(index, capacity);
        UnsafeApi.putLong(null, address + index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        Bounds.checkLong(index, capacity);
        return UnsafeApi.getLongAcquire(null, address + index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        Bounds.checkLong(index, capacity);
        UnsafeApi.putLongRelease(null, address + index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        Bounds.checkLong(index, capacity);
        return UnsafeApi.compareAndSetLong(null, address + index, expectedValue, updateValue);
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        final UnsafeDirectBuffer src = (UnsafeDirectBuffer) source;
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(sourceIndex, length, src.capacity);
        UnsafeApi.copyMemory(null, src.address + sourceIndex, null, address + index, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        this.address = address;
        this.capacity = capacity;
    }

    @Override
    public void wrap(final byte[] array) {
        throw new UnsupportedOperationException("Direct memory only");
    }
}
