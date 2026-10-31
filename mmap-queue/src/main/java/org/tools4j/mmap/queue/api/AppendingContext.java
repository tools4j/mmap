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
package org.tools4j.mmap.queue.api;

import org.agrona.MutableDirectBuffer;
import org.tools4j.mmap.mapping.api.Closeable;

/**
 * Flyweight returned by {@link Appender#appending(int)} to encode a new entry directly to the queue {@link #buffer()}.
 */
public interface AppendingContext extends Closeable {
    /**
     * Returns the buffer to write entry data directly to the queue. Returns a zero capacity buffer if context or
     * appender is closed.
     *
     * @return buffer to write the entry data directly to the queue
     */
    MutableDirectBuffer buffer();

    /**
     * Ensures that the {@link #buffer()} has at least the specified capacity in bytes, up to the entry size limit.
     * Data already encoded into the buffer is preserved, but the buffer may be re-wrapped at a new address.
     * <p>
     * This is a fast operation if the current payload region has enough space left. Otherwise, the encoded data is
     * copied to the start of the next payload region:
     * <ul>
     *     <li>directly from the previous region, if the appender's payload mapping strategy keeps it mapped, which
     *         requires deferred unmapping and a region cache larger than one plus the regions mapped ahead (as with
     *         the default queue configuration)</li>
     *     <li>via an intermediate copy buffer otherwise, which is allocated off-heap on first use (with a
     *         power-of-two size of at least 4KB, up to the payload region size) and reused by the appender</li>
     * </ul>
     * It is therefore recommended to specify sufficient capacity when the appending context is
     * {@linkplain Appender#appending(int) initialised} instead.
     *
     * @param capacity the capacity in bytes that determines the maximum length for the appended entry
     * @throws IllegalArgumentException if the specified capacity parameter exceeds the entry size limit
     * @throws IllegalStateException    if the context, the appender or the underlying queue is closed
     */
    void ensureCapacity(int capacity);

    /**
     * Aborts appending the entry
     */
    void abort();

    /**
     * Commits the entry that was encoded into the {@link #buffer()}
     *
     * @param length length of the entry in bytes
     * @return queue index at which the entry was appended
     * @throws IllegalArgumentException if length exceeds the maximum length for an entry allowed by the queue
     * @throws IllegalStateException    if the context, the appender or the underlying queue is closed
     */
    long commit(int length);

    /**
     * Closes this appending context, {@linkplain #abort() aborting} the entry unless it has already been committed or
     * aborted. The context must be closed before the next appending context is requested from the same
     * {@link Appender}, otherwise an {@link IllegalStateException} is thrown at that point.
     */
    @Override
    default void close() {
        if (!isClosed()) {
            abort();
        }
    }
}
