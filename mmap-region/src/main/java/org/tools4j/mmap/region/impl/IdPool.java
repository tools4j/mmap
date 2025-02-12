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
package org.tools4j.mmap.region.impl;

/**
 * A pool of IDs that can be acquired and released atomically.
 */
public interface IdPool extends AutoCloseable {
    /**
     * Acquire a new ID
     * @return the acquired ID
     * @throws IllegalStateException if no IDs are left in the pool or if the pool is closed
     */
    int acquire();

    /**
     * Release the given ID
     * @param id the ID to release
     * @return true if the ID was released
     * @throws IllegalArgumentException if the ID is invalid for the range supported by this pool
     * @throws IllegalStateException if the pool is closed
     */
    boolean release(int id);

    /**
     * Returns the number of IDs currently acquired, or zero if this pool is closed.
     * @return the IDs currently acquired, or zero if the pool is closed
     */
    int acquired();

    /**
     * Returns true if this ID pool is closed.
     * @return true if the pool is closed.
     */
    boolean isClosed();

    /**
     * Closes the ID pool (no-op if already closed)
     */
    @Override
    void close();
}
