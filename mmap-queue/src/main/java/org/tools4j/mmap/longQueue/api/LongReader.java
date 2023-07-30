/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.longQueue.api;

/**
 * Random access reading interface.
 */
public interface LongReader extends AutoCloseable {
    long NULL_INDEX = -1;

    /**
     * Reading context
     */
    interface Entry {
        /**
         * @return positive index if entry is available, {@link #NULL_INDEX} otherwise
         */
        long index();

        /**
         * @return value
         */
        long value();

        boolean hasValue();
    }

    /**
     * Returns last index.
     * @return positive value if entry is available, {@link #NULL_INDEX} otherwise
     */
    long lastIndex();

    /**
     * @param index zero-based index
     * @return true if a value is available at the given index
     */
    boolean hasValue(long index);

    /**
     * @param index zero-based index
     * @return reading context
     */
    Entry read(long index);

    /**
     * @return reading context of last entry
     */
    Entry readLast();

    /**
     * @return reading context of first entry
     */
    Entry readFirst();

    @Override
    void close();
}
