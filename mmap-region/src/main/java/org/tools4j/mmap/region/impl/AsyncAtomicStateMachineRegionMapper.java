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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.agrona.IoUtil;

import org.tools4j.mmap.region.api.AsyncRegionMapper;
import org.tools4j.mmap.region.api.FileSizeEnsurer;

public class AsyncAtomicStateMachineRegionMapper extends AbstractRegionMapper implements AsyncRegionMapper {
    private final UnmappedRegionState unmapped;
    private final MapRequestedRegionState mapRequested;
    private final MappedRegionState mapped;
    private final UnMapRequestedRegionState unmapRequested;

    private final AtomicReference<AsyncRegionState> currentState;

    public AsyncAtomicStateMachineRegionMapper(final int regionSize,
                                               final Supplier<? extends FileChannel> fileChannelSupplier,
                                               final FileSizeEnsurer fileSizeEnsurer,
                                               final FileChannel.MapMode mapMode) {
        super(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
        this.unmapped = new UnmappedRegionState();
        this.mapRequested = new MapRequestedRegionState();
        this.mapped = new MappedRegionState();
        this.unmapRequested = new UnMapRequestedRegionState();
        this.currentState = new AtomicReference<>(unmapped);
    }

    @Override
    public boolean processMappingRequests() {
        final AsyncRegionState readState = this.currentState.get();
        final AsyncRegionState nextState = readState.processRequest();
        if (readState != nextState) {
            this.currentState.set(nextState);
            return true;
        }
        return false;
    }

    public long map(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative: " + position);
        }
        final AsyncRegionState readState = this.currentState.get();
        final AsyncRegionState nextState = readState.requestMap(position);
        if (readState != nextState) {
            this.currentState.set(nextState);
        }
        return nextState == mapped ? currentAddress : NULL;
    }

    public boolean unmap() {
        final AsyncRegionState readState = this.currentState.get();
        final AsyncRegionState nextState = readState.requestUnmap();
        if (readState != nextState) {
            this.currentState.set(nextState);
        }
        return nextState == unmapped;
    }
    private final class UnmappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long position) {
            mapRequested.requestedPosition = position;
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
        public AsyncRegionState requestMap(final long position) {
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            return this;
        }

        @Override
        public AsyncRegionState processRequest() {
            if (currentAddress != NULL) {
                IoUtil.unmap(fileChannelSupplier.get(), currentAddress, regionSize);
                currentAddress = NULL;
            }

            if (fileSizeEnsurer.ensureSize(requestedPosition + regionSize)) {
                currentAddress = IoUtil.map(fileChannelSupplier.get(), mapMode, requestedPosition, regionSize);
                currentPosition = requestedPosition;
                requestedPosition = NULL;

                return mapped;
            } else {
                return this;
            }
        }
    }

    private final class MappedRegionState implements AsyncRegionState {
        @Override
        public AsyncRegionState requestMap(final long position) {
            if (AsyncAtomicStateMachineRegionMapper.this.currentPosition != position) {
                mapRequested.requestedPosition = position;
                AsyncAtomicStateMachineRegionMapper.this.currentPosition = NULL;
                return mapRequested;
            }
            return this;
        }

        @Override
        public AsyncRegionState requestUnmap() {
            currentPosition = NULL;
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
            IoUtil.unmap(fileChannelSupplier.get(), currentAddress, regionSize);
            currentAddress = NULL;
            return unmapped;
        }
    }
}
