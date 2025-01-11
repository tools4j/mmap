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

/**
 * API for sequential read access of entries from a {@link Queue}.
 */
public interface EntryIterator extends AutoCloseable {
    /**
     * Returns the reading context to iterate over the entries of the queue starting from the specified entry index.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * long start = 123;
     * try (IterableContext iterable = queue.readingFrom(start)) {
     *     for (Entry entry : iterable) {
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
     * Note that the returned iterable can still be used even if the queue was empty when calling this method if entries
     * are subsequently appended to the queue in the background.
     * <p>
     * Note that this is not an instant operation and the method should not be called from a latency sensitive context.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromEnd();

    boolean isClosed();

    /**
     * Closes this reader.
     */
    @Override
    void close();
}
