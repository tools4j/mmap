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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BitUtil.findNextPositivePowerOfTwo;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateGreaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.validatePowerOfTwo;

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
                                     final int mapAhead,
                                     final boolean deferUnmap) {
        this(asyncRuntime,
                baseMapper,
                self -> new RingCacheRegionMapper(self, ringCacheSize, deferUnmap),
                mapAhead);
    }

    public AsyncRunAheadRegionMapper(final AsyncRuntime asyncRuntime,
                                     final DirectRegionMapper baseMapper,
                                     final Function<? super RegionMapper, ? extends RegionMapper> cacheFactory,
                                     final int mapAhead) {
        validateGreaterThanZero("Map ahead", mapAhead);
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.baseMapper = requireNonNull(baseMapper);
        this.cachingMapper = cacheFactory.apply(new AheadMapper());
        this.sharedState = new SharedState(findNextPositivePowerOfTwo(mapAhead));
        this.asyncMapper = startAsyncMapper(baseMapper);
        this.mapAhead = mapAhead;
        this.lastPositionMapped = NULL_POSITION;
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
                    break;
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
                ":cachingMapper=" + cachingMapper +
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
        static final int OWNERSHIP_ENTRY_LENGTH = 3 * CACHE_LINE_LENGTH;//3* is enough, but better a power of 2
        static final int OWNED_BY_REQUESTER = 0;
        static final int OWNED_BY_MAPPER = 1;
        final AtomicBuffer ownershipPadded;
        final long[] requestSequences;
        final long[] requestedPositions;
        final long[] mappedPositions;
        final long[] mappedAddresses;
        final int indexMask;
        long requestSequence;

        SharedState(final int aheadCache) {
            validatePowerOfTwo("Ahead cache size", aheadCache);
            ownershipPadded = new UnsafeBuffer(ByteBuffer.allocateDirect(aheadCache * OWNERSHIP_ENTRY_LENGTH));
            requestSequences = new long[aheadCache];
            requestedPositions = new long[aheadCache];
            mappedPositions = new long[aheadCache];
            mappedAddresses = new long[aheadCache];
            indexMask = aheadCache - 1;
            Arrays.fill(requestSequences, NULL_SEQUENCE);
            Arrays.fill(requestedPositions, NULL_POSITION);
            Arrays.fill(mappedPositions, NULL_POSITION);
            Arrays.fill(mappedAddresses, NULL_ADDRESS);
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
            if (requestSequences[index] == 0) {
                return true;
            }
            if (isOwnedByMapper(index)) {
                return false;
            }
            requestSequences[index] = 0;
            return true;
        }

        boolean isMapped(final int index, final long position) {
            return mappedPositions[index] == position && isOwnedByRequester(index) && mappedPositions[index] == position;
        }

        long consumeAddressIfMapped(final long position) {
            final int index = (int)(position & indexMask);
            if (isMapped(index, position)) {
                final long addr = mappedAddresses[index];
                mappedPositions[index] = NULL_POSITION;
                mappedAddresses[index] = NULL_ADDRESS;
                return addr;
            }
            return ADDRESS_UNAVAILABLE;
        }

        boolean requestMapping(final long position) {
            final int index = (int)(position & indexMask);
            if (requestedPositions[index] == position) {
                //NOTE: above read is not fence safe, but it is ok to sometimes return true if it is about to be updated
                return true;
            }
            if (isOwnedByMapper(index)) {
                return false;
            }
            if (mappedPositions[index] == position) {
                if (mappedAddresses[index] != NULL_ADDRESS) {
                    //already mapped
                    return true;
                }
                //map attempted but NULL_ADDRESS returned, try again
                mappedPositions[index] = NULL_POSITION;
            }
            requestedPositions[index] = position;
            requestSequences[index] = ++requestSequence;
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
            final int len = requestSequences.length;
            long minSequence = Long.MAX_VALUE;
            int index = -1;
            for (int i = 0; i < len; i++) {
                long seq;
                if (isOwnedByMapper(i) && (seq = requestSequences[i]) < minSequence) {
                    minSequence = seq;
                    index = i;
                }
            }
            return index;
        }

        private void map(final FileMapper fileMapper, final int regionSize, final int index) {
            final long req = requestedPositions[index];
            final long pos = mappedPositions[index];
            requestSequences[index] = NULL_SEQUENCE;
            requestedPositions[index] = NULL_POSITION;
            if (pos != req) {
                final long addr = mappedAddresses[index];
                if (addr != NULL_ADDRESS) {
                    assert pos != NULL_POSITION;
                    fileMapper.unmap(pos, addr, regionSize);
                    mappedPositions[index] = NULL_POSITION;
                    mappedAddresses[index] = NULL_ADDRESS;
                } else {
                    assert pos == NULL_POSITION;
                }
                if (req != NULL_POSITION) {
                    final long mappedAddr = fileMapper.map(req, regionSize);
                    mappedAddresses[index] = Math.max(mappedAddr, NULL_ADDRESS);
                    mappedPositions[index] = req;
                }
            }
            assignToRequester(index);
        }

        void unmapAll(final FileMapper fileMapper, final int regionSize) {
            for (int i = 0; i < requestSequences.length; i++) {
                final long pos = mappedPositions[i];
                final long addr = mappedAddresses[i];
                requestSequences[i] = NULL_SEQUENCE;
                requestedPositions[i] = NULL_POSITION;
                mappedPositions[i] = NULL_POSITION;
                mappedAddresses[i] = NULL_ADDRESS;
                if (pos != NULL_POSITION && addr != NULL_ADDRESS) {
                    fileMapper.unmap(pos, addr, regionSize);
                }
            }
        }
    }
}
