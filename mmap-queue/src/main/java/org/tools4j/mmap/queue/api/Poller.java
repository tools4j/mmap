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
 * Queue poller
 */
public interface Poller extends AutoCloseable {
    /**
     * Result of message polling
     */
    enum Result {
        /**
         * Message was not available.
         */
        NOT_AVAILABLE,
        /**
         * Message was handled and message index was advanced.
         */
        ADVANCED,
        /**
         * Message was handled and message index was retained.
         */
        RETAINED,
        /**
         * Message was handled and message index was retreated.
         */
        RETREATED,
        /**
         * Error occurred when attempting to access message payload.
         */
        ERROR,
    }

    /**
     * Polls the queue and invokes the messageHandler if a message is available for consumption.
     *
     * @param messageHandler message handler
     * @return result value
     */
    Result poll(MessageHandler messageHandler);

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
     * Check if a message is available at the given index
     * @param index message index
     * @return true if message is available, false - otherwise
     */
    boolean hasEntry(long index);

    @Override
    void close();
}
