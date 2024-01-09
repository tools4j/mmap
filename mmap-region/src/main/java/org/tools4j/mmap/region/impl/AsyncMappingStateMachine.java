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

import static java.util.Objects.requireNonNull;
import static org.agrona.UnsafeAccess.UNSAFE;
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
final class AsyncMappingStateMachine extends AsyncMappingStateMachineState implements MutableMappingState {

    private static final long MAPPING_FAILED_FLAG = 0x8000000000000000L;
    private static final long POSITION_MASK = ~MAPPING_FAILED_FLAG;
    private static final long UNMAPPED_VALUE = valueByState(UNMAPPED);
    private static final long CLOSED_VALUE = valueByState(CLOSED);

    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private final AsyncRuntime asyncRuntime;
    private final RegionBuffer regionBuffer = new RegionBuffer();
    private final Recurring asyncRecurring = this::execute;

    AsyncMappingStateMachine(final FileMapper fileMapper,
                             final RegionMetrics regionMetrics,
                             final AsyncRuntime asyncRuntime) {
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.requestedPosition = UNMAPPED_VALUE;
        this.requestedPositionCache = UNMAPPED_VALUE;
        this.mappedPosition = UNMAPPED_VALUE;
        this.mappedPositionCache = UNMAPPED_VALUE;
        this.mappedRegionAddress = NULL_ADDRESS;
        asyncRuntime.register(asyncRecurring);
    }

    private static boolean steadyState(final long requestedPosition, final long mappedPosition) {
        return (requestedPosition == mappedPosition) | (requestedPosition == (POSITION_MASK & mappedPosition));
    }

    private static boolean closedOrClosing(final long requestedPosition) {
        return requestedPosition == CLOSED_VALUE;
    }

    private static boolean mappingFailed(final long requestedPosition, final long mappedPosition) {
        return (requestedPosition != mappedPosition) & (requestedPosition == (POSITION_MASK & mappedPosition));
    }

    private long refreshMappedPosition() {
        final long requestedPos = requestedPositionCache;
        long mappedPos = mappedPositionCache;
        if (steadyState(requestedPos, mappedPos)) {
            return mappedPos;
        }
        mappedPos = mappedPosition;
        if (steadyState(requestedPos, mappedPos)) {
            mappedPositionCache = mappedPos;
            return mappedPos;
        }
        return NULL_POSITION;
    }

    @Override
    public long position() {
        final long mappedPos = refreshMappedPosition();
        return mappedPos >= 0 ? mappedPos : NULL_POSITION;
    }

    @Override
    public AtomicBuffer buffer() {
        final RegionBuffer buffer = regionBuffer;
        if (buffer.capacity() == 0) {
            final long mappedPos = refreshMappedPosition();
            if (mappedPos >= 0) {
                final RegionMetrics metrics = regionMetrics;
                final int offset = metrics.regionOffset(mappedPos);
                buffer.wrapInternal(mappedRegionAddress + offset, metrics.regionSize() - offset);
            }
        }
        return buffer;
    }

    @Override
    public RegionState state() {
        final long requestedPos = requestedPositionCache;
        final long mappedPos = refreshMappedPosition();
        if (requestedPos == mappedPos) {
            //MAPPED or UNMAPPED or CLOSED
            return requestedPos >= 0 ? MAPPED : stateByValue(requestedPos);
        }
        if (requestedPos == CLOSED_VALUE) {
            return CLOSING;
        }
        return mappingFailed(requestedPos, mappedPos) ? FAILED : REQUESTED;
    }

    @Override
    public boolean requestLocal(final long position) {
        validPosition(position);
        final long requestedPosition = requestedPositionCache;
        if (requestedPosition < 0) {
            //UNMAPPED, CLOSING or CLOSED
            return false;
        }
        final long mappedPosition = refreshMappedPosition();
        if (mappingFailed(requestedPosition, mappedPosition)) {
            return false;
        }
        if (position == requestedPosition) {
            //same as already requested or mapped
            return true;
        }
        if (requestedPosition != mappedPosition) {
            //mapping still in progress
            return false;
        }
        final RegionMetrics metrics = regionMetrics;
        final long newRegionPosition = metrics.regionPosition(position);
        final long prevRegionPosition = metrics.regionPosition(mappedPosition);
        if (newRegionPosition == prevRegionPosition) {
            final int regionSize = metrics.regionSize();
            final int offset = metrics.regionOffset(position);
            regionBuffer.wrapInternal(mappedRegionAddress + offset, regionSize - offset);
            requestedPositionCache = position;
            mappedPositionCache = position;
            UNSAFE.putOrderedLong(this, REQUESTED_POSITION_OFFSET, position);
            //NOTE: mapped position will be updated async later
            return true;
        }
        return false;
    }

