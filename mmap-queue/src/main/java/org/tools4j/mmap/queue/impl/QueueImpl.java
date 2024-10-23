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
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.region.api.MappingConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates a separate instance of {@link Poller}.
 */
public final class QueueImpl implements Queue {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueueImpl.class);

    private final QueueFiles queueFiles;
    private final Supplier<Poller> pollerFactory;
    private final Supplier<Reader> readerFactory;
    private final Supplier<Appender> appenderFactory;
    private final List<AutoCloseable> closeables = new ArrayList<>();

    public QueueImpl(final File file, final int maxAppenders) {
        this(file, MappingConfig.getDefault(), maxAppenders);
    }

    public QueueImpl(final File file, final MappingConfig mappingConfig, final int maxAppenders) {
        this.queueFiles = new QueueFiles(file);

        final AppenderIdPool idPool = open(appenderIdPool(queueFiles, maxAppenders));
        this.pollerFactory = () -> open(
                new PollerImpl(queueFiles.queueName(), ReaderMappings.create(queueFiles, mappingConfig))
        );
        this.readerFactory = () -> open(
                new ReaderImpl(queueFiles.queueName(), ReaderMappings.create(queueFiles, mappingConfig))
        );
        this.appenderFactory = () -> open(
                new AppenderImpl(queueFiles.queueName(), AppenderMappings.create(queueFiles, idPool, mappingConfig),
                        mappingConfig.mappingStrategy().cacheSize())
        );
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
        return appenderFactory.get();
    }

    @Override
    public Poller createPoller() {
        return pollerFactory.get();
    }

    @Override
    public Reader createReader() {
        return readerFactory.get();
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
            LOGGER.info("Closed queue: {}", queueFiles.queueName());
        }
    }

    @Override
    public String toString() {
        return "QueueImpl" +
                ":queue=" + queueFiles.queueName() +
                "|closed=" + isClosed();
    }
}
