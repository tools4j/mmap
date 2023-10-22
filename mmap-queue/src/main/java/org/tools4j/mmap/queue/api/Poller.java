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
package org.tools4j.mmap.queue.api;

/**
 * Queue poller for sequential retrieval of entries with callback to an {@link EntryHandler}.
 */
public interface Poller extends AutoCloseable {
    /**
     * Result of polling an entry.
     */
    enum Result {
        /**
         * No entry was available for polling.
         */
        IDLE,
        /**
         * Entry was polled and index was moved to next higher index.
         * @see NextMove#FORWARD
         */
        POLLED_AND_MOVED_FORWARD,
        /**
         * Entry was polled and index was left unchanged.
         * @see NextMove#NONE
         */
        POLLED_AND_NOT_MOVED,
        /**
         * Entry was polled and index  was moved to next lower index.
         * @see NextMove#BACKWARD
         */
        POLLED_AND_MOVED_BACKWARD,
        /**
         * Error occurred when attempting to access entry payload.
         */
        ERROR
    }

    /**
     * Polls the queue and invokes the entry handler if one is available.
     *
     * @param entryHandler entry handler callback invoked if an entry is present
     * @return result value as per {@link NextMove} if polled, otherwise {@link Result#IDLE}
     */
    Result poll(EntryHandler entryHandler);

    /**
     * Move to given index for next entry to poll.
     *
     * @param index index to move to for next poll operation
     * @return true if the move succeeded, false otherwise
     */
    boolean moveToIndex(long index);

    /**
     * Move to end of the queue after the last entry
     * @return index at which next entry is expected to be appended in the future
     */
    long moveToEnd();

    /**
     * Move to start of the queue, for polling the first entry next
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
     * @return true if entry is available at the given index, and false otherwise
     */
    boolean hasEntry(long index);

    /**
     * Closes the poller.
     */
    @Override
    void close();
}
