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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.RegionState;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.api.RegionState.CLOSED;
import static org.tools4j.mmap.region.api.RegionState.ERROR;
import static org.tools4j.mmap.region.api.RegionState.MAPPED;
import static org.tools4j.mmap.region.api.RegionState.UNMAPPED;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

final class SyncMappingStateMachine implements MappingStateProvider {
    private final MappingState state;

    public SyncMappingStateMachine(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
        state = new SyncState(fileMapper, regionMetrics);
    }

    @Override
    public MappingState mappingState() {
        return state;
    }

    private static final class SyncState implements MappingState {
        final FileMapper fileMapper;
        final RegionMetrics regionMetrics;
        final RegionBuffer buffer = new RegionBuffer();
        long mappedAddress = NULL_ADDRESS;
        long mappedPosition = NULL_POSITION;
        RegionState state = UNMAPPED;

        SyncState(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
            this.fileMapper = requireNonNull(fileMapper);
            this.regionMetrics = requireNonNull(regionMetrics);
        }

        @Override
        public long position() {
            return mappedPosition;
        }

        @Override
        public AtomicBuffer buffer() {
            return buffer;
        }

        @Override
        public RegionState state() {
            return state;
        }

        @Override
        public boolean requestLocal(final long position) {
            validPosition(position);
            if (state != MAPPED) {
                return false;
            }
            final long reqRegionPosition = regionMetrics.regionPosition(position);
            final long curRegionPosition = regionMetrics.regionPosition(mappedPosition);
            if (reqRegionPosition == curRegionPosition) {
                final int regionSize = regionMetrics.regionSize();
                final int offset = regionMetrics.regionOffset(position);
                mappedPosition = position;
                buffer.wrapInternal(mappedAddress + offset, regionSize - offset);
                return true;
            }
            return false;
        }

        private void unmapIfNecessary() {
            final long addr = mappedAddress;
            final long pos = mappedPosition;
            if (addr != NULL_ADDRESS) {
                assert pos != NULL_POSITION;
                buffer.unwrapInternal();
                mappedAddress = NULL_ADDRESS;
                mappedPosition = NULL_POSITION;
                final long regionPosition = regionMetrics.regionPosition(pos);
                fileMapper.unmap(addr, regionPosition, regionMetrics.regionSize());
            } else {
                assert pos == NULL_POSITION;
            }
        }

        @Override
        public boolean request(final long requestedPosition) {
            if (requestLocal(requestedPosition)) {
                return true;
            }
            if (state == CLOSED) {
                return false;
            }
            try {
                unmapIfNecessary();
                final int regionSize = regionMetrics.regionSize();
                final long regionPosition = regionMetrics.regionPosition(requestedPosition);
                final int offset = regionMetrics.regionOffset(requestedPosition);
                final long addr = fileMapper.map(regionPosition, regionSize);
                if (addr > 0) {
                    mappedAddress = addr;
                    mappedPosition = requestedPosition;
                    buffer.wrapInternal(addr + offset, regionSize - offset);
                    state = MAPPED;
                    return true;
                }
            } catch (final Exception exception) {
                //ignore, handle below
            }
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            state = ERROR;
            return false;
        }

        @Override
        public void close() {
            if (state != CLOSED) {
                unmapIfNecessary();
                state = CLOSED;
            }
        }

        @Override
        public String toString() {
            return state().name();
        }
    }

}
