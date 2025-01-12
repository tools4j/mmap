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

/**
 * Interface for random read access to entries in the {@link Queue}.
 */
public interface Reader extends AutoCloseable {
    /**
     * Index returned by {@link ReadingContext#index()} if no entry is currently available for reading.
     */
    long NULL_INDEX = -1;

    /**
     * Entry in the queue, with data accessible through {@link #buffer()}.
     */
    interface Entry {
        /**
         * @return non-negative entry index
         */
        long index();

        /**
         * The buffer with entry data; valid bytes are in the range {@code [0..(n-1)]} where {@code n} is equal to the
         * buffer's {@link DirectBuffer#capacity() capacity}.
         *
         * @return buffer with entry data, with zero capacity if entry has no data or no entry is available
         */
        DirectBuffer buffer();
    }

    /**
     * Reading context with access to entry data and index if the requested queue entry was available; otherwise the
     * returned {@link #buffer()} contains no data.
     */
    interface ReadingContext extends Entry, AutoCloseable {
        /**
         * @return non-negative index if entry is available, {@link #NULL_INDEX} otherwise
         */
        @Override
        long index();

        /**
         * @return true if entry is available, false otherwise
         */
        boolean hasEntry();

        /** Closes the reading context and unwraps the buffer */
        @Override
        void close();
    }

    /**
     * Iterable context to iterate over queue entries.
     */
    interface IterableContext extends AutoCloseable {
        /** @return the index of the first entry returned by iterators, or {@link #NULL_INDEX} if unavailable */
        long startIndex();

        /**
         * Returns an iterable for iteration over queue entries in the given {@code direction} starting at
         * {@link #startIndex() startIndex}. If direction is {@link Direction#NONE NONE}, only the start entry will be returned by
         * the iterable.
         *
         * @param direction the iteration direction
         * @return an iterable to iterate over queue entries in the given {@code direction}
         */
        Iterable<? extends Entry> iterate(Direction direction);

        /** Closes any iterator associated with this iterable and unwraps the current entry's buffer */
        @Override
        void close();
    }

    /**
     * Returns the index of the first entry in the queue, or {@link #NULL_INDEX} if the queue is empty.  Note that
     * if the queue is non-empty, the returns value will always be zero.
     *
     * @return zero if the queue is non-empty, and {@link #NULL_INDEX} otherwise
     */
    long firstIndex();

    /**
     * Returns the index of the last entry in the queue, or {@link #NULL_INDEX} if the queue is empty.  Note that
     * consecutive calls may return different results if entries are appended in the background.
     *
     * @return a non-negative entry index of the last entry in the queue, or {@link #NULL_INDEX} if the queue is empty
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
     * int the following example:
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
     * int the following example:
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
     * int the following example:
     * <pre>
     * try (ReadingContext context = queue.readingLast()) {
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
    ReadingContext readingLast();

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the specified entry index.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * int the following example:
     * <pre>
     * long start = 123;
     * try (IterableContext context = queue.readingFrom(start)) {
     *     for (Entry entry : context.iterate(Direction.FORWARD) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a no-op iterable if the queue is empty or index is negative or not a valid entry index.
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
     * int the following example:
     * <pre>
     * try (IterableContext context = queue.readingFromFirst()) {
     *     for (Entry entry : context.iterate(Direction.FORWARD)) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a no-op iterable if the queue is empty.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromFirst();

    /**
     * Returns the reading context to iterate over the entries of the queue starting from the last entry.
     * <p>
     * The returned iterable should be closed after using, and it is recommended to use a try-resource statement like
     * int the following example:
     * <pre>
     * try (IterableContext context = queue.readingFromLast()) {
     *     for (Entry entry : context.iterate(Direction.BACKWARD)) {
     *         byte byte0 = entry.buffer().get(0);
     *         ...
     *     }
     * }
     * </pre>
     * Returns a no-op iterable if the queue is empty.
     *
     * @return an entry iterable that can be used in a for-loop
     */
    IterableContext readingFromLast();

    /**
     * Closes this reader.
     */
    @Override
    void close();
}
