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
package org.tools4j.mmap.core.buffer.impl;

import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.buffer.MutableMemoryBufferFlyweight;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The kinds of memory a {@link MutableMemoryBuffer} of {@link MemoryBufferImpl} can access in the tests, each created
 * with a given capacity, a multiple of 8.
 */
enum MemoryKind {
    HEAP(true, false, MemoryBuffers::allocate),
    DIRECT(true, false, MemoryBuffers::allocateDirect),
    LONG_ARRAY_RANGE(true, false, capacity -> MemoryBuffers.wrap(new long[capacity / 8 + 2], 1, capacity / 8)),
    BYTE_ARRAY_RANGE(false, false, capacity -> flyweight(buffer -> buffer.wrap(new byte[capacity + 5], 3, capacity))),
    HEAP_BYTE_BUFFER(false, false,
            capacity -> flyweight(buffer -> buffer.wrap(ByteBuffer.allocate(capacity + 5), 3, capacity))),
    DIRECT_BYTE_BUFFER(true, false,
            capacity -> flyweight(buffer -> buffer.wrap(ByteBuffer.allocateDirect(capacity + 8), 8, capacity))),
    RAW_ADDRESS(true, false, capacity -> {
        final MutableMemoryBuffer memory = keepAlive(MemoryBuffers.allocateDirect(capacity));
        return flyweight(buffer -> buffer.wrap(((MutableMemoryBufferFlyweight) memory).address(), capacity));
    }),
    HEAP_RANGE(true, false, capacity -> flyweight(buffer -> buffer.wrap(MemoryBuffers.allocate(capacity + 16), 8,
            capacity))),
    EXPANDABLE(true, true, MemoryBuffers::expandable),
    EXPANDABLE_DIRECT(true, true, MemoryBuffers::expandableDirect);

    private static final List<Object> KEEP_ALIVE = new ArrayList<>();

    final boolean orderedAccess;
    final boolean expandable;
    private final IntFunction<MutableMemoryBuffer> factory;

    MemoryKind(final boolean orderedAccess, final boolean expandable,
               final IntFunction<MutableMemoryBuffer> factory) {
        this.orderedAccess = orderedAccess;
        this.expandable = expandable;
        this.factory = factory;
    }

    MutableMemoryBuffer create(final int capacity) {
        return factory.apply(capacity);
    }

    private static MutableMemoryBuffer flyweight(final Consumer<MutableMemoryBufferFlyweight> wrapping) {
        final MutableMemoryBufferFlyweight buffer = MemoryBuffers.mutableFlyweight();
        wrapping.accept(buffer);
        return buffer;
    }

    private static MutableMemoryBuffer keepAlive(final MutableMemoryBuffer memory) {
        KEEP_ALIVE.add(memory);
        return memory;
    }
}
