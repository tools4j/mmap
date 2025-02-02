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
 * API for random read access of entries from a {@link Queue}.
 */
public interface EntryReader extends IndexReader {
    /**
     * Returns the reading context with access to entry data and index if the requested queue entry is available;
     * otherwise {@link ReadingContext#index() index} is negative and the {@link ReadingContext#buffer() buffer}
     * contains no data.
     * <p>
     * The returned context should be closed after using, and it is recommended to use a try-resource statement like
     * in the following example:
     * <pre>
     * long start = 123;
     * try (ReadingContext context = entryReader.reading(start)) {
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
     * try (ReadingContext context = entryReader.readingFirst()) {
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
     * try (ReadingContext context = entryReader.readingLast()) {
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
     * Closes this reader.
     */
    @Override
    void close();
}
