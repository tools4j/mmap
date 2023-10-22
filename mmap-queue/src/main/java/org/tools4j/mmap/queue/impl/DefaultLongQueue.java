/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.tools4j.mmap.queue.api.LongAppender;
import org.tools4j.mmap.queue.api.LongPoller;
import org.tools4j.mmap.queue.api.LongQueue;
import org.tools4j.mmap.queue.api.LongReader;
import org.tools4j.mmap.region.api.RegionRingFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.Requirements.greaterThanZero;

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

    public DefaultLongQueue(final String name,
                            final String directory,
                            final RegionRingFactory regionRingFactory,
                            final int regionSize,
                            final int regionRingSize,
                            final int regionsToMapAhead,
                            final long maxFileSize,
                            final boolean rollFiles,
                            final long readTimeout,
                            final long writeTimeout,
                            final TimeUnit timeUnit,
                            final long nullValue
    ) {
        this.name = requireNonNull(name);
        this.nullValue = nullValue;
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

        this.pollerFactory = () -> new DefaultLongPoller(name, nullValue,
                RegionAccessors.forReadOnly(name, directory, regionRingFactory, regionSize, regionRingSize,
                        regionsToMapAhead, maxFileSize, rollFiles, readTimeout, timeUnit));

        this.readerFactory = () -> new DefaultLongReader(name, nullValue,
                RegionAccessors.forReadOnly(name, directory, regionRingFactory, regionSize, regionRingSize,
                        regionsToMapAhead, maxFileSize, rollFiles, readTimeout, timeUnit));

        this.appenderFactory = () -> new DefaultLongAppender(name, nullValue,
                RegionAccessors.forReadWrite(name, directory, regionRingFactory, regionSize,
                        regionRingSize, regionsToMapAhead, maxFileSize, rollFiles, writeTimeout, timeUnit));

        this.description = format("LongQueue{name=%s, directory=%s, regionRingFactory=%s, regionSize=%d, "
                        + "regionRingSize=%d, regionsToMapAhead=%d, maxFileSize=%d, rollFiles=%s, readTimeout=%d, writeTimeout=%d, timeUnit=%s}",
                name, directory, regionRingFactory, regionSize, regionRingSize, regionsToMapAhead, maxFileSize, rollFiles,
                readTimeout, writeTimeout, timeUnit);
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
        LOGGER.info("Closed queue {}", name);
    }
}
