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
 * Defines index constants used by Queue {@link Poller} and {@link Appender}
 */
public interface Index {
    /** Null index returned for non-existent entry. */
    long NULL = -1;

    /** Zero index used for first entry in the queue. */
    long FIRST = 0;

    /** The maximum allowed index, which is 1,152,921,504,606,846,975. */
    long MAX = Long.MAX_VALUE / Long.BYTES;//otherwise we cannot express the header position

    /**
     * Pseudo-index used to generically reference the last entry in the queue.
     * <p>
     * This value is returned by {#link {@link Poller#nextIndex()}} when moving to the last entry in the queue before it
     * is reached.  Passing {@code LAST} to {@link Poller#seekNext(long)} is equivalent to calling
     * {@link Poller#seekLast()}
     */
    long LAST = Long.MAX_VALUE - 1;

    /**
     * Pseudo-index used to reference the end of the queue <i>after</i> the last entry.
     * <p>
     * This value is returned by {#link {@link Poller#nextIndex()}} when moving to the end of the queue before reaching
     * it.  Passing {@code END} to {@link Poller#seekNext(long)} is equivalent to calling
     * {@link Poller#seekEnd()}}
     */
    long END = Long.MAX_VALUE;
}
