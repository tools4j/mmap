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
 * Defines constants for index increments that can be used as return values in implementations of
 * {@link EntryHandler#onEntry(long, DirectBuffer, int, int)}.
 */
public interface Move {
    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to move to the next
     * entry.
     */
    long NEXT = 1;

    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to move to the previous
     * entry.
     */
    long PREVIOUS = -1;

    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to stay on the current
     * entry.
     */
    long NONE = 0;

    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to move to the first
     * queue entry.
     */
    long FIRST = Long.MIN_VALUE;

    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to move to the last
     * queue entry.
     */
    long LAST = Index.LAST;

    /**
     * Value returned by {@link EntryHandler#onEntry(long, DirectBuffer, int, int) onEntry(..)} to move to the end of
     * the queue after the last entry.
     */
    long END = Index.END;
}
