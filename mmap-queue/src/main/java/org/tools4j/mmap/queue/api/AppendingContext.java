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

import org.agrona.MutableDirectBuffer;

/**
 * Flyweight returned by {@link Appender#appending()} to encode a new entry directly to the queue {@link #buffer()}.
 */
public interface AppendingContext extends AutoCloseable {
    /**
     * @return buffer to write the entry data directly to the queue
     */
    MutableDirectBuffer buffer();

    /**
     * Aborts appending the entry
     */
    void abort();

    /**
     * Commits the entry that was encoded into the {@link #buffer()}
     *
     * @param length - length of the entry in bytes
     * @return queue index at which entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException    if the appender or the underlying queue is closed
     */
    long commit(int length);

    /**
     * @return true if the context is closed.
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
