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
import org.tools4j.mmap.mapping.api.Closeable;
import org.tools4j.mmap.mapping.api.OpenMode;

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
     * Opens the queue stored in the given directory, or creates it if it does not exist, using the
     * {@link QueueConfig#getDefault() default} queue configuration.
     *
     * @param directory the queue directory
     * @return a new queue instance
     * @throws IllegalArgumentException if the queue has to be created but the parent directory does not exist
     * @see #open(File, OpenMode, QueueConfig)
     */
    static Queue open(final File directory) {
        return open(directory, OpenMode.OPEN_OR_CREATE);
    }

    /**
     * Opens or creates the queue stored in the given directory as defined by the open mode, using the
     * {@link QueueConfig#getDefault() default} queue configuration.
     *
     * @param directory the queue directory
     * @param openMode  the mode to open the queue with
     * @return a new queue instance
     * @throws IllegalArgumentException if the queue cannot be opened as defined by the open mode
     * @see #open(File, OpenMode, QueueConfig)
     */
    static Queue open(final File directory, final OpenMode openMode) {
        return open(directory, openMode, QueueConfig.getDefault());
    }

    /**
     * Opens or creates the queue stored in the given directory as defined by the open mode, using the given queue
     * configuration:
     * <ul>
     *     <li>{@link OpenMode#READ_ONLY READ_ONLY}, {@link OpenMode#READ_WRITE READ_WRITE}: the queue must exist</li>
     *     <li>{@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}: the queue may not exist yet; readers see entries once
     *         the queue is created and they are appended</li>
     *     <li>{@link OpenMode#CREATE CREATE}: the queue must not exist, and is created</li>
     *     <li>{@link OpenMode#OPEN_OR_CREATE OPEN_OR_CREATE}: the queue is opened if it exists, and created
     *         otherwise</li>
     *     <li>{@link OpenMode#RECREATE RECREATE}: the queue is deleted if it exists, and created again</li>
     * </ul>
     * A queue exists once it has been created, even if it is still empty. Creating a queue creates its directory if
     * necessary, which requires the parent directory to exist. The queue files are stored inside the directory and are
     * named after it, e.g. {@code <dir>/<dir>_hdr.mmq}.
     *
     * @param directory the queue directory
     * @param openMode  the mode to open the queue with
     * @param config    the queue configuration
     * @return a new queue instance
     * @throws IllegalArgumentException if the queue cannot be opened as defined by the open mode, for instance if it
     *                                  must exist but does not, or if the parent directory does not exist
     */
    static Queue open(final File directory, final OpenMode openMode, final QueueConfig config) {
        return new QueueImpl(directory, openMode, config);
    }
}
