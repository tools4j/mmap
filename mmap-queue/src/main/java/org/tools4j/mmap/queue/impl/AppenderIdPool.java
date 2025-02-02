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
package org.tools4j.mmap.queue.impl;

/**
 * A pool of appender IDs.
 */
interface AppenderIdPool extends AutoCloseable {
    /**
     * Acquire appender ID
     * @return newly appender ID (reused if single appender pool)
     */
    int acquire();

    /**
     * Release appender ID (no-op if single appender pool)
     * @param appenderId appender ID to release
     * @return true if the appender ID was released
     */
    boolean release(int appenderId);

    /**
     * Returns the number of appenders currently acquired, or zero if this pool is closed.
     * @return the open appenders, or zero if the pool is closed
     */
    int openAppenders();

    /**
     * Closes the appender ID pool (no-op if single appender pool)
     */
    @Override
    void close();
}
