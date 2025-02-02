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
 * Representation of a queue entry used by {@link EntryReader} and {@link EntryIterator}, with data accessible through
 * {@link #buffer()}.
 */
public interface Entry {
    /**
     * @return entry index, non-negative for existing entries
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
