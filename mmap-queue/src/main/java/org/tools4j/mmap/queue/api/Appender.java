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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Entry appender.
 */
public interface Appender extends AutoCloseable {
    /**
     * Flyweight provided by appender to directly write the new entry to the destination buffer without need to copy
     */
    interface AppendingContext extends AutoCloseable {
        /**
         * @return buffer to write the entry data
         */
        MutableDirectBuffer buffer();

        /**
         * Aborts appending the entry
         */
        void abort();

        /**
         * Commits the entry that was encoded via buffer
         *
         * @param length - length of the entry in bytes
         * @return queue index at which entry was appended, negative value if error occurred, see errors in {@link Appender}
         */
        long commit(int length);

        /**
         * @return true if the context is closed
         */
        boolean isClosed();

        /**
         * Aborts appending if not closed or committed yet.
         */
        @Override
        default void close() {
            if (!isClosed()) {
                abort();
            }
        }
    }

    /**
     * Failed to move to end of the queue
     */
    long MOVE_TO_END_ERROR = -1;
    /**
     * Failed to wrap payload buffer for given position while advancing to new region
     */
    long ADVANCE_TO_NEXT_PAYLOAD_REGION_ERROR = -2;
    /**
     * Failed to wrap header region for given position
     */
    long MAP_HEADER_REGION_ERROR = -3;
    /**
     * Failed to wrap payload region for given position
     */
    long MAP_PAYLOAD_REGION_ERROR = -4;
    long APPENDING_CONTEXT_CLOSED = -5;
    long APPENDING_CONTEXT_IN_USE = -6;

    /**
     * Appends an entry copied from the given buffer.
     * Note: for zero-copy use {@link #appending(int)}
     *
     * @param buffer - direct buffer to read entry from
     * @param offset - offset of the entry in the buffer
     * @param length - length of the entry
     * @return  queue index at which entry was appended, or negative value if error occurred as per error constants in
     *          {@link Appender}
     */
    long append(DirectBuffer buffer, int offset, int length);

    /**
     * Provides appending context for zero-copy encoding of the appended entry.
     * @param maxLength max byte length of the new entry to be reserved
     * @return appending context for writing of entry data
     */
    AppendingContext appending(int maxLength);

    /**
     * Closes the appender.
     */
    @Override
    void close();
}
