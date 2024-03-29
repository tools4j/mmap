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
import static org.tools4j.mmap.region.impl.Buffers.wrap;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

public final class BackgroundMapAheadRegionMapper implements RegionMapper {
    private final AsyncRuntime asyncRuntime;
    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private final Runnable closeFinalizer;
    private final Recurring recurring = this::execBackgroundMapping;
    private final BackgroundMapping backgroundMapping = new BackgroundMapping();
    private long mappedAddress = NULL_ADDRESS;
    private long mappedPosition = NULL_POSITION;
    private DirectBuffer lastWrapped;
    private boolean closed;

    public BackgroundMapAheadRegionMapper(final AsyncRuntime asyncRuntime,
                                          final FileMapper fileMapper,
                                          final RegionMetrics regionMetrics,
                                          final Runnable closeFinalizer) {
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.closeFinalizer = requireNonNull(closeFinalizer);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
        asyncRuntime.register(recurring);
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public int map(final long position, final DirectBuffer buffer) {
        validPosition(position);
        final RegionMetrics metrics = regionMetrics;
        final long regionPosition = metrics.regionPosition(position);
        if (buffer == null) {
            return mapInBackground(regionPosition);
        }
        if (regionPosition == mappedPosition) {
            final int len = wrap(buffer, lastWrapped, mappedAddress, position, metrics);
            lastWrapped = buffer;
            return len;
        }
        if (isClosed()) {
            return CLOSED;
        }
        try {
            final BackgroundMapping background = backgroundMapping;
            final long addr;
            if (background.isMappedTo(regionPosition)) {
                addr = background.mappedAddress;
                unmapAsyncIfNecessary();
            } else {
                if (background.available()) {
                    unmapAsyncIfNecessary();
                } else {
                    unmapSyncIfNecessary();
                }
                addr = fileMapper.map(regionPosition, regionMetrics.regionSize());
            }
            if (addr > 0) {
                final int len = wrap(buffer, lastWrapped, addr, position, metrics);
                mappedAddress = addr;
                mappedPosition = regionPosition;
                lastWrapped = buffer;
                return len;
            } else {
                return FAILED;
            }
        } catch (final Exception exception) {
            return FAILED;
        }
    }

    private int mapInBackground(final long regionPosition) {
        if (mappedPosition == regionPosition) {
            return regionMetrics.regionSize();
        }
        if (isClosed()) {
            return CLOSED;
        }
        final BackgroundMapping background = backgroundMapping;
        if (!background.available()) {
            return BUSY;
        }
        if (background.mappedPosition == regionPosition) {
            return regionMetrics.regionSize();
        }
        background.requestedPosition = regionPosition;
        background.setPendingFlag();
        return PROCESSING;
    }

    private void unmapSyncIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        final DirectBuffer last = lastWrapped;
        if (addr != NULL_ADDRESS) {
            final int regionSize = regionMetrics.regionSize();
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            unwrapLastWrapped(addr, regionSize);
            assert pos != NULL_POSITION;
            fileMapper.unmap(addr, pos, regionSize);
        } else {
            assert pos == NULL_POSITION;
            assert last == null;
        }
    }

