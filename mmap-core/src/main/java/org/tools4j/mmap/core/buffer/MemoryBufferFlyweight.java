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

import org.tools4j.mmap.core.unsafe.Unsafe;

import java.nio.ByteBuffer;

/**
 * A {@link MutableMemoryBuffer} that can be wrapped around any memory, and re-wrapped as often as needed without
 * allocating: a raw address, an array, a {@code ByteBuffer} or a range of another buffer.
 * <p>
 * Whether it can write follows from the wrapped memory: wrapping a read-only {@code ByteBuffer}, or a range of a
 * buffer that {@linkplain #isReadOnly() is read-only}, makes it read-only, as does {@link #makeReadOnly()}, until it
 * wraps writable memory again. Writes to read-only memory throw {@link java.nio.ReadOnlyBufferException} instead of
 * crashing the JVM. Read-only is a property of the memory or a request of the user, not a lifetime guarantee: code
 * that must never write keeps the flyweight to itself, or hands it out typed as a {@link MemoryBuffer}.
 * <p>
 * A flyweight wrapping an array or a {@code ByteBuffer} keeps a reference to it, so that its memory stays valid as
 * long as the flyweight wraps it. Wrapping a raw address leaves the validity of the memory to the caller.
 * <p>
 * Created with {@link MemoryBuffers#flyweight()}.
 */
public interface MemoryBufferFlyweight extends MutableMemoryBuffer {

    /**
     * Wraps writable direct memory at a raw address; {@link MemoryBuffers#NULL_ADDRESS} with capacity 0 is the same
     * as {@link #unwrap()}. Read-only memory, such as a read-only memory mapping, must be made read-only with
     * {@link #makeReadOnly()} right after wrapping it.
     *
     * @param address the address of the first byte
     * @param capacity the number of bytes
     * @throws IllegalArgumentException if the capacity is negative, or positive at {@link MemoryBuffers#NULL_ADDRESS},
     *                                  as returned by {@link #address()} for heap memory
     */
    @Unsafe
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
     * Wraps a {@code ByteBuffer} from 0 to its capacity, ignoring its position, limit and byte order; read-only if the
     * byte buffer is read-only.
     *
     * @param buffer a direct or heap byte buffer, writable or read-only; ordered access and atomic operations only for
     *               direct ones
     * @throws IllegalArgumentException for a heap byte buffer over memory other than a {@code byte[]}, such as a
     *                                  {@code MemorySegment} of another array type, unless the memory is accessed
     *                                  through FFM, see {@link MemoryAccessMode}
     */
    void wrap(ByteBuffer buffer);

    /**
     * Wraps a range of a {@code ByteBuffer} at an absolute offset, ignoring its position, limit and byte order;
     * read-only if the byte buffer is read-only.
     *
     * @param buffer a direct or heap byte buffer, writable or read-only; ordered access and atomic operations only for
     *               direct ones
     * @param offset the absolute offset of the first byte in the byte buffer
     * @param length the number of bytes
     * @throws IllegalArgumentException for a heap byte buffer over memory other than a {@code byte[]}, such as a
     *                                  {@code MemorySegment} of another array type, unless the memory is accessed
     *                                  through FFM, see {@link MemoryAccessMode}
     */
    void wrap(ByteBuffer buffer, int offset, int length);

    /**
     * Wraps the memory of another buffer as a whole, read-only if the other buffer is read-only.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     * @throws IllegalArgumentException if the buffer is not created by {@link MemoryBuffers}, such as a test double,
     *                                  whose memory is unknown: wrap its memory directly instead, e.g. its address or
     *                                  array, whichever the implementation provides
     */
    void wrap(MemoryBuffer buffer);

    /**
     * Wraps a range of another buffer's memory, as a view of an entry within a larger buffer, for instance; read-only
     * if the other buffer is read-only.
     * <p>
     * The flyweight wraps the other buffer's memory, not the buffer itself: if an expandable buffer grows later, the
     * flyweight still wraps its old memory.
     *
     * @param buffer the buffer whose memory to wrap
     * @param index the index of the first byte in the other buffer
     * @param length the number of bytes
     * @throws IllegalArgumentException if the buffer is not created by {@link MemoryBuffers}, such as a test double,
     *                                  whose memory is unknown: wrap its memory directly instead, e.g. its address or
     *                                  array, whichever the implementation provides
     */
    void wrap(MemoryBuffer buffer, int index, int length);

    /**
     * Makes the wrapped memory read-only for this flyweight, and for flyweights wrapping a range of it later, until
     * it wraps other memory: writes then throw {@link java.nio.ReadOnlyBufferException}.
     */
    void makeReadOnly();

    /**
     * Wraps no memory, as a new flyweight: the capacity is 0, and the memory wrapped before is no longer referenced.
     */
    void unwrap();
}
