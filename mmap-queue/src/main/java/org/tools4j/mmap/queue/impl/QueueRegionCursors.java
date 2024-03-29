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
import org.tools4j.mmap.region.api.RegionCursor;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.InitialBytes;
import org.tools4j.mmap.region.impl.RolledFileMapper;
import org.tools4j.mmap.region.impl.SingleFileReadOnlyMapper;
import org.tools4j.mmap.region.impl.SingleFileReadWriteMapper;

import java.nio.channels.FileLock;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.greaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.nonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validRegionSize;

/**
 * Header and payload region mapper for queues.
 */
interface QueueRegionCursors extends AutoCloseable {
    /**
     * @return header region accessor
     */
    RegionCursor header();

    /**
     * @param appenderId appender id
     * @return payload region accessor
     */
    RegionCursor payload(short appenderId);

    @Override
    void close();

    /**
     * Factory method for read-only queue region mappers.
     *
     * @param queueName             queue name (Header file would have "_header" suffix and payload file would have
     *                              a "_payload" suffix.
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create region mappers
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param regionsToMapAhead     regions to map-ahead if async mapping is used (ignored in sync mode)
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @param waitingPolicy         waiting policy to use for cursor
     * @param timeoutHandler        handler for timeouts
     * @param logger                logger
     * @return an instance of QueueRegionMappers
     */
    static QueueRegionCursors forReadOnly(final String queueName,
                                          final String directory,
                                          final RegionMapperFactory regionMapperFactory,
                                          final int regionSize,
                                          final int regionCacheSize,
                                          final int regionsToMapAhead,
                                          final long maxFileSize,
                                          final boolean rollFiles,
                                          final WaitingPolicy waitingPolicy,
                                          final TimeoutHandler<? super RegionCursor> timeoutHandler,
                                          final Logger logger) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        validRegionSize(regionSize);
        validRegionCacheSize(regionCacheSize);
        greaterThanZero(maxFileSize, "maxFileSize");

        final String headerFileName = directory + "/" + queueName + "_header";
        final FileInitialiser headerInitialiser = QueueRegionCursors.headerInitialiser(MapMode.READ_ONLY);

        final FileMapper headerReadOnlyMapper;
        if (rollFiles) {
            headerReadOnlyMapper = RolledFileMapper.forReadOnly(headerFileName, maxFileSize, regionSize, headerInitialiser);
        } else {
            headerReadOnlyMapper = new SingleFileReadOnlyMapper(headerFileName, headerInitialiser);
        }

