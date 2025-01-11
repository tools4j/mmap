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
 * Handler when {@link Poller#poll(EntryHandler) polling} entries via a {@link Poller}
 */
public interface EntryHandler {
    /**
     * Handles an entry and returns the direction how to move the cursor for the next entry to poll.
     *
     * @param index entry index in the queue
     * @param buffer buffer with access to entry data, with valid byte range {@code [offset...(offset + length - 1)]}
     * @param offset offset in the buffer with the first entry byte (unless length is zero)
     * @param length number of bytes in the buffer for this entry
     * @return  index increment to move the cursor to the next entry, typically +1 to move one entry forward, -1 to move
     *          one entry backwards, zero to stay on the current entry, or also {@link Move#FIRST}, {@link Move#LAST}
     *          and {@link Move#END} to move to the first entry, last entry or to the end of the queue, respectively
     */
    long onEntry(long index, DirectBuffer buffer, int offset, int length);
}
