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
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.agrona.BufferUtil.allocateDirectAligned;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateAheadMappingCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateGreaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.validatePowerOfTwo;

/**
 * A region mapper that attempts to run ahead and pre-map regions that are likely going to be requested next.  Regions
 * are pre-mapped if sequential forward or backward requests are detected, but not for random-access patterns.
 * <p>
 * The actual mapping and unmapping operations are delegated to another underlying region mapper (usually either a
 * {@link SyncRegionMapper} or a {@link SyncRegionMapperAsyncUnmapper}).  A cache is used to store mapped positions
 */
@Unsafe
final class AsyncRunAheadRegionMapper implements RegionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncRunAheadRegionMapper.class);
    private static final long ADDRESS_UNAVAILABLE = NULL_ADDRESS - 1;

    private final AsyncRuntime asyncRuntime;
    private final RegionMapper baseMapper;
    private final RegionMapper cachingMapper;
    private final SharedState sharedState;
    private final AsyncMapper asyncMapper;
    private final int mapAhead;
    private long lastPositionMapped;
    private long asyncStats;
    private long syncStats;
    private long busyStats;

    public AsyncRunAheadRegionMapper(final AsyncRuntime asyncRuntime,
                                     final DirectRegionMapper baseMapper,
                                     final int ringCacheSize,
                                     final boolean deferUnmap,
                                     final int mapAhead,
                                     final int mapAheadCacheSize) {
        this(asyncRuntime,
                baseMapper,
                self -> new RingCacheRegionMapper(self, ringCacheSize, deferUnmap),
                mapAhead, mapAheadCacheSize);
    }

    public AsyncRunAheadRegionMapper(final AsyncRuntime asyncRuntime,
                                     final DirectRegionMapper baseMapper,
                                     final Function<? super RegionMapper, ? extends RegionMapper> cacheFactory,
                                     final int mapAhead,
                                     final int mapAheadCacheSize) {
        requireNonNull(asyncRuntime);
        requireNonNull(baseMapper);
        requireNonNull(cacheFactory);
        validateGreaterThanZero("Map ahead", mapAhead);
        final int cacheSize = mapAheadCacheSize(mapAhead, mapAheadCacheSize);
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.baseMapper = requireNonNull(baseMapper);
        this.cachingMapper = cacheFactory.apply(new AheadMapper());
        this.sharedState = new SharedState(cacheSize, baseMapper.regionSize());
        this.asyncMapper = startAsyncMapper(baseMapper);
        this.mapAhead = mapAhead;
        this.lastPositionMapped = NULL_POSITION;
    }

    private static int mapAheadCacheSize(final int mapAhead, final int mapAheadCacheSize) {
        if (mapAheadCacheSize <= 0) {
            return findNextPositivePowerOfTwo(mapAhead);
        }
        validateAheadMappingCacheSize(mapAheadCacheSize);
        if (mapAhead > mapAheadCacheSize) {
            throw new IllegalArgumentException("Map-ahead cache size cannot be less than map-ahead value " +
                    mapAhead + ": " + mapAheadCacheSize);
        }
        return mapAheadCacheSize;
    }

    private AsyncMapper startAsyncMapper(final DirectRegionMapper baseMapper) {
        final AsyncMapper mapper = new AsyncMapper(baseMapper, sharedState);
        asyncRuntime.register(mapper);
        return mapper;
    }

    @Override
    public AccessMode accessMode() {
        return baseMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return baseMapper.regionMetrics();
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        return cachingMapper.mapInternal(position, regionSize);
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return cachingMapper.isMappedInCache(position);
    }

    private void mapAhead(final long position, final int regionSize) {
        assert position >= 0;
        final int mapAhead = this.mapAhead;
        if (mapAhead <= 0) {
            return;
        }
        final long lastPosMapped = lastPositionMapped;
        if (position != lastPosMapped) {
            lastPositionMapped = position;
            long increment;
            if (position == lastPosMapped + regionSize || (position == 0 && lastPosMapped == NULL_POSITION)) {
                //sequential forward access
                increment = regionSize;
            } else if (position == lastPosMapped - regionSize || (position > 0 && lastPosMapped == NULL_POSITION)) {
                //sequential backward access
                increment = -regionSize;
            } else {
                //else: random access pattern, we don't try to predict the next access
                return;
            }
            //noinspection UnnecessaryLocalVariable
            final SharedState shared = sharedState;
            long nextPosition = position;
            for (int i = 0; i < mapAhead && (nextPosition += increment) >= 0; i++) {
                if (!isMappedInCache(nextPosition) && !shared.requestMapping(nextPosition)) {
                    busyStats++;
                }
            }
        }
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        cachingMapper.unmapInternal(position, address, regionSize);
    }

    @Override
    public boolean isClosed() {
        return baseMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            cachingMapper.close();
            baseMapper.close();
            LOGGER.info("Closed {}.", this);
        }
    }

    @Override
    public String toString() {
        return "AsyncRunAheadRegionMapper" +
                ":mapAhead=" + mapAhead +
                "|cachingMapper=" + cachingMapper +
                "|statistics=" +
                "(async=" + asyncStats +
                "|sync=" + syncStats +
                "|busy=" + busyStats +
                ")";
    }

    private final class AheadMapper implements RegionMapper {
        @Override
        public AccessMode accessMode() {
            return AsyncRunAheadRegionMapper.this.accessMode();
        }

        @Override
        public long mapInternal(final long position, final int regionSize) {
            long addr = sharedState.consumeAddressIfMapped(position);
            if (addr >= NULL_ADDRESS) {
                asyncStats++;
            }
            if (addr <= NULL_ADDRESS) {
                syncStats++;
                addr = baseMapper.mapInternal(position, regionSize);
                if (addr == NULL_ADDRESS) {
                    return NULL_ADDRESS;
                }
            }
            mapAhead(position, regionSize);
            return addr;
        }

        @Override
        public void unmapInternal(final long position, final long address, final int regionSize) {
            baseMapper.unmapInternal(position, address, regionSize);
        }

        @Override
        public boolean isMappedInCache(final long position) {
            return false;
        }

        @Override
        public boolean isClosed() {
            return baseMapper.isClosed();
        }

        @Override
        public void close() {
            if (!isClosed()) {
                asyncMapper.stopAndWait();
                asyncRuntime.deregister(asyncMapper);
                sharedState.unmapAll(asyncMapper.fileMapper, asyncMapper.regionSize);
                baseMapper.close();
            }
        }

        @Override
        public RegionMetrics regionMetrics() {
            return AsyncRunAheadRegionMapper.this.regionMetrics();
        }

        @Override
        public String toString() {
            return "AheadMapper:baseMapper=" + baseMapper;
        }
    }

    private static final class AsyncMapper implements Recurring {
        final FileMapper fileMapper;
        final int regionSize;
        final SharedState sharedState;
        final Timeout closeTimeout = new Timeout();

        AsyncMapper(final DirectRegionMapper regionMapper, final SharedState sharedState) {
            this.fileMapper = regionMapper.fileMapper();
            this.regionSize = regionMapper.regionSize();
            this.sharedState = requireNonNull(sharedState);
        }

        @Override
        public int execute() {
            if (closeTimeout.isStoppedOrStopping()) {
                closeTimeout.stopped();
                return 0;
            }
            return sharedState.mapNext(fileMapper, regionSize);
        }

        void stopAndWait() {
            closeTimeout.stopAndWait();
        }
    }

    private static final class SharedState {
        static final long NULL_SEQUENCE = 0;
        static final int OWNERSHIP_VALUE_OFFSET = CACHE_LINE_LENGTH;
        static final int OWNERSHIP_ENTRY_LENGTH = 4 * CACHE_LINE_LENGTH;//3* is enough, but better a power of 2
        static final int OWNED_BY_REQUESTER = 0;
        static final int OWNED_BY_MAPPER = 1;
        static final int REQ_SEQUENCE_OFFSET = 0;
        static final int REQ_POSITION_OFFSET = 1;
        static final int MAP_POSITION_OFFSET = 2;
        static final int MAP_ADDRESS_OFFSET = 3;
        static final int VALUES_PER_RECORD = 4;
        static final int SHIFT_PER_RECORD = 2;
        final AtomicBuffer ownershipPadded;
        final long[] recordValues;
        final int indexMask;
        final int regionSizeShift;
        long requestSequence;

        SharedState(final int aheadCache, final int regionSize) {
            validatePowerOfTwo("Ahead cache size", aheadCache);
            validatePowerOfTwo("Region size", regionSize);
            ownershipPadded = new UnsafeBuffer(allocateDirectAligned(aheadCache * OWNERSHIP_ENTRY_LENGTH, CACHE_LINE_LENGTH));
            recordValues = new long[aheadCache * VALUES_PER_RECORD];
            indexMask = aheadCache - 1;
            regionSizeShift = Integer.SIZE - Integer.numberOfLeadingZeros(regionSize - 1);
            for (int i = 0; i < aheadCache; i++) {
                recordValues[recordValueIndex(i, REQ_SEQUENCE_OFFSET)] = NULL_SEQUENCE;
                recordValues[recordValueIndex(i, REQ_POSITION_OFFSET)] = NULL_POSITION;
                recordValues[recordValueIndex(i, MAP_POSITION_OFFSET)] = NULL_POSITION;
                recordValues[recordValueIndex(i, MAP_ADDRESS_OFFSET)] = NULL_ADDRESS;
            }
        }

        int ownershipValueOffset(final int index) {
            return OWNERSHIP_VALUE_OFFSET + OWNERSHIP_ENTRY_LENGTH * index;
        }

        void assignToMapper(final int index) {
            ownershipPadded.putIntOrdered(ownershipValueOffset(index), OWNED_BY_MAPPER);
        }

        void assignToRequester(final int index) {
            ownershipPadded.putIntOrdered(ownershipValueOffset(index), OWNED_BY_REQUESTER);
        }

        boolean isOwnedByMapper(final int index) {
            return ownershipPadded.getIntVolatile(ownershipValueOffset(index)) == OWNED_BY_MAPPER;
        }

        boolean isOwnedByRequester(final int index) {
            if (recordValues[recordValueIndex(index, REQ_SEQUENCE_OFFSET)] == NULL_SEQUENCE) {
                return true;
            }
            if (isOwnedByMapper(index)) {
                return false;
            }
            recordValues[recordValueIndex(index, REQ_SEQUENCE_OFFSET)] = NULL_SEQUENCE;
            recordValues[recordValueIndex(index, REQ_POSITION_OFFSET)] = NULL_POSITION;
            return true;
        }

        boolean isMapped(final int index, final long position) {
            final int mappedPosIx = recordValueIndex(index, MAP_POSITION_OFFSET);
            return recordValues[mappedPosIx] == position && isOwnedByRequester(index) && recordValues[mappedPosIx] == position;
        }

        private int indexForPosition(final long position) {
            return (int)(indexMask & (position >>> regionSizeShift));
        }

        private static int recordValueIndex(final int index, final int offset) {
            return (index << SHIFT_PER_RECORD) + offset;
        }

        long consumeAddressIfMapped(final long position) {
            final int index = indexForPosition(position);
            if (isMapped(index, position)) {
                final long addr = recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)];
                recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)] = NULL_POSITION;
                recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)] = NULL_ADDRESS;
                return addr;
            }
            return ADDRESS_UNAVAILABLE;
        }

        boolean requestMapping(final long position) {
            final int index = indexForPosition(position);
            if (recordValues[recordValueIndex(index, REQ_POSITION_OFFSET)] == position) {
                return true;
            }
            if (isOwnedByMapper(index)) {
                return false;
            }
            if (recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)] == position) {
                if (recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)] != NULL_ADDRESS) {
                    //already mapped
                    return true;
                }
                //map attempted but NULL_ADDRESS returned, try again
                recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)] = NULL_POSITION;
            }
            recordValues[recordValueIndex(index, REQ_POSITION_OFFSET)] = position;
            recordValues[recordValueIndex(index, REQ_SEQUENCE_OFFSET)] = ++requestSequence;
            assignToMapper(index);
            return true;
        }

        int mapNext(final FileMapper fileMapper, final int regionSize) {
            final int index = indexOfMinRequestSequence();
            if (index >= 0) {
                map(fileMapper, regionSize, index);
                return 1;
            }
            return 0;
        }

        private int indexOfMinRequestSequence() {
            final int len = recordValues.length >>> SHIFT_PER_RECORD;
            long minSequence = Long.MAX_VALUE;
            int index = -1;
            for (int i = 0; i < len; i++) {
                long seq;
                if (isOwnedByMapper(i) && (seq = recordValues[recordValueIndex(i, REQ_SEQUENCE_OFFSET)]) < minSequence) {
                    minSequence = seq;
                    index = i;
                }
            }
            return index;
        }

        private void map(final FileMapper fileMapper, final int regionSize, final int index) {
            final long req = recordValues[recordValueIndex(index, REQ_POSITION_OFFSET)];
            final long pos = recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)];
            if (pos != req) {
                final long addr = recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)];
                if (addr != NULL_ADDRESS) {
                    assert pos != NULL_POSITION;
                    fileMapper.unmap(pos, addr, regionSize);
                    recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)] = NULL_POSITION;
                    recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)] = NULL_ADDRESS;
                } else {
                    assert pos == NULL_POSITION;
                }
                if (req != NULL_POSITION) {
                    final long mappedAddr = fileMapper.map(req, regionSize);
                    recordValues[recordValueIndex(index, MAP_ADDRESS_OFFSET)] = Math.max(mappedAddr, NULL_ADDRESS);
                    recordValues[recordValueIndex(index, MAP_POSITION_OFFSET)] = req;
                }
            }
            assignToRequester(index);
        }

        void unmapAll(final FileMapper fileMapper, final int regionSize) {
            final int len = recordValues.length >>> SHIFT_PER_RECORD;
            for (int i = 0; i < len; i++) {
                final long pos = recordValues[recordValueIndex(i, MAP_POSITION_OFFSET)];
                final long addr = recordValues[recordValueIndex(i, MAP_ADDRESS_OFFSET)];
                recordValues[recordValueIndex(i, REQ_SEQUENCE_OFFSET)] = NULL_SEQUENCE;
                recordValues[recordValueIndex(i, REQ_POSITION_OFFSET)] = NULL_POSITION;
                recordValues[recordValueIndex(i, MAP_POSITION_OFFSET)] = NULL_POSITION;
                recordValues[recordValueIndex(i, MAP_ADDRESS_OFFSET)] = NULL_ADDRESS;
                if (pos != NULL_POSITION && addr != NULL_ADDRESS) {
                    fileMapper.unmap(pos, addr, regionSize);
                }
            }
        }
    }
}
