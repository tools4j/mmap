/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.unsafe;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.RegionMetricsImpl;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.impl.Constraints.validatePowerOfTwo;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

/**
 * A direct region mapper that delegates mapping operations synchronously to the underlying {@link FileMapper} and
 * performs unmapping operations asynchronously in another thread.
 */
@Unsafe
public final class SyncRegionMapperAsyncUnmapper implements DirectRegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncRegionMapperAsyncUnmapper.class);

    private final AsyncRuntime asyncRuntime;
    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private final AsyncUnmapper asyncUnmapper;

    public SyncRegionMapperAsyncUnmapper(final AsyncRuntime asyncRuntime,
                                         final FileMapper fileMapper,
                                         final int regionSize,
                                         final int unmapCacheSize) {
        this(asyncRuntime, fileMapper, new RegionMetricsImpl(regionSize), unmapCacheSize);
    }

    public SyncRegionMapperAsyncUnmapper(final AsyncRuntime asyncRuntime,
                                         final FileMapper fileMapper,
                                         final RegionMetrics regionMetrics,
                                         final int unmapCacheSize) {
        validateRegionSize(regionMetrics.regionSize());
        validatePowerOfTwo("Unmap cache size", unmapCacheSize);
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
        this.asyncUnmapper = startAsyncUnmapper(unmapCacheSize);
    }

    private AsyncUnmapper startAsyncUnmapper(final int unmapCacheSize) {
        final AsyncUnmapper unmapper = new AsyncUnmapper(fileMapper, regionSize(), unmapCacheSize);
        asyncRuntime.register(unmapper);
        return unmapper;
    }

    @Override
    public FileMapper fileMapper() {
        return fileMapper;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        try {
            final long addr = fileMapper.map(position, regionSize);
            return addr > 0 ? addr : NULL_ADDRESS;
        } catch (final Exception exception) {
            return NULL_ADDRESS;
        }
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        if (!asyncUnmapper.unmap(position, address)) {
            fileMapper.unmap(position, address, regionSize);
        }
    }

    @Override
    public boolean isClosed() {
        return fileMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            asyncUnmapper.stopAndWait();
            asyncRuntime.deregister(asyncUnmapper);
            fileMapper.close();
            LOGGER.info("Closed {}.", this);
        }
    }

    @Override
    public String toString() {
        return "SyncRegionMapperAsyncUnmapper" +
                ":fileMapper=" + fileMapper +
                "|regionSize=" + regionSize() +
                "|unmappingCacheSize=" + (asyncUnmapper.ringMask + 1) +
                "|closed=" + isClosed();
    }

    private static final class AsyncUnmapper implements Recurring {
        private static final int PRODUCER_OFFSET = CACHE_LINE_LENGTH;
        private static final int CONSUMER_OFFSET = 3 * CACHE_LINE_LENGTH;
        private static final int HEADER_LENGTH = 5 * CACHE_LINE_LENGTH;
        static final int RECORD_LENGTH = Long.BYTES + Long.BYTES;
        static final int RECORD_SHIFT = 4;
        final FileMapper fileMapper;
        final int regionSize;
        final int ringMask;
        final AtomicBuffer buffer;//header, then pos/addr pairs
        final Timeout closeTimeout = new Timeout();

        AsyncUnmapper(final FileMapper fileMapper, final int regionSize, final int unmapCacheSize) {
            this.fileMapper = requireNonNull(fileMapper);
            this.regionSize = regionSize;
            this.ringMask = unmapCacheSize - 1;
            this.buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(HEADER_LENGTH + unmapCacheSize * RECORD_LENGTH));
        }

        private int ringIndex(final int index) {
            return index & ringMask;
        }

        private int offset(final int index) {
            return HEADER_LENGTH + (index << RECORD_SHIFT);
        }

        @Override
        public int execute() {
            if (closeTimeout.isStoppedOrStopping()) {
                return closeTimeout.isStopped() ? 0 : stop();
            }
            return tryUnmapOne();
        }

        private int stop() {
            int work = 0;
            if (closeTimeout.isStopGracefully()) {
                //do at most one cache round, then stop
                for (int i = 0; i <= ringMask; i++) {
                    if (tryUnmapOne() == 0 || closeTimeout.isStopImmediately()) {
                        break;
                    }
                    work++;
                }
            }
            closeTimeout.stopped();
            work++;
            return work;
        }

        private int tryUnmapOne() {
            final int ixProducer = buffer.getIntVolatile(PRODUCER_OFFSET);
            final int ixConsumer = buffer.getInt(CONSUMER_OFFSET);
            if (ixProducer == ixConsumer) {
                return 0;
            }
            final int ixConsumerNew = ringIndex(ixConsumer + 1);
            final int offset = offset(ixConsumer);
            final long pos = buffer.getLong(offset);
            final long addr = buffer.getLong(offset + Long.BYTES);
            buffer.setMemory(offset, Long.BYTES + Long.BYTES, (byte)0);
            buffer.putIntOrdered(CONSUMER_OFFSET, ixConsumerNew);
            fileMapper.unmap(pos, addr, regionSize);
            return 1;
        }

        boolean unmap(final long position, final long address) {
            final int ixConsumer = buffer.getIntVolatile(CONSUMER_OFFSET);
            final int ixProducer = buffer.getInt(PRODUCER_OFFSET);
            final int ixProducerNew = ringIndex(ixProducer + 1);
            if (ixProducerNew != ixConsumer) {
                final int offset = offset(ixProducer);
                buffer.putLong(offset, position);
                buffer.putLong(offset + Long.BYTES, address);
                buffer.putIntOrdered(PRODUCER_OFFSET, ixProducerNew);
                return true;
            }
            return false;
        }

        void stopAndWait() {
            closeTimeout.stopAndWait();
        }
    }
}
