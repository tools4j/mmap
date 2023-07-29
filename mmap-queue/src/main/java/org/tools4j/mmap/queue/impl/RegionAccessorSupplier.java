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

import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
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

import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;
import static org.tools4j.mmap.region.impl.Requirements.greaterThanZero;

/**
 * Header and payload region accessor supplier for queues.
 */
public interface RegionAccessorSupplier extends AutoCloseable {
    /**
     * @return header region accessor
     */
    RegionAccessor header();

    /**
     * @param appenderId appender id
     * @return payload region accessor
     */
    RegionAccessor payload(short appenderId);

    @Override
    void close();

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
     * @param logger            logger
     * @return an instance of RegionAccessorSupplier
     */
    static RegionAccessorSupplier forReadOnly(final String queueName,
                                              final String directory,
                                              final RegionRingFactory regionRingFactory,
                                              final int regionSize,
                                              final int regionRingSize,
                                              final int regionsToMapAhead,
                                              final long maxFileSize,
                                              final boolean rollFiles,
                                              final long timout,
                                              final TimeUnit timeUnit,
                                              final Logger logger) {
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

        final String headerFileName = directory + "/" + queueName + "_header";

        final FileInitialiser headerInitialiser = RegionAccessorSupplier.headerInitialiser(MapMode.READ_ONLY);

        final FileMapper headerReadOnlyMapper;
        if (rollFiles) {
            headerReadOnlyMapper = RolledFileMapper.forReadOnly(headerFileName, maxFileSize, regionSize, headerInitialiser);
        } else {
            headerReadOnlyMapper = new SingleFileReadOnlyFileMapper(headerFileName, headerInitialiser);
        }

        try {
            final RegionAccessor header =
                    new RegionRingAccessor(regionRingFactory, headerReadOnlyMapper, regionRingSize,
                            regionSize, regionsToMapAhead, timout, timeUnit);

            final Int2ObjectHashMap<RegionAccessor> payloadAccessors = new Int2ObjectHashMap<>();

            final IntFunction<RegionAccessor> payloadAccessorFactory = appenderId -> {
                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadOnlyMapper;
                if (rollFiles) {
                    payloadReadOnlyMapper = RolledFileMapper.forReadOnly(payloadFileName, maxFileSize, regionSize, (file, mode) -> {
                    });
                } else {
                    payloadReadOnlyMapper = new SingleFileReadOnlyFileMapper(payloadFileName, (file, mode) -> {
                    });
                }

                return new RegionRingAccessor(regionRingFactory, payloadReadOnlyMapper, regionRingSize,
                        regionSize, regionsToMapAhead, timout, timeUnit);
            };

            return new RegionAccessorSupplier() {
                @Override
                public RegionAccessor header() {
                    return header;
                }

                @Override
                public RegionAccessor payload(short appenderId) {
                    return payloadAccessors.computeIfAbsent(appenderId, payloadAccessorFactory);
                }

                @Override
                public void close() {
                    header.close();
                    payloadAccessors.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-only region accessors. queue={}", queueName);
                }
            };
        } catch (Throwable ex) {
            headerReadOnlyMapper.close();
            //note: payloadReadOnlyMappers are encapsulated into payloadAccessorFactory
            throw ex;
        }
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
     * @param logger            logger
     * @return an instance of RegionAccessorSupplier
     */
    static RegionAccessorSupplier forReadWrite(final String queueName,
                                               final String directory,
                                               final RegionRingFactory regionRingFactory,
                                               final int regionSize,
                                               final int regionRingSize,
                                               final int regionsToMapAhead,
                                               final long maxFileSize,
                                               final boolean rollFiles,
                                               final long timout,
                                               final TimeUnit timeUnit,
                                               final Logger logger) {
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

        final String headerFileName = directory + "/" + queueName + "_header";
        final MapMode mapMode = MapMode.READ_WRITE;

        final FileInitialiser headerInitialiser = RegionAccessorSupplier.headerInitialiser(mapMode);

        final FileMapper headerReadWriteMapper;
        if (rollFiles) {
            headerReadWriteMapper = RolledFileMapper.forReadWrite(headerFileName, maxFileSize, regionSize, headerInitialiser);
        } else {
            headerReadWriteMapper = new SingleFileReadWriteFileMapper(headerFileName, maxFileSize, headerInitialiser);
        }

        try {

            final RegionAccessor header =
                    new RegionRingAccessor(regionRingFactory, headerReadWriteMapper, regionRingSize, regionSize,
                            regionsToMapAhead, timout, timeUnit);

            final Int2ObjectHashMap<RegionAccessor> payloadAccessors = new Int2ObjectHashMap<>();
            final IntFunction<RegionAccessor> payloadAccessorFactory = appenderId -> {

                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadWriteMapper;
                if (rollFiles) {
                    payloadReadWriteMapper = RolledFileMapper.forReadWrite(payloadFileName, maxFileSize, regionSize, (file, mode) -> {
                    });
                } else {
                    payloadReadWriteMapper = new SingleFileReadWriteFileMapper(payloadFileName, maxFileSize, (file, mode) -> {
                    });
                }

                return new RegionRingAccessor(regionRingFactory, payloadReadWriteMapper, regionRingSize, regionSize,
                        regionsToMapAhead, timout, timeUnit);
            };

            return new RegionAccessorSupplier() {
                @Override
                public RegionAccessor header() {
                    return header;
                }

                @Override
                public RegionAccessor payload(short appenderId) {
                    return payloadAccessors.computeIfAbsent(appenderId, payloadAccessorFactory);
                }

                @Override
                public void close() {
                    header.close();
                    payloadAccessors.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-write region accessors. queue={}", queueName);
                }
            };
        } catch (Throwable ex) {
            headerReadWriteMapper.close();
            //note: payloadReadWriteMappers are encapsulated into payloadAccessorFactory
            throw ex;
        }
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
