/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.tools4j.mmap.region.api.Unsafe;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validatePowerOfTwo;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public final class AsyncRingRegionMapper implements RegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncRingRegionMapper.class);
    private static final long GRACEFUL_CLOSE_TIMEOUT_MILLIS = 4000;
    private static final long MAX_CLOSE_TIMEOUT_MILLIS = 5000;

    private final AsyncRuntime mappingRuntime;
    private final AsyncRuntime unmappingRuntime;
    private final FileMapper fileMapper;
    private final int regionSize;
    private final int regionSizeBits;
    private final int cacheSizeMask;
    private final int mapAhead;
    private final long[] positions;
    private final long[] addresses;
    private final BackgroundMapper backgroundMapper;
    private final BackgroundUnmapper backgroundUnmapper;
    private long lastPositionMapped;
    private boolean closed;

    //some statistics reported on close
    private long cacheStats;
    private long aheadStats;
    private long syncStats;
    private long busyStats;

    public AsyncRingRegionMapper(final AsyncRuntime mappingRuntime,
                                 final AsyncRuntime unmappingRuntime,
                                 final FileMapper fileMapper,
                                 final int regionSize,
                                 final int cacheSize,
                                 final int mapAhead) {
        this(mappingRuntime, unmappingRuntime, fileMapper, regionSize, cacheSize, mapAhead, 2 * cacheSize);
    }

    public AsyncRingRegionMapper(final AsyncRuntime mappingRuntime,
                                 final AsyncRuntime unmappingRuntime,
                                 final FileMapper fileMapper,
                                 final int regionSize,
                                 final int cacheSize,
                                 final int mapAhead,
                                 final int unmapCacheSize) {
        validateRegionSize(regionSize);
        validateRegionCacheSize(cacheSize);
        validatePowerOfTwo("Unmap cache size", unmapCacheSize);
        this.mappingRuntime = requireNonNull(mappingRuntime);
        this.unmappingRuntime = requireNonNull(unmappingRuntime);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionSize = regionSize;
        this.regionSizeBits = Integer.SIZE - Integer.numberOfLeadingZeros(regionSize - 1);
        this.cacheSizeMask = cacheSize - 1;
        this.mapAhead = mapAhead;
        this.positions = new long[cacheSize];
        this.addresses = new long[cacheSize];
        this.lastPositionMapped = NULL_POSITION;
        this.backgroundMapper = startBackgroundMapper(cacheSize);
        this.backgroundUnmapper = startBackgroundUnmapper(unmapCacheSize);
        Arrays.fill(positions, NULL_POSITION);
        Arrays.fill(addresses, NULL_ADDRESS);
    }

    private BackgroundMapper startBackgroundMapper(final int cacheSize) {
        final BackgroundMapper mapper = new BackgroundMapper(fileMapper, regionSize, cacheSize);
        mappingRuntime.register(mapper);
        return mapper;
    }

    private BackgroundUnmapper startBackgroundUnmapper(final int unmapCacheSize) {
        final BackgroundUnmapper unmapper = new BackgroundUnmapper(fileMapper, regionSize, unmapCacheSize);
        unmappingRuntime.register(unmapper);
        return unmapper;
    }

    @Override
    public int regionSize() {
        return regionSize;
    }

    @Override
    public FileMapper fileMapper() {
        return fileMapper;
    }

    private int cacheIndex(final long position) {
        return (int)(cacheSizeMask & (position >> regionSizeBits));
    }

    @Override
    public long map(final long position) {
        final int cacheIndex = cacheIndex(position);
        if (positions[cacheIndex] == position) {
            cacheStats++;
            return addresses[cacheIndex];
        }
        return map(cacheIndex, position);
    }

    private long map(final int cacheIndex, final long position) {
        final BackgroundMapper background = backgroundMapper;
        long addr = NULL_ADDRESS;
        if (background.isMappedTo(cacheIndex, position)) {
            addr = background.consumeMappedAddress(cacheIndex);
            aheadStats++;
        }
        if (addr == NULL_ADDRESS) {
            addr = mapSync(position);
            syncStats++;
        }
        if (addr == NULL_ADDRESS) {
            return NULL_ADDRESS;
        }
        unmapIfNecessary(cacheIndex);
        positions[cacheIndex] = position;
        addresses[cacheIndex] = addr;
        mapAhead(position);
        return addr;
    }

    private void mapAhead(final long position) {
        assert position >= 0;
        final int mapAhead = this.mapAhead;
        if (mapAhead <= 0) {
            return;
        }
        final long lastPosMapped = this.lastPositionMapped;
        if (position != lastPosMapped) {
            final int regionSize = this.regionSize;
            long nextPosition = position;
            if (position == lastPosMapped + regionSize || (position == 0 && lastPosMapped == NULL_POSITION)) {
                //sequential forward access
                for (int i = 0; i < mapAhead; i++) {
                    nextPosition += regionSize;
                    if (!mapAhead(cacheIndex(nextPosition), nextPosition)) {
                        busyStats++;
                        break;
                    }
                }
            } else if (position == lastPosMapped - regionSize || (position > 0 && lastPosMapped == NULL_POSITION)) {
                //sequential backward access
                for (int i = 0; i < mapAhead && nextPosition >= regionSize; i++) {
                    nextPosition -= regionSize;
                    if (!mapAhead(cacheIndex(nextPosition), nextPosition)) {
                        busyStats++;
                        break;
                    }
                }
            }
            //else: random access pattern, we don't try to predict the next access
            this.lastPositionMapped = position;
        }
    }

    private boolean mapAhead(final int index, final long position) {
        return positions[index] == position || backgroundMapper.mapAhead(index, position);
    }

    private void unmapIfNecessary(final int cacheIndex) {
        final long addr = addresses[cacheIndex];
        if (addr != NULL_ADDRESS) {
            final long pos = positions[cacheIndex];
            addresses[cacheIndex] = NULL_ADDRESS;
            positions[cacheIndex] = NULL_POSITION;
            assert pos != NULL_POSITION;
            if (!backgroundUnmapper.unmap(pos, addr)) {
                unmapSync(pos, addr);
            }
        } else {
            assert positions[cacheIndex] == NULL_POSITION;
        }
    }

    private long mapSync(final long position) {
        return fileMapper.map(position, regionSize);
    }

    private void unmapSync(final long position, final long address) {
        fileMapper.unmap(position, address, regionSize);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                final long end = System.currentTimeMillis() + MAX_CLOSE_TIMEOUT_MILLIS;
                final long gracefulTimeoutMillis = GRACEFUL_CLOSE_TIMEOUT_MILLIS;
                final int cacheSize = cacheSizeMask + 1;
                for (int cacheIndex = 0; cacheIndex < cacheSize; cacheIndex++) {
                    unmap(cacheIndex);
                }
                backgroundMapper.stop(backgroundUnmapper);
                backgroundMapper.await(Math.min(gracefulTimeoutMillis, end - System.currentTimeMillis()));
                backgroundUnmapper.stopGracefully();
                if (!backgroundUnmapper.await(Math.min(gracefulTimeoutMillis, end - System.currentTimeMillis()))) {
                    backgroundUnmapper.stopImmediately();
                    backgroundUnmapper.await(end - System.currentTimeMillis());
                }
            } finally {
                mappingRuntime.deregister(backgroundMapper);
                unmappingRuntime.deregister(backgroundUnmapper);
                closed = true;
            }
            LOGGER.info("Closed {}. Statistics:cache={}|ahead={}|sync={}|busy={}", this,
                    cacheStats, aheadStats, syncStats, busyStats);
        }
    }

    private void unmap(final int cacheIndex) {
        final long pos = positions[cacheIndex];
        if (pos != NULL_POSITION) {
            final long addr = addresses[cacheIndex];
            assert addr != NULL_ADDRESS;
            positions[cacheIndex] = NULL_POSITION;
            addresses[cacheIndex] = NULL_ADDRESS;
            try {
                fileMapper.unmap(addr, pos, regionSize);
            } catch (final Exception e) {
                //ignore
            }
        } else {
            assert addresses[cacheIndex] == NULL_ADDRESS;
        }
    }

    @Override
    public String toString() {
        return "AsyncRingRegionMapper" +
                ":cacheSize=" + (cacheSizeMask + 1) +
                "|mapAhead=" + mapAhead +
                "|regionSize=" + regionSize();
    }

    private static final class BackgroundMapper implements Recurring {
        private static final int FLAG_CLEAR = 0;
        private static final int FLAG_PENDING = 1;
        private static final long NULL_SEQUENCE = 0;
        private static final int HEADER_OFFSET = CACHE_LINE_LENGTH;
        private static final int HEADER_LENGTH = 4 * CACHE_LINE_LENGTH;//need only 3x, but 4x makes it a power of two
        private static final int HEADER_SHIFT = Integer.SIZE - Integer.numberOfLeadingZeros(HEADER_LENGTH - 1);

        volatile BackgroundUnmapper terminateUnmapper;
        final FileMapper fileMapper;
        final int regionSize;
        final int ringMask;
        final AtomicBuffer headerBuffer;
        final long[] requestSequences;
        final long[] requestedPositions;
        final long[] mappedPositions;
        final long[] mappedAddresses;
        int requestSequence;

        BackgroundMapper(final FileMapper fileMapper, final int regionSize, final int cacheSize) {
            this.fileMapper = requireNonNull(fileMapper);
            this.regionSize = regionSize;
            this.ringMask = cacheSize - 1;
            this.headerBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(cacheSize * HEADER_LENGTH));
            this.requestSequences = new long[cacheSize];
            this.requestedPositions = new long[cacheSize];
            this.mappedPositions = new long[cacheSize];
            this.mappedAddresses = new long[cacheSize];
            Arrays.fill(requestSequences, NULL_SEQUENCE);
            Arrays.fill(requestedPositions, NULL_POSITION);
            Arrays.fill(mappedPositions, NULL_POSITION);
            Arrays.fill(mappedAddresses, NULL_ADDRESS);
        }

        int headerOffset(final int index) {
            return HEADER_OFFSET + (index << HEADER_SHIFT);
        }

        void setPendingFlag(final int index) {
            requestSequence++;
            requestSequences[index] = requestSequence;
            headerBuffer.putIntOrdered(headerOffset(index), FLAG_PENDING);
        }

        void clearPendingFlag(final int index) {
            headerBuffer.putIntOrdered(headerOffset(index), FLAG_CLEAR);
        }

        boolean isPendingFlagSet(final int index) {
            return headerBuffer.getIntVolatile(headerOffset(index)) != FLAG_CLEAR;
        }

        boolean available(final int index) {
            if (requestSequences[index] == 0) {
                return true;
            }
            if (isPendingFlagSet(index)) {
                return false;
            }
            requestSequences[index] = 0;
            return true;
        }

        boolean isMappedTo(final int index, final long position) {
            return mappedPositions[index] == position && available(index) && mappedPositions[index] == position;
        }

        long consumeMappedAddress(final int index) {
            final long addr = mappedAddresses[index];
            mappedPositions[index] = NULL_POSITION;
            mappedAddresses[index] = NULL_ADDRESS;
            return addr;
        }

        boolean mapAhead(final int index, final long position) {
            if (requestedPositions[index] == position) {
                //NOTE: above read is not fence safe, but it is ok to sometimes return true if it is about to be updated
                return true;
            }
            if (!available(index)) {
                return false;
            }
            if (mappedPositions[index] == position) {
                if (mappedAddresses[index] != NULL_ADDRESS) {
                    return true;
                }
                consumeMappedAddress(index);
            }
            requestedPositions[index] = position;
            setPendingFlag(index);
            return true;
        }

        @Override
        public int execute() {
            final BackgroundUnmapper unmapper = terminateUnmapper;
            if (unmapper == null) {
                final int index = indexOfMinRequestSequence();
                return map(index);
            }
            return unmapAll(unmapper);
        }

        private int unmapAll(final BackgroundUnmapper unmapper) {
            requireNonNull(unmapper);
            final int len = mappedAddresses.length;
            for (int i = 0; i < len; i++) {
                final long pos = mappedPositions[i];
                if (isMappedTo(i, pos)) {
                    final long addr = consumeMappedAddress(i);
                    if (addr > NULL_ADDRESS) {
                        unmapper.unmap(pos, addr);
                    }
                }
            }
            terminateUnmapper = null;
            return len;
        }

        private int indexOfMinRequestSequence() {
            final int len = requestSequences.length;
            long minSequence = Long.MAX_VALUE;
            int index = -1;
            for (int i = 0; i < len; i++) {
                long seq;
                if (isPendingFlagSet(i) && (seq = requestSequences[i]) < minSequence) {
                    minSequence = seq;
                    index = i;
                }
            }
            return index;
        }

        int map(final int index) {
            if (index < 0) {
                return 0;
            }
            final int regionSize = this.regionSize;
            final long req = requestedPositions[index];
            final long pos = mappedPositions[index];
            final long addr = mappedAddresses[index];
            requestSequences[index] = NULL_SEQUENCE;
            requestedPositions[index] = NULL_POSITION;
            if (pos != req) {
                mappedPositions[index] = NULL_POSITION;
                mappedAddresses[index] = NULL_ADDRESS;
                if (addr != NULL_ADDRESS) {
                    assert pos != NULL_POSITION;
                    fileMapper.unmap(addr, pos, regionSize);
                } else {
                    assert pos == NULL_POSITION;
                }
                if (req != NULL_POSITION) {
                    final long mappedAddr = fileMapper.map(req, regionSize);
                    mappedAddresses[index] = Math.max(mappedAddr, NULL_ADDRESS);
                    mappedPositions[index] = req;
                }
            }
            clearPendingFlag(index);
            return 1;
        }

        void stop(final BackgroundUnmapper terminateUnmapper) {
            this.terminateUnmapper = requireNonNull(terminateUnmapper);
        }

        boolean await(final long timeoutMillis) {
            long end = System.currentTimeMillis() + timeoutMillis;
            while (terminateUnmapper != null) {
                if (System.currentTimeMillis() > end) {
                    return false;
                }
                if (!sleep(20)) {
                    end = Long.MIN_VALUE;//make terminate immediately;
                }
            }
            return true;
        }
    }

    private static final class BackgroundUnmapper implements Recurring {
        enum Termination {
            STOP_GRACEFULLY,
            STOP_IMMEDIATELY,
            STOPPED
        }
        private static final int PRODUCER_OFFSET = CACHE_LINE_LENGTH;
        private static final int CONSUMER_OFFSET = 3 * CACHE_LINE_LENGTH;
        private static final int HEADER_LENGTH = 5 * CACHE_LINE_LENGTH;
        static final int RECORD_LENGTH = Long.BYTES + Long.BYTES;
        static final int RECORD_SHIFT = 4;
        final FileMapper fileMapper;
        final int regionSize;
        final int ringMask;
        final AtomicBuffer buffer;//header, then pos/addr pairs
        final AtomicReference<Termination> termination = new AtomicReference<>();

        BackgroundUnmapper(final FileMapper fileMapper, final int regionSize, final int unmapCacheSize) {
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
            final int ixProducer = buffer.getIntVolatile(PRODUCER_OFFSET);
            final int ixConsumer = buffer.getInt(CONSUMER_OFFSET);
            if (ixProducer != ixConsumer) {
                final int ixConsumerNew = ringIndex(ixConsumer + 1);
                final int offset = offset(ixConsumer);
                final long pos = buffer.getLong(offset);
                final long addr = buffer.getLong(offset + Long.BYTES);
                buffer.setMemory(offset, Long.BYTES + Long.BYTES, (byte)0);
                buffer.putIntOrdered(CONSUMER_OFFSET, ixConsumerNew);
                fileMapper.unmap(addr, pos, regionSize);
                return 1;
            }
            if (termination.get() != null) {
                termination.set(Termination.STOPPED);
            }
            return 0;
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

        void stopGracefully() {
            termination.compareAndSet(null, Termination.STOP_GRACEFULLY);
        }

        void stopImmediately() {
            if (!termination.compareAndSet(null, Termination.STOP_IMMEDIATELY)) {
                termination.compareAndSet(Termination.STOP_GRACEFULLY, Termination.STOP_IMMEDIATELY);
            }
        }

        boolean await(final long timeoutMillis) {
            if (termination.get() == null) {
                throw new IllegalStateException("Must stop before awaiting");
            }
            long end = System.currentTimeMillis() + timeoutMillis;
            while (termination.get() != Termination.STOPPED) {
                if (System.currentTimeMillis() > end) {
                    return false;
                }
                if (!sleep(20)) {
                    end = Long.MIN_VALUE;//make terminate immediately;
                }
            }
            return true;
        }
    }

    private static boolean sleep(final long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (final InterruptedException e) {
            return false;
        }
    }
}
