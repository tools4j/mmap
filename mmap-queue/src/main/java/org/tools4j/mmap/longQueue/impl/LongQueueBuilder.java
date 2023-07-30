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
package org.tools4j.mmap.longQueue.impl;

import org.tools4j.mmap.longQueue.api.LongQueue;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.Constants;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public final class LongQueueBuilder {
    public static final int DEFAULT_REGION_SIZE = ((int) Constants.REGION_SIZE_GRANULARITY) * 1024; //4MB
    public static final int DEFAULT_REGION_RING_SIZE = 4;
    public static final int DEFAULT_REGIONS_TO_MAP_AHEAD = 1;
    public static final long DEFAULT_MAX_FILE_SIZE = ((long)DEFAULT_REGION_SIZE) * 256; //1GB
    public static final boolean DEFAULT_ROLL_FILES = true;
    private static final long DEFAULT_READ_TIMEOUT = 100;
    private static final long DEFAULT_WRITE_TIMEOUT = 2000;
    private static final TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;

    // required params
    private String directory;
    private String name;
    private RegionRingFactory regionRingFactory;

    //defaulted params
    private int regionSize = DEFAULT_REGION_SIZE;
    private int regionRingSize = DEFAULT_REGION_RING_SIZE;
    private int regionsToMapAhead = DEFAULT_REGIONS_TO_MAP_AHEAD;
    private long maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private boolean rollFiles = DEFAULT_ROLL_FILES;
    private long readTimeout = DEFAULT_READ_TIMEOUT;
    private long writeTimeout = DEFAULT_WRITE_TIMEOUT;
    private TimeUnit timeUnit = DEFAULT_TIME_UNIT;

    public LongQueueBuilder(final String name, final String directory, final RegionRingFactory regionRingFactory) {
        this.name = requireNonNull(name);
        this.directory = requireNonNull(directory);
        this.regionRingFactory = requireNonNull(regionRingFactory);
    }

    public LongQueueBuilder directory(final String directory) {
        this.directory = requireNonNull(directory);
        return this;
    }

    LongQueueBuilder name(final String name) {
        this.name = requireNonNull(name);
        return this;
    }

    public LongQueueBuilder regionRingFactory(final RegionRingFactory regionRingFactory) {
        this.regionRingFactory = requireNonNull(regionRingFactory);
        return this;
    }

    public LongQueueBuilder regionSize(final int regionSize) {
        this.regionSize = regionSize;
        return this;
    }

    public LongQueueBuilder regionRingSize(final int regionRingSize) {
        this.regionRingSize = regionRingSize;
        return this;
    }

    public LongQueueBuilder regionsToMapAhead(final int regionsToMapAhead) {
        this.regionsToMapAhead = regionsToMapAhead;
        return this;
    }

    public LongQueueBuilder maxFileSize(final long maxFileSize) {
        this.maxFileSize = maxFileSize;
        return this;
    }

    public LongQueueBuilder rollFiles(final boolean rollFiles) {
        this.rollFiles = rollFiles;
        return this;
    }

    public LongQueueBuilder readTimeout(final long readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    public LongQueueBuilder writeTimeout(final long writeTimeout) {
        this.writeTimeout = writeTimeout;
        return this;
    }

    public LongQueueBuilder timeUnit(final TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    public LongQueue build() {
        return new DefaultLongQueue(name, directory, regionRingFactory, regionSize,
                regionRingSize, regionsToMapAhead, maxFileSize, rollFiles, readTimeout, writeTimeout, timeUnit);
    }
}
