/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.RegionMapping;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionPosition;

public final class RegionMappingImpl implements RegionMapping {
    private final RegionMapper regionMapper;
    private final RegionMetrics regionMetrics;
    private final boolean closeRegionMapperOnClose;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;
    private long mappedAddress;
    private boolean closed;

    @Unsafe
    public RegionMappingImpl(final RegionMapper regionMapper, final boolean closeRegionMapperOnClose) {
        this.regionMapper = requireNonNull(regionMapper);
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionMapper.regionSize());
        this.closeRegionMapperOnClose = closeRegionMapperOnClose;
        this.mappedPosition = NULL_POSITION;
        this.mappedAddress = NULL_ADDRESS;
        this.closed = false;
    }

    @Override
    public int positionStepSize() {
        return regionSize();
    }

    @Override
    public AccessMode accessMode() {
        return regionMapper.accessMode();
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
    public int regionOffset() {
        return 0;
    }

    @Override
    public long position() {
        return mappedPosition;
    }

    @Override
    public long address() {
        return mappedAddress;
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
        return closed;
    }

    @Override
    public boolean moveTo(final long position) {
        final long mappedPos = mappedPosition;
        if (position == mappedPos && position > NULL_POSITION) {
            return true;
        }
        final int regionSize = regionSize();
        validateRegionPosition(position, regionSize);
        final long mappedAddr = mappedAddress;
        if (map(position, regionSize)) {
            unmap(mappedPos, mappedAddr, false);
            return true;
        }
        return false;
    }

    private boolean map(final long position, final int regionSize) {
        final long addr = regionMapper.map(position);
        if (addr > NULL_ADDRESS) {
            mappedPosition = position;
            mappedAddress = addr;
            buffer.wrap(addr, regionSize);
            return true;
        }
        return false;
    }

    private void unmap(final long mappedPos, final long mappedAddr, final boolean clearState) {
        if (mappedPos == NULL_POSITION) {
            assert mappedAddr == NULL_ADDRESS : "address cannot be mapped";
            return;
        }
        assert mappedAddr != NULL_ADDRESS : "address cannot be unmapped";
        if (clearState) {
            buffer.wrap(0, 0);
            mappedPosition = NULL_POSITION;
            mappedAddress = NULL_ADDRESS;
        }
        regionMapper.unmap(mappedPos, mappedAddr);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        unmap(mappedPosition, mappedAddress, true);
        closed = true;
        if (closeRegionMapperOnClose) {
            regionMapper.close();
        }
    }

    @Override
    public String toString() {
        return "RegionMappingImpl:mapped=" + isMapped() +
                "|regionStartPosition=" + regionStartPosition() +
                "|offset=" + regionOffset() +
                "|regionSize=" + regionSize() +
                "|bytesAvailable=" + bytesAvailable() +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
