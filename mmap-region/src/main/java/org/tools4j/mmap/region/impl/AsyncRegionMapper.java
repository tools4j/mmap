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
package org.tools4j.mmap.region.impl;

import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;
import static org.agrona.UnsafeAccess.UNSAFE;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Buffers.unwrap;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

public final class AsyncRegionMapper implements RegionMapper {
    private final AsyncRuntime asyncRuntime;
    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private final SharedValues sharedValues;
    private static final UnmappedState UNMAPPED_STATE = new UnmappedState();
    private static final RequestedState REQUESTED_STATE = new RequestedState();
    private static final MappedState MAPPED_STATE = new MappedState();
    private static final ClosingState CLOSING_STATE = new ClosingState();
    private static final ClosedState CLOSED_STATE = new ClosedState();
    private final Recurring recurring = this::executeAsync;
    private final Runnable closeFinalizer;

    public AsyncRegionMapper(final AsyncRuntime asyncRuntime,
                             final FileMapper fileMapper,
                             final RegionMetrics regionMetrics,
                             final Runnable closeFinalizer) {
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
        this.sharedValues = new SharedValues(regionMetrics.regionSize(), UNMAPPED_STATE);
        this.closeFinalizer = closeFinalizer(closeFinalizer);
        asyncRuntime.register(recurring);
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    private int executeAsync() {
        final SharedValues shared = sharedValues;
        final AsyncMappingState readState = shared.mappingState;//intentional no access to cached state
        final AsyncMappingState nextState = readState.processRequest(fileMapper, closeFinalizer, shared);
        if (readState != nextState) {
            shared.putOrderedState(nextState);
            return 1;
        }
        return 0;
    }

    private Runnable closeFinalizer(final Runnable closeFinalizer) {
        requireNonNull(closeFinalizer);
        return () -> {
            asyncRuntime.deregister(recurring);
            closeFinalizer.run();
        };
    }

    @Override
    public int map(final long position, final DirectBuffer buffer) {
        validPosition(position);
        final RegionMetrics metrics = regionMetrics;
        final SharedValues shared = sharedValues;
        final long regionPosition = metrics.regionPosition(position);
        final int offset = metrics.regionOffset(position);
        if (shared.mapped(regionPosition)) {
            return MAPPED_STATE.wrap(regionPosition, buffer, offset, shared);
        }
        if (shared.requested(regionPosition)) {
            return PROCESSING;
        }
        return mapAndWrap(regionPosition, buffer, offset, shared);
    }

    private int mapAndWrap(final long regionPosition,
                           final DirectBuffer buffer,
                           final int offset,
                           final SharedValues shared) {
        final AsyncMappingState readState = shared.mappingState();
        final AsyncMappingState nextState = readState.requestMap(regionPosition, shared);
        if (readState != nextState) {
            shared.putOrderedState(nextState);
        }
        return nextState.wrap(regionPosition, buffer, offset, shared);
    }

    @Override
    public void close(final long maxWaitMillis) {
        if (tryClose()) {
            return;
        }
        final long start = System.currentTimeMillis();
        do {
            if (tryClose()) {
                return;
            } else {
                LockSupport.parkNanos(20_000);
            }
        } while (System.currentTimeMillis() - start <= maxWaitMillis);
        throw new IllegalStateException("Not closed or closing after " + maxWaitMillis + "ms");
    }

    private boolean tryClose() {
        final SharedValues shared = sharedValues;
        final AsyncMappingState readState = shared.mappingState();
        final AsyncMappingState nextState = readState.requestClose(closeFinalizer, shared);
        if (readState != nextState) {
            shared.putOrderedState(nextState);
            return true;
        }
        return isClosedOrClosing(readState);
    }

    @Override
    public boolean isClosed() {
        return isClosedOrClosing(sharedValues.mappingState());
    }

    private static final class UnmappedState implements AsyncMappingState {
        @Override
        public AsyncMappingState requestMap(final long regionPosition, final SharedValues shared) {
            shared.requestedPosition = regionPosition;
            return REQUESTED_STATE;
        }

        @Override
        public AsyncMappingState requestClose(final Runnable closeFinalizer, final SharedValues shared) {
            closeFinalizer.run();
            return CLOSED_STATE;
        }

        @Override
        public AsyncMappingState processRequest(final FileMapper fileMapper, final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }

        @Override
        public int wrap(final long regionPosition, final DirectBuffer buffer, final int offset, final SharedValues shared) {
            return FAILED;
        }
    }

    private static final class RequestedState implements AsyncMappingState {
        @Override
        public AsyncMappingState requestMap(final long regionPosition, final SharedValues shared) {
            return this;
        }

        @Override
        public AsyncMappingState requestClose(final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }

        @Override
        public AsyncMappingState processRequest(final FileMapper fileMapper, final Runnable closeFinalizer, final SharedValues shared) {
            final int size = shared.regionSize;
            final long addr = shared.address;
            final long pos = shared.position;
            final long req = shared.requestedPosition;
            if (addr != NULL_ADDRESS && pos != NULL_POSITION) {
                fileMapper.unmap(addr, pos, size);
                shared.address = NULL_ADDRESS;
                shared.position = NULL_POSITION;
            }
            final long mappedAddress = fileMapper.map(req, size);
            shared.requestedPosition = NULL_POSITION;
            if (mappedAddress > 0) {
                shared.address = mappedAddress;
                shared.position = req;
                return MAPPED_STATE;
            } else {
                return UNMAPPED_STATE;
            }
        }

        @Override
        public int wrap(final long regionPosition, final DirectBuffer buffer, final int offset, final SharedValues shared) {
            return shared.requestedPosition == regionPosition ? PROCESSING : BUSY;
        }
    }

    private static final class MappedState implements AsyncMappingState {
        private DirectBuffer lastWrapped;

        private void unwrapLastWrapped(final SharedValues shared) {
            final DirectBuffer last = lastWrapped;
            lastWrapped = null;
            if (last != null) {
                unwrap(last, shared.address, shared.regionSize);
            }
        }

        @Override
        public AsyncMappingState requestMap(final long regionPosition, final SharedValues shared) {
            if (shared.position != regionPosition) {
                shared.requestedPosition = regionPosition;
                unwrapLastWrapped(shared);
                return REQUESTED_STATE;
            }
            return this;
        }

        @Override
        public AsyncMappingState requestClose(final Runnable closeFinalizer, final SharedValues shared) {
            unwrapLastWrapped(shared);
            return CLOSING_STATE;
        }

        @Override
        public AsyncMappingState processRequest(final FileMapper fileMapper, final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }

        @Override
        public int wrap(final long regionPosition, final DirectBuffer buffer, final int offset, final SharedValues shared) {
            final DirectBuffer last = lastWrapped;
            final int len = Buffers.wrap(buffer, last, shared.address, offset, shared.regionSize);
            if (last != buffer) {
                lastWrapped = buffer;
            }
            return len;
        }
    }

    private static final class ClosingState implements AsyncMappingState {
        @Override
        public AsyncMappingState requestMap(final long regionPosition, final SharedValues shared) {
            return this;
        }

        @Override
        public AsyncMappingState requestClose(final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }

        @Override
        public AsyncMappingState processRequest(final FileMapper fileMapper, final Runnable closeFinalizer, final SharedValues shared) {
            if (shared.address != NULL_ADDRESS && shared.position != NULL_POSITION) {
                fileMapper.unmap(shared.address, shared.position, shared.regionSize);
                shared.address = NULL_ADDRESS;
                shared.position = NULL_POSITION;
                shared.requestedPosition = NULL_POSITION;
            }
            closeFinalizer.run();
            return CLOSED_STATE;
        }

        @Override
        public int wrap(final long regionPosition, final DirectBuffer buffer, final int offset, final SharedValues shared) {
            return CLOSED;
        }
    }

    private static final class ClosedState implements AsyncMappingState {
        @Override
        public AsyncMappingState requestMap(final long regionPosition, final SharedValues shared) {
            return this;
        }
        @Override
        public AsyncMappingState requestClose(final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }
        @Override
        public AsyncMappingState processRequest(final FileMapper fileMapper, final Runnable closeFinalizer, final SharedValues shared) {
            return this;
        }

        @Override
        public int wrap(final long regionPosition, final DirectBuffer buffer, final int offset, final SharedValues shared) {
            return CLOSED;
        }
    }

    @SuppressWarnings("unused")
    private static abstract class AbstractSharedPadding1 {
        byte p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
        byte p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
        byte p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
        byte p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;
    }

    private static abstract class AbstractSharedValues extends AbstractSharedPadding1 {
        //@Contended
        volatile AsyncMappingState mappingState;
        long position = NULL_POSITION;
        long address = NULL_ADDRESS;
        long requestedPosition;
    }

    @SuppressWarnings("unused")
    private static abstract class AbstractSharedPadding2 extends AbstractSharedValues {
        byte p064, p065, p066, p067, p068, p069, p070, p071, p072, p073, p074, p075, p076, p077, p078, p079;
        byte p080, p081, p082, p083, p084, p085, p086, p087, p088, p089, p090, p091, p092, p093, p094, p095;
        byte p096, p097, p098, p099, p100, p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111;
        byte p112, p113, p114, p115, p116, p117, p118, p119, p120, p121, p122, p123, p124, p125, p126, p127;
    }

    private static final class SharedValues extends AbstractSharedPadding2 {
        private static final long STATE_OFFSET;
        final int regionSize;
        private AsyncMappingState cachedMappingState;


        SharedValues(final int regionSize, final UnmappedState unmapped) {
            this.regionSize = regionSize;
            this.mappingState = unmapped;
            this.cachedMappingState = unmapped;
        }

        AsyncMappingState mappingState() {
            if (cachedMappingState != null) {
                return cachedMappingState;
            }
            final AsyncMappingState state = mappingState;
            if (!isAsync(state)) {
                cachedMappingState = state;
            }
            return state;
        }

        void putOrderedState(final AsyncMappingState state) {
            cachedMappingState = null;
            UNSAFE.putOrderedObject(this, STATE_OFFSET, state);
            //this.state = state;
        }

        boolean mapped(final long regionPosition) {
            return (position == regionPosition && mappingState == MAPPED_STATE) && position == regionPosition;
        }

        boolean requested(final long regionPosition) {
            return requestedPosition == regionPosition && mappingState() == REQUESTED_STATE;
        }

        static {
            try {
                STATE_OFFSET = UNSAFE.objectFieldOffset(AbstractSharedValues.class.getDeclaredField("mappingState"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private interface AsyncMappingState {
        /**
         * Request region mapping if applicable
         *
         * @param regionPosition starting position of the region
         * @param shared shared values
         * @return next state to transition to
         */
        AsyncMappingState requestMap(long regionPosition, SharedValues shared);

        /**
         * Request closing if applicable
         *
         * @param closeFinalizer finalizer to deregister the async runnable
         * @param shared shared values
         * @return next state to transition to
         */
        AsyncMappingState requestClose(Runnable closeFinalizer, SharedValues shared);

        /**
         * Process request from the async thread
         *
         * @param fileMapper the file mapper for mapping and unmapping operations
         * @param closeFinalizer finalizer to deregister the async runnable
         * @param shared shared values
         * @return next state to transition to
         */
        AsyncMappingState processRequest(FileMapper fileMapper, Runnable closeFinalizer, SharedValues shared);

        /**
         * Wrap the given buffer at the specified offset, if applicable
         *
         * @param regionPosition
         * @param buffer         the buffer to wrap
         * @param offset         the offset from the region start position
         * @param shared         shared values
         * @return the length wrapped, or a return code as per {@link RegionMapper} constants
         */
        int wrap(long regionPosition, DirectBuffer buffer, int offset, SharedValues shared);
    }


    private static boolean isAsync(final AsyncMappingState state) {
        return state == REQUESTED_STATE || state == CLOSING_STATE;
    }

    private static boolean isClosedOrClosing(final AsyncMappingState state) {
        return state == CLOSED_STATE || state == CLOSING_STATE;
    }


    @Override
    public String toString() {
        return "AsyncRegion:position=" + sharedValues.position;
    }
}
