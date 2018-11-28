/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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

import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import sun.misc.Contended;

import org.tools4j.mmap.region.api.FileSizeEnsurer;

public class AsyncVolatileStateMachineRegion extends AbstractAsyncRegion {
    private final UnmappedRegionState unmapped;
    private final MapRequestedRegionState mapRequested;
    private final MappedRegionState mapped;
    private final UnMapRequestedRegionState unmapRequested;

    @Contended
    private volatile AsyncRegionState currentState;

    public AsyncVolatileStateMachineRegion(final Supplier<? extends FileChannel> fileChannelSupplier,
                                           final IoMapper ioMapper,
                                           final IoUnmapper ioUnmapper,
                                           final FileSizeEnsurer fileSizeEnsurer,
                                           final FileChannel.MapMode mapMode,
                                           final int regionSize,
                                           final long timeout,
                                           final TimeUnit unit) {
        super(fileChannelSupplier, ioMapper, ioUnmapper, fileSizeEnsurer, mapMode, regionSize, timeout, unit);
        this.unmapped = new UnmappedRegionState();
        this.mapRequested = new MapRequestedRegionState();
        this.mapped = new MappedRegionState();
        this.unmapRequested = new UnMapRequestedRegionState();
        this.currentState = unmapped;
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
    public long map(final long regionStartPosition) {
        //assert that regionStartPosition is aligned with length
        final AsyncRegionState readState = this.currentState;
        final AsyncRegionState nextState = readState.requestMap(regionStartPosition);
        if (readState != nextState) {
            this.currentState = nextState;
        }
        return nextState == mapped ? address : NULL;
    }

    @Override
    public boolean unmap() {
        final AsyncRegionState readState = this.currentState;
        final AsyncRegionState nextState = readState.requestUnmap();
        if (readState != nextState) {
            this.currentState = nextState;
        }
        return nextState == unmapped;
    }

    private final class UnmappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long regionStartPosition) {
            mapRequested.requestedPosition = regionStartPosition;
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
        private long requestedPosition;

        @Override
        public AsyncRegionState requestMap(final long regionStartPosition) {
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            if (address != NULL) {
                ioUnmapper.unmap(fileChannelSupplier.get(), address, regionSize);
                address = NULL;
            }

            if (fileSizeEnsurer.ensureSize(requestedPosition + regionSize)) {
                address = ioMapper.map(fileChannelSupplier.get(), mapMode, requestedPosition, regionSize);
                position = requestedPosition;
                requestedPosition = NULL;

                return mapped;
            } else {
                return this;
            }
        }
    }

    private final class MappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long regionStartPosition) {
            if (AsyncVolatileStateMachineRegion.this.position != regionStartPosition) {
                mapRequested.requestedPosition = regionStartPosition;
                AsyncVolatileStateMachineRegion.this.position = NULL;
                return mapRequested;
            }
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            position = NULL;
            return unmapRequested;
        }

        @Override
        public AsyncRegionState processRequest() {
            return this;
        }
    }

    private final class UnMapRequestedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long regionStartPosition) {
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            ioUnmapper.unmap(fileChannelSupplier.get(), address, regionSize);
            address = NULL;
            return unmapped;
        }
    }
}
