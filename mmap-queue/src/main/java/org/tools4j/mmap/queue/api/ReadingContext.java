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

import org.tools4j.mmap.region.api.Closeable;

/**
 * Flyweight return by {@link EntryReader} with access to entry data and index.
 */
public interface ReadingContext extends Entry, Closeable {
    /**
     * Returns the index of the available entry.
     *
     * @return non-negative index if entry is available, and {@link Index#NULL} otherwise
     */
    @Override
    long index();

    /**
     * Returns whether an entry is available.
     *
     * @return true if entry is available, and false otherwise
     */
    boolean hasEntry();

    /**
     * Closes this reading context: the {@link #buffer() buffer} is unwrapped and the {@link #index() index} reset to
     * {@link Index#NULL}. The context must be closed before the next reading context is requested from the same
     * {@link EntryReader}, otherwise an {@link IllegalStateException} is thrown at that point.
     */
    @Override
    void close();
}
