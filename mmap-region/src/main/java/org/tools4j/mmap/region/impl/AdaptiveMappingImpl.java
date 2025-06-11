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
import org.tools4j.mmap.region.api.DynamicMapping;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validatePosition;

public final class AdaptiveMappingImpl implements AdaptiveMapping {
    private final RegionMapper regionMapper;
    private final boolean closeFileMapperOnClose;
    private final RegionMetrics regionMetrics;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;
    private int offset;

    @Unsafe
    public AdaptiveMappingImpl(final RegionMapper regionMapper, final boolean closeFileMapperOnClose) {
        this.regionMapper = requireNonNull(regionMapper);
        this.closeFileMapperOnClose = closeFileMapperOnClose;
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionMapper.regionSize());
        this.mappedPosition = NULL_POSITION;
        this.offset = 0;
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
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public long regionStartPosition() {
        return mappedPosition - offset;//works also for NULL_POSITION
    }

    @Override
    public int offset() {
        return offset;
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
    public boolean isMapped() {
        return mappedPosition != NULL_POSITION;
    }

    @Override
    public boolean isClosed() {
        return regionMapper.isClosed();
    }

    @Override
    public boolean moveTo(final long position) {
        validatePosition(position);
        final RegionMetrics metrics = regionMetrics;
        final int oldOffset = offset;
        final int newOffset = metrics.regionOffset(position);
        final long oldRegionPosition = mappedPosition - oldOffset;
        final long newRegionPosition = metrics.regionPosition(position);
        final long address;
        if (newRegionPosition == oldRegionPosition) {
            if (newOffset == oldOffset) {
                return true;
            }
            address = address() - oldOffset;
        } else {
            address = regionMapper.map(newRegionPosition);
            if (address == NULL_ADDRESS) {
                clearMapping();
                return false;
            }
        }
        initMapping(address, position, newOffset, metrics.regionSize());
        return true;
    }

    private void initMapping(final long address, final long position, final int newOffset, final int regionSize) {
        assert position >= NULL_POSITION : "invalid position";
        assert address >= NULL_ADDRESS : "invalid address";
        mappedPosition = position;
        offset = newOffset;
        buffer.wrap(address + newOffset, regionSize - newOffset);
    }

    private void clearMapping() {
        mappedPosition = NULL_POSITION;
        offset = 0;
        buffer.wrap(0, 0);
    }

    @Override
    public void close() {
        if (!regionMapper.isClosed()) {
            mappedPosition = NULL_POSITION;
            buffer.wrap(0, 0);
            if (closeFileMapperOnClose) {
                regionMapper.close();
            }
        }
    }

    @Override
    public boolean findLast(final long startPosition,
                            final long positionIncrement,
                            final Predicate<? super DynamicMapping> matcher) {
        return DynamicMappingImpl.findLast(this, startPosition, positionIncrement, matcher);
    }

    @Override
    public boolean binarySearchLast(final long startPosition,
                                    final long positionIncrement,
                                    final Predicate<? super DynamicMapping> matcher) {
        return DynamicMappingImpl.binarySearchLast(this, startPosition, positionIncrement, matcher);
    }

    @Override
    public String toString() {
        return "AdaptiveMappingImpl:mapped=" + isMapped() +
                "|regionStartPosition=" + regionStartPosition() +
                "|offset=" + offset() +
                "|regionSize=" + regionSize() +
                "|bytesAvailable=" + bytesAvailable() +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
