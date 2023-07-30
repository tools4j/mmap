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
 * Long queue poller
 */
public interface LongPoller extends AutoCloseable {
    /**
     * Result of entry polling
     */
    enum Result {
        /**
         * Entry was not available.
         */
        NOT_AVAILABLE,
        /**
         * Entry was handled and the index was advanced.
         */
        ADVANCED,
        /**
         * Entry was handled and the index was retained.
         */
        RETAINED,
        /**
         * Entry was handled and the index was retreated.
         */
        RETREATED,
    }

    /**
     * Polls the queue and invokes the entryHandler if the entry is available for consumption.
     *
     * @param entryHandler message handler
     * @return result value
     */
    Result poll(EntryHandler entryHandler);

    /**
     * Move current cursor to given index.
     *
     * @param index to move the cursor to
     * @return true if the move succeeded, false otherwise
     */
    boolean moveToIndex(long index);

    /**
     * Move to end of the queue
     * @return last index at which new message is expected
     */
    long moveToEnd();

    /**
     * Move to start
     * @return true is succeeded
     */
    boolean moveToStart();

    /**
     * @return current index
     */
    long currentIndex();

    /**
     * Check if an entry is available at the given index
     * @param index entry index
     * @return true if entry is available, false - otherwise
     */
    boolean hasEntry(long index);

    @Override
    void close();
}
