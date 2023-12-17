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

import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.RolledFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadOnlyFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadWriteFileMapper;
import org.tools4j.mmap.region.impl.Word;

import java.nio.channels.FileLock;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.Constraints.greaterThanZero;

/**
 * Region mappers for long queues.
 */
enum LongQueueRegionMappers {
    ;
    public static final Word VALUE_WORD = new Word(8, 64);

    /**
     * Factory method for read-only long-queue region mappers.
     *
     * @param queueName             queue name (Header file would have "_header" suffix and payload file would have
     *                              a "_payload" suffix.
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create region mappers
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @return an instance of RegionMapper
     */
    static RegionMapper forReadOnly(final String queueName,
                                    final String directory,
                                    final RegionMapperFactory regionMapperFactory,
                                    final int regionSize,
                                    final int regionCacheSize,
                                    final long maxFileSize,
                                    final boolean rollFiles) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        greaterThanZero(regionSize, "regionSize");
        greaterThanZero(regionCacheSize, "regionCacheSize");
        greaterThanZero(maxFileSize, "maxFileSize");
        if (regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("regionSize must be multiple of " + REGION_SIZE_GRANULARITY);
        }

        final String fileName = directory + "/" + queueName;

        final FileInitialiser fileInitialiser = LongQueueRegionMappers.headerInitialiser(MapMode.READ_ONLY);

        final FileMapper readOnlyMapper;
        if (rollFiles) {
            readOnlyMapper = RolledFileMapper.forReadOnly(fileName, maxFileSize, regionSize, fileInitialiser);
        } else {
            readOnlyMapper = new SingleFileReadOnlyFileMapper(fileName, fileInitialiser);
        }

        return regionMapperFactory.create(readOnlyMapper, regionSize, regionCacheSize);
    }

    /**
     * Factory method for read/write long-queue region mappers.
     *
     * @param queueName             queue name
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create region mappers
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @return an instance of RegionMapper
     */
    static RegionMapper forReadWrite(final String queueName,
                                     final String directory,
                                     final RegionMapperFactory regionMapperFactory,
                                     final int regionSize,
                                     final int regionCacheSize,
                                     final long maxFileSize,
                                     final boolean rollFiles) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        greaterThanZero(regionSize, "regionSize");
        greaterThanZero(regionCacheSize, "regionCacheSize");
        greaterThanZero(maxFileSize, "maxFileSize");
        if (regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("regionSize must be multiple of " + REGION_SIZE_GRANULARITY);
        }

        final String fileName = directory + "/" + queueName;
        final MapMode mapMode = MapMode.READ_WRITE;

        final FileInitialiser fileInitialiser = LongQueueRegionMappers.headerInitialiser(mapMode);

        final FileMapper readWriteMapper;
        if (rollFiles) {
            readWriteMapper = RolledFileMapper.forReadWrite(fileName, maxFileSize, regionSize, fileInitialiser);
        } else {
            readWriteMapper = new SingleFileReadWriteFileMapper(fileName, maxFileSize, fileInitialiser);
        }

        return regionMapperFactory.create(readWriteMapper, regionSize, regionCacheSize);
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