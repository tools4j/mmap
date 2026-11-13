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

import net.openhft.chronicle.bytes.BytesStore;
import net.openhft.chronicle.bytes.PointerBytesStore;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

/**
 * Chronicle Bytes, the buffers of Chronicle Queue: direct memory through a {@link PointerBytesStore} moved to other
 * memory in place, heap memory through a {@link BytesStore} wrapping the byte array, created on each wrap since heap
 * stores cannot be moved. Chronicle has no acquire read; its volatile read compiles to the same instruction on arm64
 * (ldar) and x64 (a plain load). Release writes are its ordered writes.
 */
final class ChronicleBuffer implements BenchBuffer {
    private final PointerBytesStore pointer = new PointerBytesStore();
    private BytesStore<?, ?> store = pointer;

    @Override
    public int capacity() {
        return (int) store.capacity();
    }

    @Override
    public long getLong(final int index) {
        return store.readLong(index);
    }

    @Override
    public void putLong(final int index, final long value) {
        store.writeLong(index, value);
    }

    @Override
    public long getLongAcquire(final int index) {
        return store.readVolatileLong(index);
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        store.writeOrderedLong(index, value);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        return store.compareAndSwapLong(index, expectedValue, updateValue);
    }

    @Override
    public void putBytes(final int index, final BenchBuffer source, final int sourceIndex, final int length) {
        store.write(index, ((ChronicleBuffer) source).store, sourceIndex, length);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        pointer.set(address, capacity);
        store = pointer;
    }

    @Override
    public void wrap(final byte[] array, final MutableMemoryBuffer longArrayMemory) {
        store = BytesStore.wrap(array);
    }
}
