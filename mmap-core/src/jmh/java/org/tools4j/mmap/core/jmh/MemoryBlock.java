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

import org.agrona.BufferUtil;

import java.nio.ByteBuffer;

/**
 * Direct memory aligned to a cache line, or a heap byte array, to wrap buffers around.
 */
final class MemoryBlock {
    private static final int ALIGNMENT = 64;

    @SuppressWarnings({"unused", "FieldCanBeLocal"}) //keeps the direct memory reachable while buffers wrap it
    private final ByteBuffer directBuffer;
    private final long address;
    private final byte[] array;
    private final int capacity;

    private MemoryBlock(final ByteBuffer directBuffer, final long address, final byte[] array, final int capacity) {
        this.directBuffer = directBuffer;
        this.address = address;
        this.array = array;
        this.capacity = capacity;
    }

    static MemoryBlock allocate(final MemoryKind kind, final int capacity) {
        if (kind == MemoryKind.HEAP) {
            return new MemoryBlock(null, 0, new byte[capacity], capacity);
        }
        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(capacity + ALIGNMENT).alignedSlice(ALIGNMENT);
        return new MemoryBlock(directBuffer, BufferUtil.address(directBuffer), null, capacity);
    }

    void wrapInto(final BenchBuffer buffer) {
        if (array != null) {
            buffer.wrap(array);
        } else {
            buffer.wrap(address, capacity);
        }
    }
}
