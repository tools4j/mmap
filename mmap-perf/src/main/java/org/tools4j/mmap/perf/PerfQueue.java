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
package org.tools4j.mmap.perf;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Queue adapter used by {@link Sender} and {@link Receiver}, so that the same harness measures different queue
 * implementations. Writers and readers are created by, and used from, the thread that uses them.
 */
public interface PerfQueue extends AutoCloseable {
    /**
     * Creates a writer for the calling thread.
     *
     * @return a writer for the calling thread
     */
    Writer createWriter();

    /**
     * Creates a reader for the calling thread.
     *
     * @return a reader for the calling thread, starting at the first message
     */
    Reader createReader();

    /**
     * Appends the settings of the queue.
     *
     * @param sb the string builder to append the queue settings to, one setting per line
     */
    void describeSettings(StringBuilder sb);

    @Override
    void close();

    /** Zero-copy writer: {@link #claim(int) claim}, encode, {@link #commit() commit}. */
    interface Writer extends AutoCloseable {
        /**
         * Claims a message of the given length.
         *
         * @param length the message length
         * @return a buffer to encode the message into, at offset zero and with the given length
         */
        MutableDirectBuffer claim(int length);

        /** Commits the message last claimed */
        void commit();

        @Override
        void close();
    }

    /** Zero-copy reader invoking a handler with the next message if one is available. */
    interface Reader extends AutoCloseable {
        /**
         * Polls the next message.
         *
         * @param handler the handler invoked with the next message if one is available
         * @return true if a message was handled
         */
        boolean poll(MessageHandler handler);

        @Override
        void close();
    }

    /** Handler for a message in a buffer. */
    @FunctionalInterface
    interface MessageHandler {
        /**
         * Handles a message.
         *
         * @param buffer the buffer containing the message
         * @param offset the message offset in the buffer
         * @param length the message length
         */
        void onMessage(DirectBuffer buffer, int offset, int length);
    }
}
