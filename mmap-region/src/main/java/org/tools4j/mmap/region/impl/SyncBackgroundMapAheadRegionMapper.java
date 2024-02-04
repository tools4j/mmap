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

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

public final class SyncBackgroundMapAheadRegionMapper implements RegionMapper {

    private static final long MAX_CLOSE_WAIT_MILLIS = 500;
    private final AsyncRuntime asyncRuntime;
    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private final Recurring recurring = this::execBackgroundMapping;
    private long mappedAddress = NULL_ADDRESS;
    private long mappedPosition = NULL_POSITION;
    private long backgroundRequestedPosition = NULL_POSITION;
    private long backgroundMappedAddress = NULL_ADDRESS;
    private long backgroundMappedPosition = NULL_POSITION;
    private volatile boolean backgroundMappingPending;
    private boolean backgroundMappingAvailable;
    private DirectBuffer lastMapped;
    private boolean closed;

    public SyncBackgroundMapAheadRegionMapper(final AsyncRuntime asyncRuntime,
                                              final FileMapper fileMapper,
                                              final RegionMetrics regionMetrics) {
        this.asyncRuntime = requireNonNull(asyncRuntime);
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
        if (regionPosition == mappedPosition) {
            return wrap(metrics, mappedAddress, position, buffer);
        }
        if (isClosed()) {
            return CLOSED;
        }
        if (buffer == null) {
            return mapInBackground(regionPosition);
        }
        try {
            final long addr;
            if (regionPosition == backgroundMappedPosition && backgroundAvailable()) {
                addr = backgroundMappedAddress;
                unmapAsyncIfNecessary();
            } else {
                if (backgroundAvailable()) {
                    unmapAsyncIfNecessary();
                } else {
                    unmapSyncIfNecessary();
                }
                addr = fileMapper.map(regionPosition, regionMetrics.regionSize());
            }
            if (addr > 0) {
                mappedAddress = addr;
                mappedPosition = regionPosition;
                return wrap(metrics, addr, position, buffer);
            } else {
                return FAILED;
            }
        } catch (final Exception exception) {
            return FAILED;
        }
    }

    private boolean backgroundAvailable() {
        return backgroundMappingAvailable || (backgroundMappingAvailable = !backgroundMappingPending);
    }

    private int mapInBackground(final long regionPosition) {
        if (backgroundMappingPending) {
            return BUSY;
        }
        if (backgroundMappedPosition == regionPosition) {
            return regionMetrics.regionSize();
        }
        backgroundRequestedPosition = regionPosition;
        backgroundMappingAvailable = false;
        backgroundMappingPending = true;
        return PROCESSING;
    }

    private int wrap(final RegionMetrics metrics,
                     final long mappedAddress,
                     final long position,
                     final DirectBuffer buffer) {
        final int offset = metrics.regionOffset(position);
        final int length = metrics.regionSize() - offset;
        if (buffer != null) {
            buffer.wrap(mappedAddress + offset, length);
        }
        lastMapped = buffer;
        return length;
    }

    private void unmapSyncIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        final DirectBuffer last = lastMapped;
        if (addr != NULL_ADDRESS) {
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            if (last != null) {
                last.wrap(0, 0);
                lastMapped = null;
            }
            assert pos != NULL_POSITION;
            final long regionPosition = regionMetrics.regionPosition(pos);
            fileMapper.unmap(addr, regionPosition, regionMetrics.regionSize());
        } else {
            assert pos == NULL_POSITION;
            assert last == null;
        }
    }

    private void unmapAsyncIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        final DirectBuffer last = lastMapped;
        if (addr != NULL_ADDRESS) {
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            if (last != null) {
                last.wrap(0, 0);
                lastMapped = null;
            }
            assert pos != NULL_POSITION;
            backgroundMappedAddress = addr;
            backgroundMappedPosition = pos;
            backgroundRequestedPosition = NULL_POSITION;
            backgroundMappingAvailable = false;
            backgroundMappingPending = true;
        } else {
            assert pos == NULL_POSITION;
            assert last == null;
            backgroundMappedAddress = NULL_ADDRESS;
            backgroundMappedPosition = NULL_POSITION;
        }
    }

    private int execBackgroundMapping() {
        if (backgroundMappingPending) {
            final int regionSize = regionMetrics.regionSize();
            final long addr = backgroundMappedAddress;
            final long pos = backgroundMappedPosition;
            final long req = backgroundRequestedPosition;
            backgroundRequestedPosition = NULL_POSITION;
            if (pos != req) {
                backgroundMappedPosition = NULL_POSITION;
                backgroundMappedAddress = NULL_ADDRESS;
                if (addr != NULL_ADDRESS) {
                    assert pos != NULL_POSITION;
                    fileMapper.unmap(addr, pos, regionSize);
                }
                if (req != NULL_POSITION) {
                    final long mappedAddr = fileMapper.map(req, regionSize);
                    backgroundMappedAddress = mappedAddr > 0 ? mappedAddr : NULL_ADDRESS;
                    backgroundMappedPosition = req;
                }
            }
            backgroundMappingPending = false;
            return 1;
        }
        return 0;
    }

    private void unmapBackgroundMappingOnClose() {
        if (backgroundAvailable() && backgroundMappedAddress == NULL_ADDRESS) {
            return;
        }
        final long timeoutTimeMillis = System.currentTimeMillis() + MAX_CLOSE_WAIT_MILLIS;
        do {
            if (backgroundAvailable()) {
                if (backgroundMappedAddress == NULL_ADDRESS) {
                    return;
                }
                backgroundRequestedPosition = NULL_POSITION;
                backgroundMappingAvailable = false;
                backgroundMappingPending = true;
            }
        } while (System.currentTimeMillis() < timeoutTimeMillis);
        System.err.println("WARNING: unmapping of background mapping timed out on close");
    }

    @Override
    public void close() {
        if (!isClosed()) {
            unmapBackgroundMappingOnClose();
            asyncRuntime.deregister(recurring);
            try {
                unmapSyncIfNecessary();
            } finally {
                closed = true;
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        return "BackgroundRegion:mappedPosition=" + mappedPosition;
    }
}
