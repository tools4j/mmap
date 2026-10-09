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

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.impl.QueueImpl;
import org.tools4j.mmap.region.api.Closeable;

import java.io.File;

/**
 * A queue of entries accessible in sequence or by index, where each entry is just a block of bytes.
 */
public interface Queue extends Closeable {
    /**
     * Creates an appender using the queue's default {@link QueueConfig#appenderConfig() appender configuration}.
     *
     * @return new instance of an appender
     */
    Appender createAppender();

    /**
     * Creates an appender using the given configuration.
     *
     * @param config the appender configuration
     * @return new instance of an appender
     */
    Appender createAppender(AppenderConfig config);

    /**
     * Creates a poller for sequential read access via callback starting with the first queue entry, using the queue's
     * default {@link QueueConfig#pollerConfig() poller configuration}.
     *
     * @return new instance of a poller
     */
    Poller createPoller();

    /**
     * Creates a poller for sequential read access via callback starting with the first queue entry, using the given
     * configuration.
     *
     * @param config the poller configuration
     * @return new instance of a poller
     */
    Poller createPoller(ReaderConfig config);

    /**
     * Creates an entry reader for accessing queue {@link Entry entries} via index, using the queue's default
     * {@link QueueConfig#entryReaderConfig() entry reader configuration}.
     *
     * @return new instance of an entry reader
     */
    EntryReader createEntryReader();

    /**
     * Creates an entry reader for accessing queue {@link Entry entries} via index, using the given configuration.
     *
     * @param config the entry reader configuration
     * @return new instance of an entry reader
     */
    EntryReader createEntryReader(ReaderConfig config);

    /**
     * Creates an entry iterator for sequential access of queue {@link Entry entries}, using the queue's default
     * {@link QueueConfig#entryIteratorConfig() entry iterator configuration}.
     *
     * @return new instance of an entry iterator
     */
    EntryIterator createEntryIterator();

    /**
     * Creates an entry iterator for sequential access of queue {@link Entry entries}, using the given configuration.
     *
     * @param config the entry iterator configuration
     * @return new instance of an entry iterator
     */
    EntryIterator createEntryIterator(ReaderConfig config);

    /**
     * Creates an index reader for querying queue entry indices, using the queue's default
     * {@link QueueConfig#indexReaderConfig() index reader configuration}.
     *
     * @return new instance of an index reader
     */
    IndexReader createIndexReader();

    /**
     * Creates an index reader for querying queue entry indices, using the given configuration.
     *
     * @param config the index reader configuration
     * @return new instance of an index reader
     */
    IndexReader createIndexReader(IndexReaderConfig config);

    /**
     * Closes this queue and all appenders, pollers, entry readers, entry iterators and index readers created through
     * it. Exceptions thrown while closing those are ignored.
     */
    @Override
    void close();

    /**
     * Creates or opens the queue stored in the given directory, using the {@link QueueConfig#getDefault() default}
     * queue configuration.
     *
     * @param directory the queue directory
     * @return a new queue instance
     * @throws IllegalArgumentException if the directory has to be created but its parent directory does not exist
     * @see #create(File, QueueConfig)
     */
    static Queue create(final File directory) {
        return new QueueImpl(directory);
    }

    /**
     * Creates or opens the queue stored in the given directory, using the given queue configuration.
     * <p>
     * The queue files are created inside the directory and are named after it, e.g. {@code <dir>/<dir>_hdr.mmq}.
     * Unless the configured {@link QueueConfig#accessMode() access mode} is
     * {@link org.tools4j.mmap.region.api.AccessMode#READ_ONLY READ_ONLY}, the directory is created if it does not
     * exist; its parent directory must exist. With
     * {@link org.tools4j.mmap.region.api.AccessMode#READ_WRITE_CLEAR READ_WRITE_CLEAR}, existing queue files are
     * deleted first.
     *
     * @param directory the queue directory
     * @param config    the queue configuration
     * @return a new queue instance
     * @throws IllegalArgumentException if the directory has to be created but its parent directory does not exist
     */
    static Queue create(final File directory, final QueueConfig config) {
        return new QueueImpl(directory, config);
    }
}
