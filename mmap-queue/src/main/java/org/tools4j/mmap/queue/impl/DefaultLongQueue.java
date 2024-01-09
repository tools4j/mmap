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
import org.tools4j.mmap.queue.api.LongAppender;
import org.tools4j.mmap.queue.api.LongPoller;
import org.tools4j.mmap.queue.api.LongQueue;
import org.tools4j.mmap.queue.api.LongReader;
import org.tools4j.mmap.region.api.RegionMapperFactory;
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
 * Implementation of {@link LongQueue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates a separate instance of {@link LongPoller}.
 */
public final class DefaultLongQueue implements LongQueue {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongQueue.class);

    private final String name;
    private final long nullValue;
    private final Supplier<LongPoller> pollerFactory;
    private final Supplier<LongReader> readerFactory;
    private final Supplier<LongAppender> appenderFactory;
    private final String description;
    private final List<AutoCloseable> closeables = new CopyOnWriteArrayList<>();

    public DefaultLongQueue(final String name,
                            final String directory,
                            final RegionMapperFactory regionMapperFactory,
                            final int regionSize,
                            final int regionCacheSize,
                            final int regionsToMapAhead,
                            final long maxFileSize,
                            final boolean rollFiles,
                            final int filesToCreateAhead,
                            final long nullValue,
                            final WaitingPolicy readWaitingPolicy,
                            final WaitingPolicy writeWaitingPolicy,
                            final boolean exceptionOnTimeout) {
        this.name = requireNonNull(name);
        this.nullValue = nullValue;
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        requireNonNull(readWaitingPolicy);
        requireNonNull(writeWaitingPolicy);
        validRegionSize(regionSize);
        validRegionCacheSize(regionCacheSize);
        greaterThanZero(maxFileSize, "maxFileSize");
        nonNegative(filesToCreateAhead, "filesToCreateAhead");

        final RegionMapperFactory readMapperFactory = regionMapperFactory.isAsync() ?
                RegionMapperFactory.async(regionMapperFactory, readWaitingPolicy,
                        exceptionOnTimeout ? TimeoutHandlers.exception(name) : TimeoutHandlers.log(LOGGER, name))
                : regionMapperFactory;
        final RegionMapperFactory writeMapperFactory = regionMapperFactory.isAsync() ?
                RegionMapperFactory.async(regionMapperFactory, writeWaitingPolicy,
                        exceptionOnTimeout ? TimeoutHandlers.exception(name) : TimeoutHandlers.log(LOGGER, name))
                : regionMapperFactory;

        this.pollerFactory = () -> open(new DefaultLongPoller(name, nullValue,
                LongQueueRegionMappers.forReadOnly(name, directory, readMapperFactory, regionSize, regionCacheSize,
                        regionsToMapAhead, maxFileSize, rollFiles)));

        this.readerFactory = () -> open(new DefaultLongReader(name, nullValue,
                LongQueueRegionMappers.forReadOnly(name, directory, readMapperFactory, regionSize, regionCacheSize,
                        regionsToMapAhead, maxFileSize, rollFiles)));

        this.appenderFactory = () -> open(new DefaultLongAppender(nullValue,
                LongQueueRegionMappers.forReadWrite(name, directory, writeMapperFactory, regionSize, regionCacheSize,
                        regionsToMapAhead, maxFileSize, rollFiles, filesToCreateAhead)));

        final String asyncInfo = regionMapperFactory.isAsync() ? String.format(
                ", regionsToMapAhead=%s, readWaitingPolicy=%s, writeWaitingPolicy=%s, exceptionOnTimeout=%s",
                regionsToMapAhead, readMapperFactory, writeMapperFactory, exceptionOnTimeout) : "";
        this.description = String.format("LongQueue{name=%s, directory=%s, regionMapperFactory=%s, regionSize=%d, " +
                        "regionCacheSize=%d, maxFileSize=%d, rollFiles=%s, filesToCreateAhead=%s%s}", name, directory,
                regionMapperFactory, regionSize, regionCacheSize, maxFileSize, rollFiles, filesToCreateAhead, asyncInfo);
    }

    private <T extends AutoCloseable> T open(final T closeable) {
        closeables.add(closeable);
        return closeable;
    }

    static long maskNullValue(final long value, final long nullValue) {
        return value != DEFAULT_NULL_VALUE ? value : nullValue;
    }

    static long unmaskNullValue(final long value, final long nullValue) {
        return value != nullValue ? value : DEFAULT_NULL_VALUE;
    }


    @Override
    public long nullValue() {
        return nullValue;
    }

    @Override
    public LongAppender createAppender() {
        return appenderFactory.get();
    }

    @Override
    public LongPoller createPoller() {
        return pollerFactory.get();
    }

    @Override
    public LongReader createReader() {
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
        LOGGER.info("Closed queue {}", name);
    }
}
