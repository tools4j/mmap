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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.RegionState;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.api.RegionState.CLOSED;
import static org.tools4j.mmap.region.api.RegionState.CLOSING;
import static org.tools4j.mmap.region.api.RegionState.FAILED;
import static org.tools4j.mmap.region.api.RegionState.MAPPED;
import static org.tools4j.mmap.region.api.RegionState.REQUESTED;
import static org.tools4j.mmap.region.api.RegionState.UNMAPPED;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

/**
 * Async state machine as described in {@link RegionState}
 */
final class AsyncMappingStateMachine implements MappingStateProvider {

    private static final long UNMAPPED_VALUE = valueByState(UNMAPPED);
    private static final long MAPPED_VALUE = valueByState(MAPPED);
    private static final long FAILED_VALUE = valueByState(FAILED);
    private static final long CLOSED_VALUE = valueByState(CLOSED);
    private static final long CLOSING_VALUE = valueByState(CLOSING);

    private final MappingState mappingState;

    /**
     * Returns the negative region state value given the state.  State shall not be REQUESTED as we use the requested
     * position in that case.
     *
     * @param state the state
     * @return the negative state value
     */
    private static long valueByState(final RegionState state) {
        assert state != REQUESTED;
        return Long.MIN_VALUE + state.ordinal();
    }

    /**
     * Returns the region state given the state value.  If the state value is non-negative, it represents the requested
     * position, hence the resulting state is REQUESTED.  If negative, it is a derivative of the state ordinal.
     *
     * @param stateValue the state value
     * @return the region state
     */
    private static RegionState stateByValue(final long stateValue) {
        if (stateValue >= 0) {
            return REQUESTED;
        }
        final int ordinal = (int)(stateValue - Long.MIN_VALUE);
        return RegionState.valueByOrdinal(ordinal);
    }

    private static boolean isClosedOrClosing(final long stateValue) {
        return stateValue == CLOSED_VALUE || stateValue == CLOSING_VALUE;
    }

    AsyncMappingStateMachine(final FileMapper fileMapper,
                             final RegionMetrics regionMetrics,
                             final AsyncRuntime asyncRuntime) {
        mappingState = new AsyncMappingState(fileMapper, regionMetrics, asyncRuntime);
    }

    @Override
    public MappingState mappingState() {
        return mappingState;
    }

    private static final class AsyncMappingState implements MappingState, Recurring {
        final FileMapper fileMapper;
        final RegionMetrics regionMetrics;
        final AsyncRuntime asyncRuntime;
        final RegionBuffer regionBuffer = new RegionBuffer();
        final AtomicLong state = new AtomicLong(UNMAPPED_VALUE);
        long mappedAddress = NULL_ADDRESS;
        long mappedPosition = NULL_POSITION;

        AsyncMappingState(final FileMapper fileMapper,
                          final RegionMetrics regionMetrics,
                          final AsyncRuntime asyncRuntime) {
            this.fileMapper = requireNonNull(fileMapper);
            this.regionMetrics = requireNonNull(regionMetrics);
            this.asyncRuntime = requireNonNull(asyncRuntime);
            asyncRuntime.register(this);
        }

        @Override
        public long position() {
            return position(state.get());
        }

        long position(final long stateValue) {
            return stateValue == MAPPED_VALUE ? mappedPosition : NULL_POSITION;
        }

        @Override
        public AtomicBuffer buffer() {
            return regionBuffer;
        }

        @Override
        public RegionState state() {
            return stateByValue(state.get());
        }

        @Override
        public boolean requestLocal(final long position) {
            validPosition(position);
            final long curValue = state.get();
            if (position == curValue) {
                //same as already requested
                return true;
            }
            if (isClosedOrClosing(curValue)) {
                return false;
            }
            final long curPosition = position(curValue);
            if (curPosition == NULL_POSITION) {
                return false;
            }
            if (position == curPosition) {
                //same as already mapped
                return true;
            }
            final long newRegionPosition = regionMetrics.regionPosition(position);
            final long prevRegionPosition = regionMetrics.regionPosition(curPosition);
            if (newRegionPosition == prevRegionPosition) {
                final int regionSize = regionMetrics.regionSize();
                final int offset = regionMetrics.regionOffset(position);
                mappedPosition = position;
                regionBuffer.wrapInternal(mappedAddress + offset, regionSize - offset);
                return true;
            }
            return false;
        }

