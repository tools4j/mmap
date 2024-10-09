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
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.region.api.DynamicRegion;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionPosition;

public final class DynamicRegionImpl implements DynamicRegion {
    private final RegionMapper regionMapper;
    private final RegionMetrics regionMetrics;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;

    public DynamicRegionImpl(final RegionMapper regionMapper) {
        this.regionMapper = requireNonNull(regionMapper);
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionMapper.regionSize());
        this.mappedPosition = NULL_POSITION;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public int regionSize() {
        return regionMetrics.regionSize();
    }

    @Override
    public boolean isMapped() {
        return mappedPosition != NULL_POSITION;
    }

    @Override
    public long position() {
        return mappedPosition;
    }

    @Override
    public long address() {
        return buffer.addressOffset();
    }

    @Override
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public long regionStartPosition() {
        return mappedPosition;
    }

    @Override
    public boolean isClosed() {
        return regionMapper.isClosed();
    }

    @Override
    public boolean moveTo(final long position) {
        if (position == mappedPosition && position > NULL_POSITION) {
            return true;
        }
        final RegionMetrics metrics = regionMetrics;
        validateRegionPosition(position, metrics.regionSize());
        final long addr = regionMapper.map(position);
        if (addr > NULL_ADDRESS) {
            mappedPosition = position;
            buffer.wrap(addr, metrics.regionSize());
            return true;
        }
        mappedPosition = NULL_POSITION;
        buffer.wrap(0, 0);
        return false;
    }

    @Override
    public void close() {
        if (!regionMapper.isClosed()) {
            mappedPosition = NULL_POSITION;
            buffer.wrap(0, 0);
            regionMapper.close();
        }
    }

    @Override
    public String toString() {
        return "DynamicRegionImpl:mapped=" + isMapped() +
                "|start=" + regionStartPosition() +
                "|regionSize=" + regionSize() +
                "|bytesAvailable=" + bytesAvailable() +
                "|closed=" + isClosed();
    }
}
