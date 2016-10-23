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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.concurrent.api.UniqueIdPool;
import org.tools4j.mmap.concurrent.imp.DefaultUniqueIdPool;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.region.api.RegionRingFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.Requirements.greaterThanZero;

/**
 * Implementation of {@link Queue} that allows a multiple writing threads and
 * multiple reading threads, when each thread creates a separate instance of {@link Poller}.
 */
public final class DefaultQueue implements Queue {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultQueue.class);
    private static final int MAX_APPENDER_IDS = 256;

    private final String name;
    private final UniqueIdPool appenderIdPool;
    private final Supplier<Poller> pollerFactory;
    private final Supplier<Reader> readerFactory;
    private final Supplier<Appender> appenderFactory;
    private final String description;

    public DefaultQueue(final String name,
                        final String directory,
                        final boolean manyAppenders,
                        final RegionRingFactory regionRingFactory,
                        final int regionSize,
                        final int regionRingSize,
                        final int regionsToMapAhead,
                        final long maxFileSize,
                        final boolean rollFiles,
                        final long readTimeout,
                        final long writeTimeout,
                        final TimeUnit timeUnit
    ) {
        this.name = requireNonNull(name);
        requireNonNull(directory);
        requireNonNull(regionRingFactory);
        requireNonNull(timeUnit);
        greaterThanZero(regionSize, "regionSize");
        greaterThanZero(regionRingSize, "regionRingSize");
        greaterThanZero(regionsToMapAhead, "regionsToMapAhead");
        greaterThanZero(maxFileSize, "maxFileSize");

        if (regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("regionRingSize must be multiple of " + REGION_SIZE_GRANULARITY);
        }

        this.appenderIdPool = manyAppenders ? new DefaultUniqueIdPool(directory, name, MAX_APPENDER_IDS) : UniqueIdPool.SINGLE_ID_POOL;

        this.pollerFactory = () -> new DefaultPoller(name,
                RegionAccessorSupplier.forReadOnly(name, directory, regionRingFactory, regionSize, regionRingSize,
                        regionsToMapAhead, maxFileSize, rollFiles, readTimeout, timeUnit, LOGGER));

        this.readerFactory = () -> new DefaultReader(name,
                RegionAccessorSupplier.forReadOnly(name, directory, regionRingFactory, regionSize, regionRingSize,
                        regionsToMapAhead, maxFileSize, rollFiles, readTimeout, timeUnit, LOGGER));

        this.appenderFactory = () -> new DefaultAppender(name,
                RegionAccessorSupplier.forReadWrite(name, directory, regionRingFactory, regionSize,
                        regionRingSize, regionsToMapAhead, maxFileSize, rollFiles, writeTimeout, timeUnit, LOGGER), appenderIdPool);

        this.description = format("Queue{name=%s, directory=%s, regionRingFactory=%s, regionSize=%d, "
                        + "regionRingSize=%d, regionsToMapAhead=%d, maxFileSize=%d, rollFiles=%s, readTimeout=%d, writeTimeout=%d, timeUnit=%s}",
                name, directory, regionRingFactory, regionSize, regionRingSize, regionsToMapAhead, maxFileSize, rollFiles,
                readTimeout, writeTimeout, timeUnit);
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
        appenderIdPool.close();
        LOGGER.info("Closed queue {}", name);
    }
}