        try {
            final RegionCursor header = RegionCursor.managed(
                    regionMapperFactory.create(headerReadOnlyMapper, regionSize, regionCacheSize, regionsToMapAhead),
                    waitingPolicy, timeoutHandler
            );

            final Int2ObjectHashMap<RegionCursor> payloadCursors = new Int2ObjectHashMap<>();
            final IntFunction<RegionCursor> payloadCursorFactory = appenderId -> {
                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadOnlyMapper;
                if (rollFiles) {
                    payloadReadOnlyMapper = RolledFileMapper.forReadOnly(payloadFileName, maxFileSize, regionSize, (file, mode) -> {
                    });
                } else {
                    payloadReadOnlyMapper = new SingleFileReadOnlyMapper(payloadFileName, (file, mode) -> {
                    });
                }

                return RegionCursor.managed(
                        regionMapperFactory.create(payloadReadOnlyMapper, regionSize, regionCacheSize, regionsToMapAhead),
                        waitingPolicy, timeoutHandler
                );
            };

            return new QueueRegionCursors() {
                @Override
                public RegionCursor header() {
                    return header;
                }

                @Override
                public RegionCursor payload(final short appenderId) {
                    return payloadCursors.computeIfAbsent(appenderId, payloadCursorFactory);
                }

                @Override
                public void close() {
                    //TODO shared runtime could be closed too early if still used by other mapper
                    header.close();
                    payloadCursors.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-only queue region mappers. queue={}", queueName);
                }
            };
        } catch (Throwable ex) {
            headerReadOnlyMapper.close();
            //note: payloadReadOnlyMappers are encapsulated into payloadMapperFactory
            throw ex;
        }
    }

    /**
     * Factory method for read/write queue region mappers.
     *
     * @param queueName             queue name (Header file would have "_header" suffix and payload file would have
     *                              a "_payload" suffix.
     * @param directory             directory where the files are located
     * @param regionMapperFactory   factory to create region mappers
     * @param regionSize            region size in bytes
     * @param regionCacheSize       number of regions to cache
     * @param regionsToMapAhead     regions to map-ahead if async mapping is used (ignored in sync mode)
     * @param maxFileSize           max file size to prevent unexpected file growth. For single file or for each file if
     *                              file rolling is enabled.
     * @param rollFiles             true if file rolling is enabled, false otherwise.
     * @param filesToCreateAhead    how many payload files should be pre-created (ignored if rollFiles=false)
     * @param waitingPolicy         waiting policy to use for cursor
     * @param timeoutHandler        handler for timeouts
     * @param logger                logger
     * @return an instance of QueueRegionMappers
     */
    static QueueRegionCursors forReadWrite(final String queueName,
                                           final String directory,
                                           final RegionMapperFactory regionMapperFactory,
                                           final int regionSize,
                                           final int regionCacheSize,
                                           final int regionsToMapAhead,
                                           final long maxFileSize,
                                           final boolean rollFiles,
                                           final int filesToCreateAhead,
                                           final WaitingPolicy waitingPolicy,
                                           final TimeoutHandler<? super RegionCursor> timeoutHandler,
                                           final Logger logger) {
        requireNonNull(queueName);
        requireNonNull(directory);
        requireNonNull(regionMapperFactory);
        validRegionSize(regionSize);
        validRegionCacheSize(regionCacheSize);
        greaterThanZero(maxFileSize, "maxFileSize");
        nonNegative(filesToCreateAhead, "filesToCreateAhead");
        requireNonNull(waitingPolicy);
        requireNonNull(logger);

        final String headerFileName = directory + "/" + queueName + "_header";
        final MapMode mapMode = MapMode.READ_WRITE;
        final FileInitialiser headerInitialiser = QueueRegionCursors.headerInitialiser(mapMode);

        final FileMapper headerReadWriteMapper;
        if (rollFiles) {
            headerReadWriteMapper = RolledFileMapper.forReadWrite(headerFileName, maxFileSize, regionSize, 0, headerInitialiser);
        } else {
            headerReadWriteMapper = new SingleFileReadWriteMapper(headerFileName, maxFileSize, headerInitialiser);
        }

        try {
            final RegionCursor header = RegionCursor.managed(
                    regionMapperFactory.create(headerReadWriteMapper, regionSize, regionCacheSize, regionsToMapAhead),
                    waitingPolicy, timeoutHandler
            );

            final Int2ObjectHashMap<RegionCursor> payloadCursors = new Int2ObjectHashMap<>();
            final IntFunction<RegionCursor> payloadCursorFactory = appenderId -> {

                final String payloadFileName = directory + "/" + queueName + "_payload_" + appenderId;

                final FileMapper payloadReadWriteMapper;
                if (rollFiles) {
                    payloadReadWriteMapper = RolledFileMapper.forReadWrite(payloadFileName, maxFileSize, regionSize,
                            filesToCreateAhead, (file, mode) -> {});
                } else {
                    payloadReadWriteMapper = new SingleFileReadWriteMapper(payloadFileName, maxFileSize, (file, mode) -> {
                    });
                }

                return RegionCursor.managed(
                        regionMapperFactory.create(payloadReadWriteMapper, regionSize, regionCacheSize, regionsToMapAhead),
                        waitingPolicy, timeoutHandler
                );
            };

            return new QueueRegionCursors() {
                @Override
                public RegionCursor header() {
                    return header;
                }

                @Override
                public RegionCursor payload(short appenderId) {
                    return payloadCursors.computeIfAbsent(appenderId, payloadCursorFactory);
                }

                @Override
                public void close() {
                    header.close();
                    payloadCursors.forEachInt((appenderId, payloadAccessor) -> payloadAccessor.close());
                    logger.info("Closed all read-write queue region mappers. queue={}", queueName);
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
