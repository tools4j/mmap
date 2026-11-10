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
package org.tools4j.mmap.core.api;

import java.nio.ByteBuffer;

/**
 * A read-only {@link MemoryBuffer} that can be wrapped around any memory, and re-wrapped as often as needed without
 * allocating: a raw address, an array, a {@code ByteBuffer} or a range of another buffer.
 * <p>
 * A flyweight only reads the wrapped memory; it never writes, even if the memory is writable. To write, use a
 * {@link MutableMemoryBufferFlyweight}, which deliberately is no subtype of this interface, so that a read-only buffer
 * cannot be wrapped into a mutable one.
 * <p>
 * A flyweight wrapping an array or a {@code ByteBuffer} keeps a reference to it, so that its memory stays valid as
 * long as the flyweight wraps it. Wrapping a raw address leaves the validity of the memory to the caller.
 * <p>
 * Created with {@link MemoryBuffers#flyweight()}.
 */
public interface MemoryBufferFlyweight extends MemoryBuffer {

    /**
     * Wraps direct memory at a raw address.
     *
     * @param address the address of the first byte
     * @param capacity the number of bytes
     */
    void wrap(long address, int capacity);

    /**
     * Wraps a whole byte array; plain access only, see {@link MemoryBuffer}.
     *
     * @param array the array to wrap
     */
    void wrap(byte[] array);

    /**
     * Wraps a range of a byte array; plain access only, see {@link MemoryBuffer}.
     *
     * @param array the array to wrap
     * @param offset the offset of the first byte in the array
     * @param length the number of bytes
     */
    void wrap(byte[] array, int offset, int length);

    /**
     * Wraps a whole long array, with a capacity of 8 bytes per element; supports ordered access.
     *
     * @param array the array to wrap
     */
    void wrap(long[] array);

    /**
     * Wraps a range of a long array; supports ordered access.
     *
     * @param array the array to wrap
     * @param byteOffset the offset of the first byte in bytes from the start of the array
     * @param length the number of bytes
     */
    void wrap(long[] array, int byteOffset, int length);

    /**
     * Wraps a {@code ByteBuffer} from 0 to its capacity, ignoring its position, limit and byte order.
     *
     * @param buffer a direct byte buffer, or a heap byte buffer with an {@linkplain ByteBuffer#hasArray() accessible
     *               array}; ordered access only for direct ones
     * @throws IllegalArgumentException if the byte buffer is a heap buffer without accessible array, i.e. a read-only
     *                                  heap buffer
     */
    void wrap(ByteBuffer buffer);

    /**
     * Wraps a range of a {@code ByteBuffer} at an absolute offset, ignoring its position, limit and byte order.
     *
     * @param buffer a direct byte buffer, or a heap byte buffer with an {@linkplain ByteBuffer#hasArray() accessible
     *               array}; ordered access only for direct ones
     * @param offset the absolute offset of the first byte in the byte buffer
     * @param length the number of bytes
     * @throws IllegalArgumentException if the byte buffer is a heap buffer without accessible array, i.e. a read-only
     *                                  heap buffer
     */
    void wrap(ByteBuffer buffer, int offset, int length);

    /**
     * Wraps the memory of another buffer as a whole.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     */
    void wrap(MemoryBuffer buffer);

    /**
     * Wraps a range of another buffer's memory, as a view of an entry within a larger buffer, for instance.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     * @param index the index of the first byte in the other buffer
     * @param length the number of bytes
     */
    void wrap(MemoryBuffer buffer, int index, int length);

    /**
     * Returns the offset of index 0 within the wrapped array or {@code ByteBuffer}: index {@code i} of this
     * flyweight is element {@code wrapOffset() + i} of the wrapped byte array or byte buffer, and byte
     * {@code wrapOffset() + i} of the wrapped long array. A flyweight wrapping a range of another buffer has the
     * other buffer's offset plus the index of the range, so it stays relative to the underlying array or byte
     * buffer. A flyweight wrapping a raw address, or a buffer allocated by {@link MemoryBuffers}, has offset 0.
     *
     * @return the offset in bytes
     */
    int wrapOffset();

    /**
     * Returns the address of the first byte of the wrapped direct memory.
     *
     * @return the address
     * @throws IllegalStateException if this flyweight wraps heap memory, see {@link #isDirect()}
     */
    long address();
}
