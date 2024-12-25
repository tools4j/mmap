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
package org.tools4j.mmap.queue.impl;

import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.config.MappingStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates a separate instance of {@link Poller}.
 */
public final class QueueImpl implements Queue {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueImpl.class);

    private final QueueFiles files;
    private final QueueConfig config;
    private final Function<ReaderConfig, Poller> pollerFactory;
    private final Function<ReaderConfig, EntryReader> entryReaderFactory;
    private final Function<ReaderConfig, EntryIterator> entryIteratorFactory;
    private final Function<IndexReaderConfig, IndexReader> indexReaderFactory;
    private final Function<AppenderConfig, Appender> appenderFactory;
    private final List<AutoCloseable> closeables = new ArrayList<>();

    public QueueImpl(final File file) {
        this(file, QueueConfig.getDefault());
    }

    public QueueImpl(final File file, final QueueConfig queueConfig) {
        final AccessMode accessMode = queueConfig.accessMode();
        final int maxAppenders = queueConfig.maxAppenders();
        this.files = new QueueFiles(file, maxAppenders);
        this.config = queueConfig.toImmutableQueueConfig();
        if (accessMode == AccessMode.READ_WRITE_CLEAR) {
            deleteQueueFiles();
        }
        if (!file.exists() && accessMode != AccessMode.READ_ONLY) {
            createQueueDir(file);
        }

        final AppenderIdPool idPool = open(appenderIdPool(files, maxAppenders));
        this.pollerFactory = pollerConfig -> open(new PollerImpl(
                files.queueName(),
                ReaderMappings.create(files, config, pollerConfig)
        ));
        this.entryReaderFactory = readerConfig -> open(new EntryReaderImpl(
                files.queueName(),
                ReaderMappings.create(files, config, readerConfig)
        ));
        this.entryIteratorFactory = readerConfig -> open(new EntryIteratorImpl(
                files.queueName(),
                ReaderMappings.create(files, config, readerConfig)
        ));
        this.indexReaderFactory = indReaderConfig -> open(new IndexReaderImpl(
                files.queueName(), IndexMappings.create(files, config, indReaderConfig)
        ));
        this.appenderFactory = accessMode == AccessMode.READ_ONLY ?
                appenderConfig -> {throw new IllegalStateException(
                        "Cannot open appender in read-only mode for queue " + files.queueName());
                } :
                appenderConfig -> open(new AppenderImpl(
                        files.queueName(),
                        AppenderMappings.create(files, idPool, config, appenderConfig),
                        enableCopyFromPreviousRegion(appenderConfig)
                ));
    }

    private void deleteQueueFiles() {
        for (final File file : files.listFiles()) {
            final boolean deleted = file.delete();
            assert deleted : files.queueName();
        }
    }

    private void createQueueDir(final File file) {
        if (!file.mkdir()) {
            throw new IllegalArgumentException("Parent directory does not exist: " + file);
        }
    }

    private static boolean enableCopyFromPreviousRegion(final AppenderConfig appenderConfig) {
        final MappingStrategy mappingStrategy = appenderConfig.payloadMappingStrategy();
        final int cacheSie = mappingStrategy.cacheSize();
        final int mapAhead = mappingStrategy.asyncOptions().isPresent() ?
                mappingStrategy.asyncOptions().get().regionsToMapAhead() : 0;
        return cacheSie > Math.max(1, mapAhead + 1);
    }

    private static AppenderIdPool appenderIdPool(final QueueFiles queueFiles, final int maxAppenders) {
        switch (maxAppenders) {
            case 1:
                return ConstantAppenderId.ALWAYS_ZERO;
            case AppenderIdPool64.MAX_APPENDERS:
                return new AppenderIdPool64(queueFiles.appenderPoolFile());
            case AppenderIdPool256.MAX_APPENDERS:
                return new AppenderIdPool256(queueFiles.appenderPoolFile());
            default:
                throw new IllegalArgumentException("Invalid value for max appenders: " + maxAppenders);
        }
    }

    private synchronized <T extends AutoCloseable> T open(final T closeable) {
        closeables.add(closeable);
        return closeable;
    }

    @Override
    public Appender createAppender() {
        return createAppender(config.appenderConfig());
    }

    @Override
    public Appender createAppender(final AppenderConfig config) {
        return appenderFactory.apply(config);
    }

    @Override
    public Poller createPoller() {
        return createPoller(config.pollerConfig());
    }

    @Override
    public Poller createPoller(final ReaderConfig config) {
        return pollerFactory.apply(config);
    }

    @Override
    public IndexReader createIndexReader() {
        return createIndexReader(config.indexReaderConfig());
    }

    @Override
    public EntryReader createEntryReader() {
        return createEntryReader(config.entryReaderConfig());
    }

    @Override
    public EntryReader createEntryReader(final ReaderConfig config) {
        return entryReaderFactory.apply(config);
    }

    @Override
    public EntryIterator createEntryIterator() {
        return createEntryIterator(config.entryIteratorConfig());
    }

    @Override
    public EntryIterator createEntryIterator(final ReaderConfig config) {
        return entryIteratorFactory.apply(config);
    }

    @Override
    public IndexReader createIndexReader(final IndexReaderConfig config) {
        return indexReaderFactory.apply(config);
    }

    @Override
    public boolean isClosed() {
        return closeables.isEmpty();
    }

    @Override
    public synchronized void close() {
        if (!isClosed()) {
            CloseHelper.quietCloseAll(closeables);
            closeables.clear();
            LOGGER.info("Closed queue: {}", files.queueName());
        }
    }

    @Override
    public String toString() {
        return "QueueImpl" +
                ":queue=" + files.queueName() +
                "|closed=" + isClosed();
    }
}
