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

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.impl.QueueImpl;

import java.io.File;

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
    Appender createAppender(AppenderConfig config);

    /**
     * Creates a poller for sequential read access via callback starting with the first queue entry.
     *
     * @return new instance of a poller
     */
    Poller createPoller();
    Poller createPoller(ReaderConfig config);

    /**
     * Creates an entry reader for accessing queue {@link Entry entries} via index.
     *
     * @return new instance of an entry reader.
     */
    EntryReader createEntryReader();
    EntryReader createEntryReader(ReaderConfig config);

    /**
     * Creates an entry iterator for sequential access of queue {@link Entry entries}.
     *
     * @return new instance of an entry iterator.
     */
    EntryIterator createEntryIterator();
    EntryIterator createEntryIterator(ReaderConfig config);

    /**
     * Creates an index reader for querying queue entry indices.
     *
     * @return new instance of an index reader.
     */
    IndexReader createIndexReader();
    IndexReader createIndexReader(IndexReaderConfig config);

    boolean isClosed();

    /**
     * Closes the queue and all appender, pollers and readers.
     */
    @Override
    void close();

    static Queue create(final File file) {
        return new QueueImpl(file);
    }

    static Queue create(final File file, final QueueConfig config) {
        return new QueueImpl(file, config);
    }
}