        @Override
        public boolean request(final long position) {
            if (requestLocal(position)) {
                return true;
            }
            long curValue = state.get();
            if (isClosedOrClosing(curValue)) {
                return false;
            }
            while (!state.compareAndSet(curValue, position)) {
                curValue = state.get();
                if (isClosedOrClosing(curValue)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void close() {
            long curValue = state.get();
            if (isClosedOrClosing(curValue)) {
                return;
            }
            long newValue = curValue == UNMAPPED_VALUE ? CLOSED_VALUE : CLOSING_VALUE;
            while (!state.compareAndSet(curValue, newValue)) {
                curValue = state.get();
                if (isClosedOrClosing(curValue)) {
                    return;
                }
                newValue = CLOSING_VALUE;
            }
            if (newValue == CLOSED_VALUE) {
                asyncRuntime.deregister(this);
            }
        }

        @Override
        public int execute() {
            long curValue, newValue;
            long deferredUnmapAddr, deferredUnmapPos;
            do {
                curValue = state.get();
                if (curValue == CLOSING_VALUE) {
                    unmapIfNecessary();
                    deferredUnmapAddr = NULL_ADDRESS;
                    deferredUnmapPos = NULL_POSITION;
                    newValue = CLOSED_VALUE;
                } else if (curValue >= 0) {
                    //NOTE: defer unmapping for better mapping latency
                    deferredUnmapAddr = mappedAddress;
                    deferredUnmapPos = mappedPosition;
                    mappedAddress = NULL_ADDRESS;
                    mappedPosition = NULL_POSITION;
                    newValue = map(curValue);
                } else {
                    return 0;
                }
            } while (!state.compareAndSet(curValue, newValue));
            unmapIfNecessary(deferredUnmapAddr, deferredUnmapPos);
            if (newValue == CLOSED_VALUE) {
                asyncRuntime.deregister(this);
            }
            return 1;
        }

        void unmapIfNecessary() {
            if (mappedAddress != NULL_ADDRESS) {
                regionBuffer.unwrapInternal();
                try {
                    assert mappedPosition >= 0;
                    final long regionPosition = regionMetrics.regionPosition(mappedPosition);
                    fileMapper.unmap(mappedAddress, regionPosition, regionMetrics.regionSize());
                } finally {
                    mappedAddress = NULL_ADDRESS;
                    mappedPosition = NULL_POSITION;
                }
            }
        }
        void unmapIfNecessary(final long addr, final long pos) {
            if (addr != NULL_ADDRESS) {
                assert pos >= 0;
                final long regionPosition = regionMetrics.regionPosition(pos);
                fileMapper.unmap(addr, regionPosition, regionMetrics.regionSize());
            }
        }

        long map(final long position) {
            try {
                assert position >= 0;
                final int regionSize = regionMetrics.regionSize();
                final long regionPosition = regionMetrics.regionPosition(position);
                final int offset = regionMetrics.regionOffset(position);
                final long address = fileMapper.map(regionPosition, regionSize);
                if (address > 0) {
                    mappedAddress = address;
                    mappedPosition = position;
                    regionBuffer.wrapInternal(mappedAddress + offset, regionSize - offset);
                    return MAPPED_VALUE;
                }
                //NOTE: this can e.g. happen for attempted read mapping when section does not exist yet
                return FAILED_VALUE;
            } catch (final Exception e) {
                mappedAddress = NULL_ADDRESS;
                mappedPosition = NULL_POSITION;
                return FAILED_VALUE;
            }
        }
    }

}
