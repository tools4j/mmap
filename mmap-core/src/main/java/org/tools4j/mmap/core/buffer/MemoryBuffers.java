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
package org.tools4j.mmap.core.buffer;

import org.tools4j.mmap.core.buffer.impl.MemoryBufferImpl;

/**
 * Creates buffers and flyweights.
 * <p>
 * All buffers are instances of one internal class, so that call sites using them stay monomorphic, whatever memory
 * they access. Which mechanism accesses the memory is chosen once per JVM, see {@link MemoryAccessMode}. Only
 * {@linkplain MemoryBinding#WRAPPABLE flyweights} can be wrapped and unwrapped: the other buffers throw
 * {@link UnsupportedOperationException}, even when cast to {@link MemoryBufferFlyweight}.
 */
public enum MemoryBuffers {
    ;

    /** The null address, invalid for direct memory. */
    public static final long NULL_ADDRESS = 0;

    /**
     * A buffer with capacity 0, which cannot be wrapped, not even after a cast to {@link MemoryBufferFlyweight}, so
     * that it can be shared, e.g. as the buffer of something not ready for data access.
     */
    public static final MutableMemoryBuffer EMPTY = MemoryBufferImpl.empty();

    /**
     * Returns a new flyweight, initially wrapping no memory, see {@link MemoryBufferFlyweight#unwrap()}.
     *
     * @return the flyweight
     */
    public static MemoryBufferFlyweight flyweight() {
        return MemoryBufferImpl.flyweight();
    }

    /**
     * Allocates a heap buffer backed by a {@code long[]}, which supports ordered access and atomic operations.
     *
     * @param capacity the capacity in bytes, rounded up to a multiple of 8 for the array, but not for the buffer
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer allocate(final int capacity) {
        return MemoryBufferImpl.allocate(capacity);
    }

    /**
     * Returns a new heap buffer over an existing {@code long[]}, sharing it without copying, which supports ordered
     * access and atomic operations like an {@linkplain #allocate(int) allocated} heap buffer.
     * <p>
     * Unlike wrapping a flyweight, this allocates: the buffer, and for some memory access mechanisms its view of the
     * array. To access ranges of the array without allocating, wrap flyweights around the returned buffer.
     *
     * @param array the array, with a capacity of 8 bytes per element
     * @return the buffer
     * @throws IllegalArgumentException if the array has more than {@code Integer.MAX_VALUE / 8} elements
     */
    public static MutableMemoryBuffer wrap(final long[] array) {
        return MemoryBufferImpl.wrap(array, 0, array.length);
    }

    /**
     * Returns a new heap buffer over a range of elements of an existing {@code long[]}, sharing it without copying,
     * which supports ordered access and atomic operations like an {@linkplain #allocate(int) allocated} heap buffer.
     * Index 0 of the buffer is the first byte of element {@code offset}, and the capacity is 8 bytes per element.
     * <p>
     * Unlike wrapping a flyweight, this allocates: the buffer, and for some memory access mechanisms its view of the
     * array. To access ranges of the array without allocating, also ranges not starting at an element, wrap
     * flyweights around the returned buffer.
     *
     * @param array the array
     * @param offset the index of the first element
     * @param length the number of elements
     * @return the buffer
     * @throws IndexOutOfBoundsException if the range is not within the array
     * @throws IllegalArgumentException if the range has more than {@code Integer.MAX_VALUE / 8} elements
     */
    public static MutableMemoryBuffer wrap(final long[] array, final int offset, final int length) {
        return MemoryBufferImpl.wrap(array, offset, length);
    }

    /**
     * Allocates a direct buffer whose first byte is aligned to a cache line, freed by the garbage collector once the
     * buffer is no longer referenced.
     *
     * @param capacity the capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer allocateDirect(final int capacity) {
        return MemoryBufferImpl.allocateDirect(capacity);
    }

    /**
     * Allocates an expandable heap buffer backed by a {@code long[]}, growing on writes beyond its capacity.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer expandable(final int initialCapacity) {
        return MemoryBufferImpl.expandable(initialCapacity);
    }

    /**
     * Allocates an expandable direct buffer, growing on writes beyond its capacity; growing moves its memory, so
     * flyweights wrapping it see the old memory.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer expandableDirect(final int initialCapacity) {
        return MemoryBufferImpl.expandableDirect(initialCapacity);
    }
}
