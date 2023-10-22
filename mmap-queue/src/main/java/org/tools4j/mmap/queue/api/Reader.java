/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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

/**
 * Interface for random read access to entries in the {@link Queue}.
 */
public interface Reader extends AutoCloseable {
    /**
     * Index returned by {@link ReadingContext#index()} if no entry is currently available for reading.
     */
    long NULL_INDEX = -1;

    /**
     * Reading context
     */
    interface ReadingContext extends AutoCloseable {
        /**
         * @return positive value if entry is available, {@link #NULL_INDEX} otherwise
         */
        long index();

        /**
         * @return wrapped entry buffer if entry is available, otherwise unwrapped buffer
         */
        DirectBuffer buffer();

        /**
         * @return true if entry is available, false otherwise
         */
        boolean hasEntry();

        @Override
        void close();
    }

    /**
     * Returns the index of the last entry in the queue, or {@link #NULL_INDEX} if the queue is empty.  Note that
     * consecutive calls may return different results if entries are appended in the background.
     *
     * @return a non-negative queue index of the last entry in the queue, or {@link #NULL_INDEX} if the queue is empty
     */
    long lastIndex();

    /**
     * Returns true if a valid entry exists at the specified queue index, and false otherwise.
     * @param index zero-based queue index
     * @return true if an entry is available at the given index, and false otherwise
     */
    boolean hasEntry(long index);

    /**
     * Initialises and returns context for reading the entry at the specified {@code index} in the queue.
     * @param index zero-based queue index
     * @return reading context to read queue entry at the given index
     */
    ReadingContext read(long index);

    /**
     * Initialises and returns context for reading the last entry in the queue.
     * @return reading context to read last queue entry
     */
    ReadingContext readLast();

    /**
     * Initialises and returns context for reading the first entry in the queue.
     * @return reading context to read first queue entry
     */
    ReadingContext readFirst();

    /**
     * Closes this reader.
     */
    @Override
    void close();
}
