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
 * API for random read access of entries in a {@link Queue}.
 */
public interface Reader extends AutoCloseable {

    /**
     * Returns the index of the first entry in the queue, or {@link Index#NULL} if the queue is empty.  Note that
     * if the queue is non-empty, the returns value will always be zero.
     *
     * @return zero if the queue is non-empty, and {@link Index#NULL} otherwise
     */
    long firstIndex();

    /**
     * Returns the index of the last entry in the queue, or {@link Index#NULL} if the queue is empty.
     * <p>
     * Note that consecutive calls may return different results if entries are appended in the background.  Note also
     * that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return a non-negative entry index of the last entry in the queue, or {@link Index#NULL} if the queue is empty
     */
    long lastIndex();

    /**
     * Returns true if a valid entry exists at the specified index, and false otherwise.
     *
     * @param index zero-based entry index
     * @return true if an entry is available at the given index, and false otherwise
     */
    boolean hasEntry(long index);

    /**
     * Returns the reading context with access to entry data and index if the requested queue entry is available;
     * otherwise {@link ReadingContext#index() index} is negative and the {@link ReadingContext#buffer() buffer}
     * contains no data.
     * <p>
     * The returned context should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * long start = 123;
     * try (ReadingContext context = queue.reading(start)) {
     *     if (context.hasEntry()) {
     *         byte byte0 = context.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a context with unavailable entry if the queue is empty or index is negative or not a valid entry index.
     *
     * @param index zero-based index of entry to read
     * @return reading context to access entry data if available
     */
    ReadingContext reading(long index);

    /**
     * Returns the reading context with access to entry data and index if the first queue entry is available;
     * otherwise {@link ReadingContext#index() index} is negative and the {@link ReadingContext#buffer() buffer}
     * contains no data.
     * <p>
     * The returned context should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * try (ReadingContext context = queue.readingFirst()) {
     *     if (context.hasEntry()) {
     *         byte byte0 = context.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a context with unavailable entry if the queue is empty.
     *
     * @return reading context to access entry data if available
     */
    ReadingContext readingFirst();

    /**
     * Returns the reading context with access to entry data and index if the last queue entry is available;
     * otherwise {@link ReadingContext#index() index} is negative and the {@link ReadingContext#buffer() buffer}
     * contains no data.
     * <p>
     * The returned context should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * try (ReadingContext context = queue.readingLast()) {
     *     if (context.hasEntry()) {
     *         byte byte0 = context.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a context with unavailable entry if the queue is empty.
     * <p>
     * Note that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return reading context to access entry data if available
     */
    ReadingContext readingLast();

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the specified entry index.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * long start = 123;
     * try (IterableContext context = queue.readingFrom(start)) {
     *     for (Entry entry : context.iterate(Direction.FORWARD) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Note that the returned iterable can still be used even if the queue was empty when calling this method if ent
     * entries are subsequently appended to the queue in the background.
     *
     * @param index     zero-based entry index from which to start
     * @return an entry iterable that can be used in a for-loop
     * @throws IllegalArgumentException Direction is NONE
     */
    IterableContext readingFrom(long index);

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the first entry.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * try (IterableContext iterable = queue.readingFromFirst()) {
     *     for (Entry entry : iterable) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Note that the returned iterable can still be used even if the queue was empty when calling this method if ent
     * entries are subsequently appended to the queue in the background.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromFirst();

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the last entry.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * try (IterableContext iterable = queue.readingFromLast().reverse()) {
     *     for (Entry entry : iterable) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Note that the returned iterable can still be used even if the queue was empty when calling this method if ent
     * entries are subsequently appended to the queue in the background.
     * <p>
     * Note that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromLast();

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the end of the queue
     * <i>after</i> the last entry.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * try (IterableContext iterable = queue.readingFromEnd()) {
     *     for (Entry entry : iterable) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Note that the returned iterable can still be used even if the queue was empty when calling this method if ent
     * entries are subsequently appended to the queue in the background.
     * <p>
     * Note that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromEnd();

    /**
     * Closes this reader.
     */
    @Override
    void close();
}