    private void unmapAsyncIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        final BackgroundMapping background = backgroundMapping;
        if (addr != NULL_ADDRESS) {
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            unwrapLastWrapped(addr, regionMetrics.regionSize());
            assert pos != NULL_POSITION;
            background.mappedAddress = addr;
            background.mappedPosition = pos;
            background.requestedPosition = NULL_POSITION;
            background.setPendingFlag();
        } else {
            assert pos == NULL_POSITION;
            assert lastWrapped == null;
            background.mappedAddress = NULL_ADDRESS;
            background.mappedPosition = NULL_POSITION;
        }
    }

    private void unwrapLastWrapped(final long address, final int regionSize) {
        final DirectBuffer last = lastWrapped;
        if (last != null) {
            lastWrapped = null;
            unwrap(last, address, regionSize);
        }
    }

    private int execBackgroundMapping() {
        final BackgroundMapping background = backgroundMapping;
        if (background.pending != 0) {
            final int regionSize = regionMetrics.regionSize();
            final long addr = background.mappedAddress;
            final long pos = background.mappedPosition;
            final long req = background.requestedPosition;
            background.requestedPosition = NULL_POSITION;
            if (pos != req) {
                background.mappedPosition = NULL_POSITION;
                background.mappedAddress = NULL_ADDRESS;
                if (addr != NULL_ADDRESS) {
                    assert pos != NULL_POSITION;
                    fileMapper.unmap(addr, pos, regionSize);
                }
                if (req != NULL_POSITION) {
                    final long mappedAddr = fileMapper.map(req, regionSize);
                    background.mappedAddress = mappedAddr > 0 ? mappedAddr : NULL_ADDRESS;
                    background.mappedPosition = req;
                }
            }
            background.clearPendingFlag();
            return 1;
        }
        return 0;
    }

    private void unmapBackgroundMappingOnClose(final long maxWaitMillis) {
        final BackgroundMapping background = backgroundMapping;
        if (background.available() && background.mappedAddress == NULL_ADDRESS) {
            return;
        }
        final long timeoutTimeMillis = System.currentTimeMillis() + maxWaitMillis;
        do {
            if (background.available()) {
                if (background.mappedAddress == NULL_ADDRESS) {
                    return;
                }
                background.requestedPosition = NULL_POSITION;
                background.setPendingFlag();
            } else {
                LockSupport.parkNanos(20_000);
            }
        } while (System.currentTimeMillis() < timeoutTimeMillis);
        System.err.println("WARNING: unmapping of background mapping timed out on close");
    }

    @Override
    public void close(final long maxWaitMillis) {
        if (!isClosed()) {
            try {
                unmapBackgroundMappingOnClose(maxWaitMillis);
                asyncRuntime.deregister(recurring);
                unmapSyncIfNecessary();
                closeFinalizer.run();
            } finally {
                closed = true;
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }


    private static abstract class AbstractBackgroundPadding1 {
        byte p000, p001, p002, p003, p004, p005, p006, p007, p008, p009, p010, p011, p012, p013, p014, p015;
        byte p016, p017, p018, p019, p020, p021, p022, p023, p024, p025, p026, p027, p028, p029, p030, p031;
        byte p032, p033, p034, p035, p036, p037, p038, p039, p040, p041, p042, p043, p044, p045, p046, p047;
        byte p048, p049, p050, p051, p052, p053, p054, p055, p056, p057, p058, p059, p060, p061, p062, p063;
    }

    private static abstract class AbstractBackgroundValues extends AbstractBackgroundPadding1 {
        //@Contended
        long requestedPosition = NULL_POSITION;
        long mappedAddress = NULL_ADDRESS;
        long mappedPosition = NULL_POSITION;
        volatile int pending;
        boolean available = true;
    }

    @SuppressWarnings("unused")
    private static abstract class AbstractBackgroundPadding2 extends AbstractBackgroundValues {
        byte p064, p065, p066, p067, p068, p069, p070, p071, p072, p073, p074, p075, p076, p077, p078, p079;
        byte p080, p081, p082, p083, p084, p085, p086, p087, p088, p089, p090, p091, p092, p093, p094, p095;
        byte p096, p097, p098, p099, p100, p101, p102, p103, p104, p105, p106, p107, p108, p109, p110, p111;
        byte p112, p113, p114, p115, p116, p117, p118, p119, p120, p121, p122, p123, p124, p125, p126, p127;
    }

    private static final class BackgroundMapping extends AbstractBackgroundPadding2 {
        private static final long PENDING_OFFSET;

        void setPendingFlag() {
            available = false;
            UNSAFE.putOrderedInt(this, PENDING_OFFSET, 1);
        }
        void clearPendingFlag() {
            UNSAFE.putOrderedInt(this, PENDING_OFFSET, 0);
        }

        boolean available() {
            return available || (available = (pending == 0));
        }

        boolean isMappedTo(final long regionPosition) {
            return mappedPosition == regionPosition && available() && mappedPosition == regionPosition;
        }

        static {
            try {
                PENDING_OFFSET = UNSAFE.objectFieldOffset(AbstractBackgroundValues.class.getDeclaredField("pending"));
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String toString() {
        return "BackgroundMapAheadRegion:mappedPosition=" + mappedPosition;
    }
}
