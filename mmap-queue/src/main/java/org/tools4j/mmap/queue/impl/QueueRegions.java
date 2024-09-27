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

import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.RolledFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadOnlyMapper;
import org.tools4j.mmap.region.impl.SingleFileReadWriteMapper;

import java.nio.channels.FileLock;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateGreaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

/**
 * Header and payload region for queues.
 */
interface QueueRegions extends AutoCloseable {
    /**
     * @return header region
     */
    Region header();

    /**
     * @param appenderId appender id
     * @return payload region
     */
    Region payload(int appenderId);

    @Override
    void close();

    /**
     * Factory method for read-only queue regions.
     *
     * @param queueName             queue name (Header file would have "_header" suffix and payload file would have
     *                              a "_payload" suffix.
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create regions
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param regionsToMapAhead     regions to map-ahead if async mapping is used (ignored in sync mode)
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @param logger                logger
     * @return an instance of QueueRegionMappers
     */
    static QueueRegions forReadOnly(final String queueName,
                                    final String directory,
                                    final RegionMapperFactory regionMapperFactory,
                                    final int regionSize,
                                    final int regionCacheSize,
                                    final int regionsToMapAhead,
                                    final long maxFileSize,
                                    final boolean rollFiles,
                                    final Logger logger) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        validateRegionSize(regionSize);
        validateRegionCacheSize(regionCacheSize);
        validateGreaterThanZero(maxFileSize, "maxFileSize");

        final String headerFileName = directory + "/" + queueName + "_header";
        final FileInitialiser headerInitialiser = QueueRegions.headerInitialiser(MapMode.READ_ONLY);

        final FileMapper headerReadOnlyMapper;
        if (rollFiles) {
            headerReadOnlyMapper = RolledFileMapper.forReadOnly(headerFileName, maxFileSize, regionSize, headerInitialiser);
        } else {
            headerReadOnlyMapper = new SingleFileReadOnlyMapper(headerFileName, headerInitialiser);
        }

        try {
            final Region header = Region.create(
                    regionMapperFactory.create(headerReadOnlyMapper, regionSize, regionCacheSize, regionsToMapAhead)
            );

            final Int2ObjectHashMap<Region> payloadRegions = new Int2ObjectHashMap<>();
            final IntFunction<Region> payloadRegionFactory = appenderId -> {
                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadOnlyMapper;
                if (rollFiles) {
                    payloadReadOnlyMapper = RolledFileMapper.forReadOnly(payloadFileName, maxFileSize, regionSize, (file, mode) -> {
                    });
                } else {
                    payloadReadOnlyMapper = new SingleFileReadOnlyMapper(payloadFileName, (file, mode) -> {
                    });
                }

                return Region.create(
                        regionMapperFactory.create(payloadReadOnlyMapper, regionSize, regionCacheSize, regionsToMapAhead)
                );
            };

            return new QueueRegions() {
                @Override
                public Region header() {
                    return header;
                }

                @Override
                public Region payload(final short appenderId) {
                    return payloadRegions.computeIfAbsent(appenderId, payloadRegionFactory);
                }

                @Override
                public void close() {
                    //TODO shared runtime could be closed too early if still used by other regions
                    header.close();
                    payloadRegions.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-only queue regions. queue={}", queueName);
                }
            };
        } catch (Throwable ex) {
            headerReadOnlyMapper.close();
            //note: payloadReadOnlyMappers are encapsulated into payloadMapperFactory
            throw ex;
        }
    }

    /**
     * Factory method for read/write queue regions.
     *
     * @param queueName             queue name (Header file would have "_header" suffix and payload file would have
     *                              a "_payload" suffix.
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create regions
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param regionsToMapAhead     regions to map-ahead if async mapping is used (ignored in sync mode)
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @param filesToCreateAhead    how many payload files should be pre-created (ignored if rollFiles=false)
     * @param logger                logger
     * @return an instance of QueueRegionMappers
     */
    static QueueRegions forReadWrite(final String queueName,
                                     final String directory,
                                     final RegionMapperFactory regionMapperFactory,
                                     final int regionSize,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead,
                                     final long maxFileSize,
                                     final boolean rollFiles,
                                     final int filesToCreateAhead,
                                     final Logger logger) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        validateRegionSize(regionSize);
        validateRegionCacheSize(regionCacheSize);
        validateGreaterThanZero(maxFileSize, "maxFileSize");
        validateNonNegative(filesToCreateAhead, "filesToCreateAhead");
        requireNonNull(logger);

        final String headerFileName = directory + "/" + queueName + "_header";
        final MapMode mapMode = MapMode.READ_WRITE;
        final FileInitialiser headerInitialiser = QueueRegions.headerInitialiser(mapMode);

        final FileMapper headerReadWriteMapper;
        if (rollFiles) {
            headerReadWriteMapper = RolledFileMapper.forReadWrite(headerFileName, maxFileSize, regionSize, 0, headerInitialiser);
        } else {
            headerReadWriteMapper = new SingleFileReadWriteMapper(headerFileName, maxFileSize, headerInitialiser);
        }

        try {
            final Region header = Region.create(
                    regionMapperFactory.create(headerReadWriteMapper, regionSize, regionCacheSize, regionsToMapAhead)
            );

            final Int2ObjectHashMap<Region> payloadRegions = new Int2ObjectHashMap<>();
            final IntFunction<Region> payloadRegionFactory = appenderId -> {

                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadWriteMapper;
                if (rollFiles) {
                    payloadReadWriteMapper = RolledFileMapper.forReadWrite(payloadFileName, maxFileSize, regionSize,
                            filesToCreateAhead, (file, mode) -> {});
                } else {
                    payloadReadWriteMapper = new SingleFileReadWriteMapper(payloadFileName, maxFileSize, (file, mode) -> {
                    });
                }

                return Region.create(
                        regionMapperFactory.create(payloadReadWriteMapper, regionSize, regionCacheSize, regionsToMapAhead)
                );
            };

            return new QueueRegions() {
                @Override
                public Region header() {
                    return header;
                }

                @Override
                public Region payload(short appenderId) {
                    return payloadRegions.computeIfAbsent(appenderId, payloadRegionFactory);
                }

                @Override
                public void close() {
                    header.close();
                    payloadRegions.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-write queue regions. queue={}", queueName);
                }
            };
        } catch (Throwable ex) {
            headerReadWriteMapper.close();
            //note: payloadReadWriteMappers are encapsulated into payloadMapperFactory
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
