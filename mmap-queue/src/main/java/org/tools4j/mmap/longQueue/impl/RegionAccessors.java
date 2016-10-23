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
package org.tools4j.mmap.longQueue.impl;

import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;
import org.tools4j.mmap.region.api.RegionAccessor;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.RegionRingAccessor;
import org.tools4j.mmap.region.impl.RolledFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadOnlyFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadWriteFileMapper;
import org.tools4j.mmap.region.impl.Word;

import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.Requirements.greaterThanZero;

/**
 * Region accessors for long queues.
 */
public class RegionAccessors {
    public static final Word VALUE_WORD = new Word(8, 64);

    private RegionAccessors() {
        throw new IllegalStateException("RegionAccessors is not instantiable");
    }

    /**
     * Factory method for readOnly region accessors
     *
     * @param queueName         - queueName of the queue.
     *                          Header would have "_header" suffix and payload would have "_payload" suffix.
     * @param directory         - directory where the files are located
     * @param regionRingFactory - region ring factory
     * @param regionSize        - region size in bytes
     * @param regionRingSize    - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @param maxFileSize       - max file size for a single file, or each file when file rolling is enabled
     * @param rollFiles         - true if file rolling is enabled, false otherwise
     * @param timout            max time for which reading operation may block waiting for a region to be mapped if it is not yet
     * @param timeUnit          time unit
     * @return an instance of RegionAccessorSupplier
     */
    static RegionAccessor forReadOnly(final String queueName,
                                      final String directory,
                                      final RegionRingFactory regionRingFactory,
                                      final int regionSize,
                                      final int regionRingSize,
                                      final int regionsToMapAhead,
                                      final long maxFileSize,
                                      final boolean rollFiles,
                                      final long timout,
                                      final TimeUnit timeUnit) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionRingFactory);
        greaterThanZero(regionSize, "regionSize");
        greaterThanZero(regionRingSize, "regionRingSize");
        greaterThanZero(regionsToMapAhead, "regionsToMapAhead");
        greaterThanZero(maxFileSize, "maxFileSize");
        if (regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("regionRingSize must be multiple of " + REGION_SIZE_GRANULARITY);
        }

        final String fileName = directory + "/" + queueName;

        final FileInitialiser fileInitialiser = RegionAccessors.headerInitialiser(MapMode.READ_ONLY);

        final FileMapper readOnlyMapper;
        if (rollFiles) {
            readOnlyMapper = RolledFileMapper.forReadOnly(fileName, maxFileSize, regionSize, fileInitialiser);
        } else {
            readOnlyMapper = new SingleFileReadOnlyFileMapper(fileName, fileInitialiser);
        }

        return new RegionRingAccessor(regionRingFactory, readOnlyMapper, regionRingSize,
                regionSize, regionsToMapAhead, timout, timeUnit);

    }

    /**
     * Factory method for readWrite region accessors with file clearing option.
     *
     * @param queueName         - queue name
     *                          Header file would have "_header" suffix and payload file would have "_payload" suffix.
     * @param directory         - directory where the files are located
     * @param regionRingFactory - region ring factory
     * @param regionSize        - region size in bytes
     * @param regionRingSize    - number of regions in a ring
     * @param regionsToMapAhead - number of regions to map ahead.
     * @param maxFileSize       - max file size to prevent unexpected file growth. For single file or for each
     *                          file if file rolling is enabled.
     * @param rollFiles         true of file rolling is enabled, false otherwise.
     * @param timout            max time for which writing operation may block waiting for a region to be mapped if it is not yet
     * @param timeUnit          time unit
     * @return an instance of RegionAccessorSupplier
     */
    static RegionAccessor forReadWrite(final String queueName,
                                       final String directory,
                                       final RegionRingFactory regionRingFactory,
                                       final int regionSize,
                                       final int regionRingSize,
                                       final int regionsToMapAhead,
                                       final long maxFileSize,
                                       final boolean rollFiles,
                                       final long timout,
                                       final TimeUnit timeUnit) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionRingFactory);
        greaterThanZero(regionSize, "regionSize");
        greaterThanZero(regionRingSize, "regionRingSize");
        greaterThanZero(regionsToMapAhead, "regionsToMapAhead");
        greaterThanZero(maxFileSize, "maxFileSize");
        if (regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("regionRingSize must be multiple of " + REGION_SIZE_GRANULARITY);
        }

        final String fileName = directory + "/" + queueName;
        final MapMode mapMode = MapMode.READ_WRITE;

        final FileInitialiser fileInitialiser = RegionAccessors.headerInitialiser(mapMode);

        final FileMapper readWriteMapper;
        if (rollFiles) {
            readWriteMapper = RolledFileMapper.forReadWrite(fileName, maxFileSize, regionSize, fileInitialiser);
        } else {
            readWriteMapper = new SingleFileReadWriteFileMapper(fileName, maxFileSize, fileInitialiser);
        }

        return new RegionRingAccessor(regionRingFactory, readWriteMapper, regionRingSize, regionSize,
                regionsToMapAhead, timout, timeUnit);

    }

    static FileInitialiser headerInitialiser(final MapMode mode) {
        switch (mode) {
            case READ_ONLY:
                return (fileName, fileChannel) -> {
                    if (fileChannel.size() < 8) {
                        throw new IllegalArgumentException("Invalid file format: " + fileName);
                    }
                };
            case READ_WRITE:
                return (fileName, fileChannel) -> {
                    if (fileChannel.size() == 0) {
                        final FileLock lock = fileChannel.lock();
                        try {
                            if (fileChannel.size() == 0) { //allow file init once-only
                                fileChannel.transferFrom(InitialBytes.ZERO, 0, 8);
                                fileChannel.force(true);
                            }
                        } finally {
                            lock.release();
                        }
                    }
                };
            default:
                throw new IllegalArgumentException("Unsupported mode: " + mode);

        }
    }
}
