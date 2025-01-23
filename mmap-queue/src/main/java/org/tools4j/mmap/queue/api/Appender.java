/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.api;

import org.agrona.DirectBuffer;

import java.nio.ByteBuffer;

/**
 * API to append entries at the end of a {@link Queue}. Existing data can be appended through
 * {@link #append(DirectBuffer, int, int)}. Alternatively a new entry can be coded directly into the
 * {@link AppendingContext#buffer() queue buffer} provided through {@link #appending(int)}.
 */
public interface Appender extends AutoCloseable {
    /**
     * Appends an entry copying the data provided in the given array.
     *
     * @param bytes - entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if array length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(byte[] bytes);

    /**
     * Appends an entry copying the data provided in the given array.
     *
     * @param bytes - entry data
     * @param offset - offset of the entry data in the array
     * @param length - length of the entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(byte[] bytes, int offset, int length);

    /**
     * Appends an entry copying the data provided in the given buffer. Bytes will be copied from current
     * {@link ByteBuffer#position()} reading all {@link ByteBuffer#remaining()} bytes.
     * <p>
     * The source buffer will have its {@link ByteBuffer#position()} advanced as a result.
     *
     * @param buffer - buffer containing entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if the buffer's remaining bytes exceeds the maximum length for an entry allowed
     *                                  by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(ByteBuffer buffer);

    /**
     * Appends an entry copying the data provided in the given buffer. Bytes will be copied from current
     * {@link ByteBuffer#position()} for the given length.
     * <p>
     * The source buffer will have its {@link ByteBuffer#position()} advanced as a result.
     *
     * @param buffer - buffer containing entry data
     * @param length - length of the entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(ByteBuffer buffer, int length);

    /**
     * Appends an entry copying the data provided in the given buffer. Bytes will be copied from the buffer index to
     * the buffer index + length.
     * <p>
     * The source buffer will not have its {@link ByteBuffer#position()} advanced as a result.
     *
     * @param buffer - buffer containing entry data
     * @param offset - offset of the entry data in the buffer
     * @param length - length of the entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(ByteBuffer buffer, int offset, int length);

    /**
     * Appends an entry copying the data provided in the given buffer. For zero-copy coding directly into the queue
     * buffer the {@link #appending(int)} method can be used instead.
     *
     * @param buffer - direct buffer containing entry data
     * @param offset - offset of the entry data in the buffer
     * @param length - length of the entry data
     * @return  queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    long append(DirectBuffer buffer, int offset, int length);

    /**
     * Provides an appending context for zero-copy encoding of the new entry into the queue
     * {@link AppendingContext#buffer() buffer}. The queue buffer is guaranteed to have capacity for at least the
     * specified number of bytes.
     * <p>
     * Coding of the entry has to be committed when completed (or it can be aborted). This is best performed by using a
     * try-resource block:
     * <pre>
     * int maxLength = 1000;
     * try (AppendingContext context = appending(maxLength)) {
     *     MutableDirectBuffer buffer = context.buffer();
     *     //code entry data into buffer here
     *     int length = ...;//actual number of bytes encoded
     *     buffer.commit(length);
     * }
     * </pre>
     *
     * @param capacity the maximum length of the entry data in bytes
     * @return appending context for writing of entry data
     * @throws IllegalArgumentException if the specified capacity parameter exceeds the entry size limit
     * @throws IllegalStateException if the appender or the underlying queue is closed
     */
    AppendingContext appending(int capacity);

    /**
     * @return true if this appender is closed
     */
    boolean isClosed();

    /**
     * Closes the appender.
     */
    @Override
    void close();
}
