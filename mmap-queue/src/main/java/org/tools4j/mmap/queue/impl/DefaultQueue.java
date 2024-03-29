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
import org.tools4j.mmap.region.api.RegionCursor;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.greaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.nonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validRegionSize;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates a separate instance of {@link Poller}.
 */
public final class DefaultQueue implements Queue {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultQueue.class);

    private final String name;
    private final Supplier<Poller> pollerFactory;
    private final Supplier<Reader> readerFactory;
    private final Supplier<Appender> appenderFactory;
    private final String description;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();

    public DefaultQueue(final String name,
                        final String directory,
                        final RegionMapperFactory regionMapperFactory,
                        final boolean manyAppenders,
                        final int regionSize,
                        final int regionCacheSize,
                        final int regionsToMapAhead,
                        final long maxFileSize,
                        final boolean rollFiles,
                        final int filesToCreateAhead,
                        final WaitingPolicy readWaitingPolicy,
                        final WaitingPolicy writeWaitingPolicy,
                        final boolean exceptionOnTimeout) {
        this.name = requireNonNull(name);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        validRegionSize(regionSize);
        validRegionCacheSize(regionCacheSize);
        greaterThanZero(maxFileSize, "maxFileSize");
        nonNegative(filesToCreateAhead, "filesToCreateAhead");

        final TimeoutHandler<RegionCursor> timeoutHandler = exceptionOnTimeout ?
                TimeoutHandlers.exception(name) : TimeoutHandlers.log(LOGGER, name);
        final AppenderIdPool appenderIdPool = open(manyAppenders ?
                new DefaultAppenderIdPool(directory, name) : AppenderIdPool.SINGLE_APPENDER);

        this.pollerFactory = () -> open(new DefaultPoller(name, QueueRegionCursors.forReadOnly(
                name, directory, regionMapperFactory, regionSize, regionCacheSize, regionsToMapAhead, maxFileSize,
                rollFiles, readWaitingPolicy, timeoutHandler, LOGGER
        )));

        this.readerFactory = () -> open(new DefaultReader(name, QueueRegionCursors.forReadOnly(
                name, directory, regionMapperFactory, regionSize, regionCacheSize, regionsToMapAhead, maxFileSize,
                rollFiles, readWaitingPolicy, timeoutHandler, LOGGER
        )));

        this.appenderFactory = () -> open(new DefaultAppender(name, QueueRegionCursors.forReadWrite(
                name, directory, regionMapperFactory, regionSize, regionCacheSize, regionsToMapAhead, maxFileSize,
                rollFiles, filesToCreateAhead, writeWaitingPolicy, timeoutHandler, LOGGER
        ), appenderIdPool));

        final String asyncInfo = regionMapperFactory.isAsync() ? String.format(
                ", regionsToMapAhead=%s, readWaitingPolicy=%s, writeWaitingPolicy=%s, exceptionOnTimeout=%s",
                regionsToMapAhead, readWaitingPolicy, writeWaitingPolicy, exceptionOnTimeout) : "";
        this.description = String.format("Queue{name=%s, directory=%s, regionMapperFactory=%s, regionSize=%d, " +
                        "regionCacheSize=%d, maxFileSize=%d, rollFiles=%s, filesToCreateAhead=%s%s}", name, directory,
                regionMapperFactory, regionSize, regionCacheSize, maxFileSize, rollFiles, filesToCreateAhead, asyncInfo);
    }

    private <T extends AutoCloseable> T open(final T closeable) {
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
    public String toString() {
        return description;
    }

    @Override
    public void close() {
        if (closeables.isEmpty()) {
            return;
        }
        CloseHelper.quietCloseAll(closeables);
        closeables.clear();
        LOGGER.info("Closed queue {}", name);
    }
}
