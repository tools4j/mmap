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
import org.tools4j.mmap.region.api.AdaptiveMapping;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateLength;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;
import static org.tools4j.mmap.region.impl.Constraints.validatePosition;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;

public final class AdaptiveMappingImpl implements AdaptiveMapping {
    private final RegionMapper regionMapper;
    private final boolean closeRegionMapperOnClose;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedRegionPosition;
    private long mappedRegionAddress;
    private int offset;
    private boolean closed;

    @Unsafe
    public AdaptiveMappingImpl(final RegionMapper regionMapper, final boolean closeRegionMapperOnClose) {
        validateNotClosed(regionMapper);
        this.regionMapper = requireNonNull(regionMapper);
        this.closeRegionMapperOnClose = closeRegionMapperOnClose;
        this.mappedRegionPosition = NULL_POSITION;
        this.mappedRegionAddress = NULL_ADDRESS;
        this.offset = 0;
        this.closed = false;
    }

    @Override
    public int positionStepSize() {
        return 1;
    }

    @Override
    public AccessMode accessMode() {
        return regionMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMapper.regionMetrics();
    }

    @Override
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public long regionStartPosition() {
        return mappedRegionPosition;
    }

    @Override
    public int regionOffset() {
        return offset;
    }

    @Override
    public long position() {
        return mappedRegionPosition + offset;//works also for NULL_POSITION
    }

    @Override
    public long address() {
        return mappedRegionAddress + offset;//works also for NULL_ADDRESS
    }

    @Override
    public boolean isMapped() {
        return mappedRegionPosition != NULL_POSITION;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean moveTo(final long position) {
        validatePosition(position);
        final int maxLength = maxLengthAtPosition(position);
        final int newLength = isMapped() ? Math.min(length(), maxLength) : maxLength;
        return moveToInternal(position, newLength);
    }

    @Override
    public boolean moveTo(final long position, final int length) {
        validatePosition(position);
        validateLength(length, maxLengthAtPosition(position));
        return moveToInternal(position, length);
    }

    private boolean moveToInternal(final long position, final int length) {
        final RegionMetrics metrics = regionMetrics();
        final int newOffset = metrics.regionOffset(position);
        final long newRegionPosition = metrics.regionPosition(position);
        final long oldRegionPosition = mappedRegionPosition;
        if (newRegionPosition == oldRegionPosition) {
            if (newOffset == offset && length == length()) {
                return true;
            }
            initBufferAndOffset(mappedRegionAddress, newOffset, length);
            return true;
        }
        final long oldRegionAddress = mappedRegionAddress;
        final long newRegionAddress = map(newRegionPosition);
        if (newRegionAddress != NULL_ADDRESS) {
            initBufferAndOffset(newRegionAddress, newOffset, length);
            unmap(oldRegionPosition, oldRegionAddress, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        final long newPosition = position + delta;
        final int newLength = Math.min(length(), maxLengthAtPosition(newPosition));
        return moveToInternal(newPosition, newLength);
    }

    @Override
    public boolean moveBy(final long delta, final int length) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        final long newPosition = position + delta;
        validateLength(length, maxLengthAtPosition(newPosition));
        return moveToInternal(newPosition, length);
    }

    private void initBufferAndOffset(final long regionAddress, final int offset, final int length) {
        this.buffer.wrap(regionAddress + offset, length);
        this.offset = offset;
    }

    private long map(final long regionPosition) {
        final long addr = regionMapper.map(regionPosition);
        if (addr > NULL_ADDRESS) {
            mappedRegionPosition = regionPosition;
            mappedRegionAddress = addr;
        }
        return addr;
    }

    private void unmap(final long mappedPos, final long mappedAddr, final boolean clearState) {
        if (mappedPos == NULL_POSITION) {
            assert mappedAddr == NULL_ADDRESS : "address cannot be mapped";
            return;
        }
        assert mappedAddr != NULL_ADDRESS : "address cannot be unmapped";
        if (clearState) {
            buffer.wrap(0, 0);
            mappedRegionPosition = NULL_POSITION;
            mappedRegionAddress = NULL_ADDRESS;
            offset = 0;
        }
        regionMapper.unmap(mappedPos, mappedAddr);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        unmap(mappedRegionPosition, mappedRegionAddress, true);
        closed = true;
        if (closeRegionMapperOnClose) {
            regionMapper.close();
        }
    }

    @Override
    public String toString() {
        return "AdaptiveMappingImpl:mapped=" + isMapped() +
                "|regionStartPosition=" + regionStartPosition() +
                "|offset=" + regionOffset() +
                "|length=" + length() +
                "|maxLength=" + maxLength() +
                "|regionSize=" + regionSize() +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
