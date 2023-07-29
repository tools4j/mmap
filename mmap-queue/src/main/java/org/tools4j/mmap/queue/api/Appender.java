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
import org.agrona.MutableDirectBuffer;

/**
 * Message appender.
 */
public interface Appender extends AutoCloseable {
    /**
     * Flyweight provided by appender to append message
     * to the final destination avoiding copy.
     */
    interface AppendingContext extends AutoCloseable {
        /**
         * @return buffer to append the message
         */
        MutableDirectBuffer buffer();

        /**
         * Aborts appending message
         */
        void abort();

        /**
         * Commits message in encoded in the buffer.
         *
         * @param length - length of the message
         * @return index at which message was appended, negative value if error occurred, see errors in {@link Appender}
         */
        long commit(int length);

        /**
         * @return true if the context is closed
         */
        boolean isClosed();

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
    long WRAP_HEADER_REGION_ERROR = -3;
    /**
     * Failed to wrap payload region for given position
     */
    long WRAP_PAYLOAD_REGION_ERROR = -4;
    long APPENDING_CONTEXT_CLOSED = -5;
    long APPENDING_CONTEXT_IN_USE = -6;

    /**
     * Appends a message from given buffer.
     * Note: for zero-copy use {@link #appending(int)}
     *
     * @param buffer - direct buffer to read message from
     * @param offset - offset of the message in the buffer
     * @param length - length of the message
     * @return index at which message was appended, negative value if error occurred, see errors in {@link Appender}
     */
    long append(DirectBuffer buffer, int offset, int length);

    /**
     * Provides appending context for zero-copy encoding.
     * @param maxLength max length to be reserved
     * @return appending context
     */
    AppendingContext appending(int maxLength);

    /**
     * Override to avoid throwing checked exception.
     */
    @Override
    void close();
}
