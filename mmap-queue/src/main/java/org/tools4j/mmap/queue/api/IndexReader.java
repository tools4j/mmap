/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

/**
 * API for reading and checking entry indices of a {@link Queue}.
 */
public interface IndexReader extends AutoCloseable {

    /**
     * Returns the index of the first entry in the queue, or {@link Index#NULL} if the queue is empty.  Note that
     * if the queue is non-empty, the returns value will always be zero.
     *
     * @return zero if the queue is non-empty, and {@link Index#NULL} otherwise
     * @throws IllegalStateException if queue or this index reader is closed
     */
    default long firstIndex() {
        return hasEntry(Index.FIRST) ? Index.FIRST : Index.NULL;
    }

    /**
     * Returns the index of the last entry in the queue, or {@link Index#NULL} if the queue is empty.
     * <p>
     * Note that consecutive calls may return different results if entries are appended in the background.  Note also
     * that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return a non-negative entry index of the last entry in the queue, or {@link Index#NULL} if the queue is empty
     * @throws IllegalStateException if queue or this index reader is closed
     */
    long lastIndex();

    /**
     * Returns true if a valid entry exists at the specified index, and false otherwise.
     *
     * @param index zero-based entry index
     * @return true if an entry is available at the given index, and false otherwise
     * @throws IllegalStateException if queue or this index reader is closed
     */
    boolean hasEntry(long index);

    /**
     * Returns the number of entries in the queue, or zero if the queue is empty.
     * <p>
     * Note that consecutive calls may return different results if entries are appended in the background.  Note also
     * that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return the current number of entries in the queue
     * @see #lastIndex()
     * @throws IllegalStateException if queue or this index reader is closed
     */
    default long size() {
        return 1 + lastIndex();//works also with NULL
    }

    /**
     * Returns true if the queue is empty, and false otherwise.
     *
     * @return true if the queue contains no entries, and false otherwise
     * @throws IllegalStateException if queue or this index reader is closed
     */
    default boolean isEmpty() {
        return hasEntry(Index.FIRST);
    }

    /**
     * @return true if this reader is closed
     */
    boolean isClosed();

    /**
     * Closes this reader.
     */
    @Override
    void close();
}
