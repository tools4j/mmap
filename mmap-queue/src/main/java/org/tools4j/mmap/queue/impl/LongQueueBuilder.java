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

import org.tools4j.mmap.queue.api.LongQueue;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.WaitingPolicy;
import org.tools4j.mmap.region.impl.Constants;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.tools4j.mmap.queue.api.LongQueue.DEFAULT_NULL_VALUE;
import static org.tools4j.mmap.region.impl.Constraints.greaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.nonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validRegionSize;

public final class LongQueueBuilder {
    public static final int DEFAULT_REGION_SIZE = ((int) Constants.REGION_SIZE_GRANULARITY) * 64; //~256K
    public static final int DEFAULT_REGION_CACHE_SIZE = 4;
    public static final int DEFAULT_REGIONS_TO_MAP_AHEAD = 1;
    public static final long DEFAULT_MAX_FILE_SIZE = 64 * 1024 * 1024;
    public static final boolean DEFAULT_ROLL_FILES = true;
    public static final int DEFAULT_FILES_TO_CREATE_AHEAD = 1;
    private static final long DEFAULT_READ_TIMEOUT_MILLIS = 500;
    private static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 2000;
    private static final boolean DEFAULT_EXCEPTION_ON_TIMEOUT = true;

    // required params
    private String directory;
    private String name;
    private RegionMapperFactory regionMapperFactory;
    //defaulted params
    private int regionSize = DEFAULT_REGION_SIZE;
    private int regionCacheSize = DEFAULT_REGION_CACHE_SIZE;
    private int regionsToMapAhead = DEFAULT_REGIONS_TO_MAP_AHEAD;
    private long maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private boolean rollFiles = DEFAULT_ROLL_FILES;
    private int filesToCreateAhead = DEFAULT_FILES_TO_CREATE_AHEAD;
    private WaitingPolicy readWaitingPolicy;
    private WaitingPolicy writeWaitingPolicy;
    private boolean exceptionOnTimeout;
    private long nullValue = DEFAULT_NULL_VALUE;

    public LongQueueBuilder(final String name, final String directory, final RegionMapperFactory regionMapperFactory) {
        this.name = requireNonNull(name);
        this.directory = requireNonNull(directory);
        this.regionMapperFactory = requireNonNull(regionMapperFactory);
        this.readWaitingPolicy = regionMapperFactory.isAsync() ?
                WaitingPolicy.busySpinWaiting(DEFAULT_READ_TIMEOUT_MILLIS, MILLISECONDS) : WaitingPolicy.noWait();
        this.writeWaitingPolicy = regionMapperFactory.isAsync() ?
                WaitingPolicy.busySpinWaiting(DEFAULT_WRITE_TIMEOUT_MILLIS, MILLISECONDS) : WaitingPolicy.noWait();
        this.exceptionOnTimeout = !regionMapperFactory.isAsync() || DEFAULT_EXCEPTION_ON_TIMEOUT;
    }

    public LongQueueBuilder directory(final String directory) {
        this.directory = requireNonNull(directory);
        return this;
    }

    public LongQueueBuilder name(final String name) {
        this.name = requireNonNull(name);
        return this;
    }

    public LongQueueBuilder regionSize(final int regionSize) {
        validRegionSize(regionSize);
        this.regionSize = regionSize;
        return this;
    }

    public LongQueueBuilder regionCacheSize(final int regionCacheSize) {
        validRegionCacheSize(regionCacheSize);
        this.regionCacheSize = regionCacheSize;
        return this;
    }

    public LongQueueBuilder regionsToMapAhead(final int regionsToMapAhead) {
        this.regionsToMapAhead = regionsToMapAhead;
        return this;
    }

    public LongQueueBuilder maxFileSize(final long maxFileSize) {
        greaterThanZero(maxFileSize, "maxFileSize");
        this.maxFileSize = maxFileSize;
        return this;
    }

    public LongQueueBuilder filesToCreateAhead(final int nFiles) {
        nonNegative(nFiles, "filesToCreateAhead");
        this.filesToCreateAhead = nFiles;
        return this;
    }

    public LongQueueBuilder rollFiles(final boolean rollFiles) {
        this.rollFiles = rollFiles;
        return this;
    }

    public LongQueueBuilder readWaitingPolicy(final WaitingPolicy waitingPolicy) {
        this.readWaitingPolicy = requireNonNull(waitingPolicy);
        return this;
    }

    public LongQueueBuilder readTimeout(final long readTimeout, final TimeUnit unit) {
        return readWaitingPolicy(WaitingPolicy.busySpinWaiting(readTimeout, unit));
    }

    public LongQueueBuilder writeWaitingPolicy(final WaitingPolicy waitingPolicy) {
        this.writeWaitingPolicy = requireNonNull(waitingPolicy);
        return this;
    }

    public LongQueueBuilder writeTimeout(final long writeTimeout, final TimeUnit unit) {
        return writeWaitingPolicy(WaitingPolicy.busySpinWaiting(writeTimeout, unit));
    }

    public LongQueueBuilder exceptionOnTimeout(final boolean exceptionOnTimeout) {
        this.exceptionOnTimeout = exceptionOnTimeout;
        return this;
    }

    public LongQueueBuilder nullValue(final long nullValue) {
        this.nullValue = nullValue;
        return this;
    }

    public LongQueue build() {
        return new DefaultLongQueue(
                name, directory, regionMapperFactory, regionSize, regionCacheSize, regionsToMapAhead, maxFileSize,
                rollFiles, filesToCreateAhead, nullValue, readWaitingPolicy, writeWaitingPolicy, exceptionOnTimeout
        );
    }
}
