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

import org.tools4j.mmap.core.base.Closeable;

import java.util.Iterator;

/**
 * Flyweight returned by {@link EntryIterator} to iterate over queue entries.
 */
public interface IterableContext extends Iterable<Entry>, Closeable {
    /**
     * Returns the index of the first entry returned by iterators.
     *
     * @return the index of the first entry returned by iterators, or {@link Index#NULL} if unavailable.
     */
    long startIndex();

    /**
     * Returns an iterator for queue entries starting at {@link #startIndex()}, iterating as defined by the
     * {@linkplain #mode() iteration mode}. Every invocation returns an iterator starting again at
     * {@link #startIndex()}; since iterators are flyweights, the previously returned iterator must no longer be used.
     * <p>
     * In {@link IterationMode#FORWARD FORWARD} and {@link IterationMode#BACKWARD BACKWARD} mode the iterator ends at
     * the first index without an entry: once {@link Iterator#hasNext()} returns false, it keeps returning false, even
     * if entries are appended to the queue later. In {@link IterationMode#CONTINUOUS CONTINUOUS} mode, entries
     * appended later are returned even if {@code hasNext()} previously returned false.
     *
     * @return an iterator for queue entries
     * @throws IllegalStateException if this context is closed
     */
    @Override
    Iterator<Entry> iterator();

    /**
     * Sets the iteration mode to {@link IterationMode#FORWARD FORWARD}, the default: iterators return entries in
     * queue order and end once {@link Iterator#hasNext()} returns false.
     *
     * @return this iterable context for method chaining
     * @throws IllegalStateException if this context is closed
     */
    IterableContext forward();

    /**
     * Sets the iteration mode to {@link IterationMode#BACKWARD BACKWARD}: iterators return entries in reverse order
     * finishing with the first queue entry.
     *
     * @return this iterable context for method chaining
     * @throws IllegalStateException if this context is closed
     */
    IterableContext backward();

    /**
     * Sets the iteration mode to {@link IterationMode#CONTINUOUS CONTINUOUS}: iterators return entries in queue order
     * like a continuous query, including entries appended after {@link Iterator#hasNext()} returned false, unlike
     * iterators of Java collections.
     *
     * @return this iterable context for method chaining
     * @throws IllegalStateException if this context is closed
     */
    IterableContext continuous();

    /**
     * Returns the iteration mode, applying to iterators of this context from their next {@link Iterator#hasNext()} or
     * {@link Iterator#next()} invocation; an iterator that ended in {@code FORWARD} or {@code BACKWARD} mode remains
     * ended.
     *
     * @return the iteration mode
     */
    IterationMode mode();

    /**
     * Closes this iterable context: iterators obtained from it return no further entries, and the current entry's
     * buffer is unwrapped. The context must be closed before the next iterable context is requested from the same
     * {@link EntryIterator}, otherwise an {@link IllegalStateException} is thrown at that point.
     */
    @Override
    void close();
}
