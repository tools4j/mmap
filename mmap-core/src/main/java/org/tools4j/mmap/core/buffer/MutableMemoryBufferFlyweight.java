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

import java.nio.ByteBuffer;

/**
 * A {@link MutableMemoryBuffer} that can be wrapped around any writable memory, and re-wrapped as often as needed
 * without allocating: a raw address, an array, a writable {@code ByteBuffer} or a range of another mutable buffer.
 * <p>
 * Deliberately no subtype of {@link MemoryBufferFlyweight}, which would let it wrap read-only buffers.
 * <p>
 * A flyweight wrapping an array or a {@code ByteBuffer} keeps a reference to it, so that its memory stays valid as
 * long as the flyweight wraps it. Wrapping a raw address leaves the validity of the memory to the caller.
 * <p>
 * Created with {@link MemoryBuffers#mutableFlyweight()}.
 */
public interface MutableMemoryBufferFlyweight extends MutableMemoryBuffer {

    /**
     * Wraps direct memory at a raw address; {@link MemoryBuffers#NULL_ADDRESS} with capacity 0 is the same as
     * {@link #unwrap()}.
     *
     * @param address the address of the first byte
     * @param capacity the number of bytes
     * @throws IllegalArgumentException if the capacity is negative, or positive at {@link MemoryBuffers#NULL_ADDRESS},
     *                                  as returned by {@link #address()} for heap memory
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
     * Wraps a writable {@code ByteBuffer} from 0 to its capacity, ignoring its position, limit and byte order.
     *
     * @param buffer a direct byte buffer, or a heap byte buffer; ordered access and atomic operations only for direct
     *               ones
     * @throws java.nio.ReadOnlyBufferException if the byte buffer is read-only
     */
    void wrap(ByteBuffer buffer);

    /**
     * Wraps a range of a writable {@code ByteBuffer} at an absolute offset, ignoring its position, limit and byte
     * order.
     *
     * @param buffer a direct byte buffer, or a heap byte buffer; ordered access and atomic operations only for direct
     *               ones
     * @param offset the absolute offset of the first byte in the byte buffer
     * @param length the number of bytes
     * @throws java.nio.ReadOnlyBufferException if the byte buffer is read-only
     */
    void wrap(ByteBuffer buffer, int offset, int length);

    /**
     * Wraps the memory of another mutable buffer as a whole.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     */
    void wrap(MutableMemoryBuffer buffer);

    /**
     * Wraps a range of another mutable buffer's memory, as a view of an entry within a larger buffer, for instance.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     * @param index the index of the first byte in the other buffer
     * @param length the number of bytes
     */
    void wrap(MutableMemoryBuffer buffer, int index, int length);

    /**
     * Wraps no memory, as a new flyweight: the capacity is 0, and the memory wrapped before is no longer referenced.
     */
    void unwrap();

    /**
     * Returns the offset of index 0 within the wrapped array or {@code ByteBuffer}: index {@code i} of this
     * flyweight is element {@code wrapOffset() + i} of the wrapped byte array or byte buffer. A flyweight wrapping
     * a range of another buffer has the other buffer's offset plus the index of the range, so it stays relative to
     * the underlying array or byte buffer; for a buffer created with {@link MemoryBuffers#wrap(long[], int, int)},
     * it is the byte offset from the start of its {@code long[]}, 8 bytes per element. A buffer allocated by
     * {@link MemoryBuffers}, and a flyweight wrapping a raw address, have offset 0.
     *
     * @return the offset in bytes
     */
    int wrapOffset();

    /**
     * Returns the address of the first byte of the wrapped direct memory, or {@link MemoryBuffers#NULL_ADDRESS} if
     * this flyweight wraps heap memory, see {@link #isDirect()}.
     *
     * @return the address, or {@link MemoryBuffers#NULL_ADDRESS} for heap memory
     */
    long address();
}
