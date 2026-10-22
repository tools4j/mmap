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
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.impl.IdPool;
import org.tools4j.mmap.mapping.impl.IdPool256;
import org.tools4j.mmap.mapping.impl.IdPool64;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates their own instances of
 * {@link #createAppender() appender}, {@link #createPoller() poller} etc.
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
    private final AtomicBoolean closed = new AtomicBoolean();
    private final java.util.Queue<AutoCloseable> closeables = new ConcurrentLinkedQueue<>();

    public QueueImpl(final File file, final OpenMode openMode, final QueueConfig queueConfig) {
        final int maxAppenders = queueConfig.maxAppenders();
        this.files = new QueueFiles(file, maxAppenders);
        this.config = queueConfig.toImmutableQueueConfig();
        openQueueDir(file, openMode);

        final IdPool idPool = openMode.accessMode() == AccessMode.READ_ONLY ? null : open(idPool(files, maxAppenders));
        this.pollerFactory = pollerConfig -> open(new PollerImpl(
                queueNameIfNotClosed(),
                ReaderMappings.create(files, config, pollerConfig)
        ));
        this.entryReaderFactory = readerConfig -> open(new EntryReaderImpl(
                queueNameIfNotClosed(),
                ReaderMappings.create(files, config, readerConfig)
        ));
        this.entryIteratorFactory = readerConfig -> open(new EntryIteratorImpl(
                queueNameIfNotClosed(),
                ReaderMappings.create(files, config, readerConfig)
        ));
        this.indexReaderFactory = indReaderConfig -> open(new IndexReaderImpl(
                queueNameIfNotClosed(), IndexMappings.create(files, config, indReaderConfig)
        ));
        this.appenderFactory = idPool == null ?
                appenderConfig -> {throw new IllegalStateException(
                        "Cannot open appender in read-only mode for queue " + queueNameIfNotClosed());
                } :
                appenderConfig -> open(new AppenderImpl(
                        queueNameIfNotClosed(),
                        AppenderMappings.create(files, idPool, config, appenderConfig),
                        enableCopyFromPreviousRegion(appenderConfig)
                ));
    }

    private void openQueueDir(final File file, final OpenMode openMode) {
        final boolean exists = file.isDirectory();
        switch (openMode) {
            case READ_ONLY, READ_WRITE -> {
                if (!exists) {
                    throw new IllegalArgumentException("Queue does not exist: " + file);
                }
            }
            case READ_ONLY_LAZY -> {}
            case CREATE -> {
                if (exists) {
                    throw new IllegalArgumentException("Queue already exists: " + file);
                }
                createQueueDir(file);
            }
            case OPEN_OR_CREATE -> {
                if (!exists) {
                    createQueueDir(file);
                }
            }
            case RECREATE -> {
                if (exists) {
                    deleteQueueFiles();
                } else {
                    createQueueDir(file);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported open mode: " + openMode);
        }
    }

    private void deleteQueueFiles() {
        for (final File file : files.listFiles()) {
            final boolean deleted = file.delete();
            assert deleted : files.queueName();
        }
    }

    private static void createQueueDir(final File file) {
        //NOTE: a concurrent creator may have created it in the meantime
        if (!file.mkdir() && !file.isDirectory()) {
            throw new IllegalArgumentException("Parent directory does not exist: " + file);
        }
    }

    private static boolean enableCopyFromPreviousRegion(final AppenderConfig appenderConfig) {
        final MappingStrategyConfig mappingStrategy = appenderConfig.payloadMappingStrategy();
        final int cacheSie = mappingStrategy.cacheSize();
        final int mapAhead = mappingStrategy.asyncMapping().isPresent() ?
                mappingStrategy.asyncMapping().get().regionsToMapAhead() : 0;
        return cacheSie > Math.max(1, mapAhead + 1);
    }

    private static IdPool idPool(final QueueFiles queueFiles, final int maxAppenders) {
        if (maxAppenders <= IdPool64.MAX_IDS) {
            return new IdPool64(queueFiles.idPoolFile());
        }
        if (maxAppenders <= IdPool256.MAX_IDS) {
            return new IdPool256(queueFiles.idPoolFile());
        }
        throw new IllegalArgumentException("Invalid value for max appenders: " + maxAppenders);
    }

    private <T extends AutoCloseable> T open(final T closeable) {
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

    private String queueNameIfNotClosed() {
        if (!isClosed()) {
            return files.queueName();
        }
        throw new IllegalStateException("Queue is closed: " + files.queueName());
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
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
