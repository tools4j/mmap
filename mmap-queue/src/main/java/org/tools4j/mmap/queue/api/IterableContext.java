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

import java.util.Iterator;

/**
 * Flyweight return by {@link Reader} to iterate over queue entries.
 */
public interface IterableContext extends Iterable<Entry>, AutoCloseable {
    /**
     * @return the index of the first entry returned by iterators, or {@link Index#NULL} if unavailable.
     */
    long startIndex();

    /**
     * Returns an iterator for queue entries starting at {@link #startIndex()}.
     * <p>
     * Note that the iterator continues to function if queue entries are appended in the background, and new entries are
     * returned even if {@link Iterator#hasNext()} has previously returned false at some point.
     *
     * @return an iterator for queue entries
     */
    @Override
    Iterator<Entry> iterator();

    /**
     * Returns an iterable for queue entries starting at {@link #startIndex()} returning entries in reverse order
     * finishing with the first queue entry.
     *
     * @return an iterator for queue entries in reverse order
     */
    IterableContext reverse();

    /**
     * Closes any iterator associated with this iterable and unwraps the current entry's buffer
     */
    @Override
    void close();
}
