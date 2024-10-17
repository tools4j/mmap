/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
 * A queue of entries accessible in sequence or by index, where each entry is just a block of bytes.
 */
public interface Queue extends AutoCloseable {
    /**
     * Creates an appender.
     *
     * @return new instance of an appender
     */
    Appender createAppender();

    /**
     * Creates a poller for sequential read access via callback starting with the first queue entry.
     *
     * @return new instance of a poller
     */
    Poller createPoller();

    /**
     * Creates a reader for read access queue {@link Entry entries} via index or through iteration.
     *
     * @return new instance of a reader.
     */
    Reader createReader();

    boolean isClosed();

    /**
     * Closes the queue and all appender, pollers and readers.
     */
    @Override
    void close();
}
