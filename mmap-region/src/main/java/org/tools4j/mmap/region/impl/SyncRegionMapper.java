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
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Buffers.unwrap;
import static org.tools4j.mmap.region.impl.Buffers.wrap;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

public final class SyncRegionMapper implements RegionMapper {

    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;
    private long mappedAddress = NULL_ADDRESS;
    private long mappedPosition = NULL_POSITION;
    private DirectBuffer lastWrapped;
    private boolean closed;

    public SyncRegionMapper(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
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
            final int len = wrap(buffer, lastWrapped, mappedAddress, position, metrics);
            lastWrapped = buffer;
            return len;
        }
        if (isClosed()) {
            return CLOSED;
        }
        try {
            unmapIfNecessary();
            final long addr = fileMapper.map(regionPosition, regionMetrics.regionSize());
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

    private void unmapIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        if (addr != NULL_ADDRESS) {
            final int regionSize = regionMetrics.regionSize();
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            unwrapLastWrapped(addr, regionSize);
            assert pos != NULL_POSITION;
            fileMapper.unmap(addr, pos, regionSize);
        } else {
            assert pos == NULL_POSITION;
            assert lastWrapped == null;
        }
    }

    private void unwrapLastWrapped(final long address, final int regionSize) {
        final DirectBuffer last = lastWrapped;
        if (last != null) {
            lastWrapped = null;
            unwrap(last, address, regionSize);
        }
    }

    @Override
    public void close(final long maxWaitMillis) {
        if (!isClosed()) {
            try {
                unmapIfNecessary();
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
        return "SyncRegion:mappedPosition=" + mappedPosition;
    }
}