    @Override
    public boolean request(final long position) {
        if (requestLocal(position)) {
            return true;
        }
        final long requestedPosition = requestedPositionCache;
        if (closedOrClosing(requestedPosition)) {
            return false;
        }
        final long failedPosition = position | MAPPING_FAILED_FLAG;
        final boolean sameMappingFailed = mappedPositionCache == failedPosition;
        regionBuffer.unwrapInternal();
        requestedPositionCache = position;
        mappedPositionCache = NULL_POSITION;
        UNSAFE.putOrderedLong(this, REQUESTED_POSITION_OFFSET, position);

        //NOTE: we need to reset mapped position if it failed, otherwise it will never be re-attempted
        if (sameMappingFailed) {
            UNSAFE.compareAndSwapLong(this, MAPPED_POSITION_OFFSET, failedPosition, NULL_POSITION);
        }
        return true;
    }

    @Override
    public void close() {
        final long requestedPosition = requestedPositionCache;
        if (closedOrClosing(requestedPosition)) {
            return;
        }
        regionBuffer.unwrapInternal();
        requestedPositionCache = CLOSED_VALUE;
        UNSAFE.putOrderedLong(this, REQUESTED_POSITION_OFFSET, CLOSED_VALUE);
    }

    private int execute() {
        final RegionMetrics metrics = regionMetrics;
        final long requestedPos = requestedPosition;
        final long mappedRegionPos = metrics.regionPosition(mappedPosition);
        final long mappedRegionAddr = mappedRegionAddress;
        if (requestedPos == CLOSED_VALUE) {
            return unmapOnClose(mappedRegionPos, mappedRegionAddr);
        }
        if (requestedPos >= 0) {
            if (metrics.regionPosition(requestedPos) == mappedRegionPos) {
                //just update mapped position to new value -- in response to local request
                UNSAFE.putOrderedLong(this, MAPPED_POSITION_OFFSET, requestedPos);
            } else {
                map(requestedPos);
                unmapAfterMap(mappedRegionPos, mappedRegionAddr);
            }
            return 1;
        }
        return 0;
    }

    private void map(final long position) {
        assert position >= 0;
        final RegionMetrics metrics = regionMetrics;
        final long regionPosition = metrics.regionPosition(position);
        try {
            final long address = fileMapper.map(regionPosition, metrics.regionSize());
            if (address >= 0) {
                mappedRegionAddress = address;
                UNSAFE.putOrderedLong(this, MAPPED_POSITION_OFFSET, position);
            } else {
                mappedRegionAddress = NULL_ADDRESS;
                UNSAFE.putOrderedLong(this, MAPPED_POSITION_OFFSET, position | MAPPING_FAILED_FLAG);
            }
        } catch (final Exception e) {
            mappedRegionAddress = NULL_ADDRESS;
            UNSAFE.putOrderedLong(this, MAPPED_POSITION_OFFSET, position | MAPPING_FAILED_FLAG);
        }
    }

    private void unmapAfterMap(final long mappedRegionPos, final long mappedRegionAddr) {
        if (mappedRegionPos >= 0) {
            assert mappedRegionAddr != NULL_ADDRESS;
            fileMapper.unmap(mappedRegionAddr, mappedRegionPos, regionMetrics.regionSize());
        } else {
            assert mappedRegionAddr == NULL_ADDRESS;
        }
    }

    private int unmapOnClose(final long mappedRegionPos, final long mappedRegionAddr) {
        if (mappedRegionPos >= 0) {
            assert mappedRegionAddr != NULL_ADDRESS;
            try {
                fileMapper.unmap(mappedRegionAddr, mappedRegionPos, regionMetrics.regionSize());
            } finally {
                mappedRegionAddress = NULL_ADDRESS;
                UNSAFE.putOrderedLong(this, MAPPED_POSITION_OFFSET, CLOSED_VALUE);
                asyncRuntime.deregister(asyncRecurring);
            }
            return 1;
        }
        assert mappedRegionAddr == NULL_ADDRESS;
        return 0;
    }


    /**
     * Returns a negative position-like value given a region state.  Region state shall not be REQUESTED or MAPPED as we
     * use the position in that case.
     *
     * @param state the region state
     * @return the negative state value
     */
    private static long valueByState(final RegionState state) {
        assert state != REQUESTED && state != MAPPED;
        return -(1 + state.ordinal());
    }

    /**
     * Returns the region state given a negative position-like value.
     *
     * @param value the position-like value
     * @return the region state
     */
    private static RegionState stateByValue(final long value) {
        final long ordinal = -(1 + value);
        return RegionState.valueByOrdinal((int)ordinal);
    }

}
