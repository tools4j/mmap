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

import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.AsyncRegion;
import org.tools4j.mmap.region.api.AsyncRegionState;
import org.tools4j.mmap.region.api.FileMapper;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class AsyncVolatileStateMachineRegion implements AsyncRegion {
    private static final long NULL = -1;

    private final FileMapper fileMapper;
    private final int length;
    private final long timeoutNanos;

    private final UnmappedRegionState unmapped;
    private final MapRequestedRegionState mapRequested;
    private final MappedRegionState mapped;
    private final UnMapRequestedRegionState unmapRequested;

    //@Contended
    private volatile AsyncRegionState currentState;

    private long position = NULL;
    private long address = NULL;
    private long requestedPosition;

    public AsyncVolatileStateMachineRegion(final FileMapper fileMapper,
                                           final int length,
                                           final long timeout,
                                           final TimeUnit timeUnits) {
        this.fileMapper = Objects.requireNonNull(fileMapper);
        this.length = length;
        this.timeoutNanos = timeUnits.toNanos(timeout);

        this.unmapped = new UnmappedRegionState();
        this.mapRequested = new MapRequestedRegionState();
        this.mapped = new MappedRegionState();
        this.unmapRequested = new UnMapRequestedRegionState();
        this.currentState = unmapped;
        if (Integer.bitCount(length) > 1)
            throw new IllegalArgumentException("length must be power of two");
    }

    @Override
    public boolean wrap(final long position, final DirectBuffer source) {
        final int regionOffset = (int) (position & (this.length - 1));
        final long regionStartPosition = position - regionOffset;
        if (awaitMapped(regionStartPosition)) {
            source.wrap(address + regionOffset, this.length - regionOffset);
            return true;
        }
        return false;
    }

    private boolean awaitMapped(final long position) {
        if (!mapped(position)) {
            final long startTimeNanos = System.nanoTime();
            while (!map(position)) {
                if (System.nanoTime() - startTimeNanos >= timeoutNanos)
                    return false;
            }
        }
        return true;
    }

    /**
     * Checks if given position is already mapped.
     * Note: in sequential access it is enough to check if "{@code this.position == position}",
     * and it is less expensive.
     * However, as this implementation is also used for random access, then we need to also
     * do sync "{@code currentState == mapped}", which is more expensive.
     *
     * @param position starting position of a region
     * @return true if region is mapped, false - otherwise
     */
    private boolean mapped(final long position) {
        return currentState == mapped && this.position == position;
    }

    private void awaitUnMapped() {
        if (!unmapped()) {
            final long startTimeNanos = System.nanoTime() + timeoutNanos;
            while (!unmap()) {
                if (System.nanoTime() - startTimeNanos >= timeoutNanos)
                    return;
            }
        }
    }

    private boolean unmapped() {
        return currentState == unmapped;
    }

    @Override
    public void close() {
        awaitUnMapped();
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public boolean processRequest() {
        final AsyncRegionState readState = this.currentState;
        final AsyncRegionState nextState = readState.processRequest();
        if (readState != nextState) {
            this.currentState = nextState;
            return true;
        }
        return false;
    }

    @Override
    public boolean map(final long position) {
        final AsyncRegionState readState = this.currentState;
        final AsyncRegionState nextState = readState.requestMap(position);
        if (readState != nextState)
            this.currentState = nextState;
        return nextState == mapped;
    }

    @Override
    public boolean unmap() {
        final AsyncRegionState readState = this.currentState;
        final AsyncRegionState nextState = readState.requestUnmap();
        if (readState != nextState)
            this.currentState = nextState;
        return nextState == unmapped;
    }

    private final class UnmappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long position) {
            requestedPosition = position;
            return mapRequested;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            return this;
        }
    }

    private final class MapRequestedRegionState implements AsyncRegionState {

        @Override
        public AsyncRegionState requestMap(final long position) {
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            if (address != NULL && position != NULL) {
                fileMapper.unmap(address, position, length);
                address = NULL;
                position = NULL;
            }

            final long mappedAddress = fileMapper.map(requestedPosition, length);
            if (mappedAddress > 0) {
                address = mappedAddress;
                position = requestedPosition;
                requestedPosition = NULL;

                return mapped;

            } else {
                requestedPosition = NULL;
                return unmapped;
            }
        }
    }

    private final class MappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long position) {
            if (AsyncVolatileStateMachineRegion.this.position != position) {
                requestedPosition = position;
                return mapRequested;
            }
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return unmapRequested;
        }

        @Override
        public AsyncRegionState processRequest() {
            return this;
        }
    }

    private final class UnMapRequestedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long position) {
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            if (address != NULL && position != NULL) {
                fileMapper.unmap(address, position, length);
                address = NULL;
                position = NULL;
                requestedPosition = NULL;
            }
            return unmapped;
        }
    }
}
