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
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

public final class DefaultRegion implements Region {
    private final RegionMapper regionMapper;
    private final RegionMetrics regionMetrics;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;
    private long mappedAddress;

    public DefaultRegion(final RegionMapper regionMapper) {
        this.regionMapper = requireNonNull(regionMapper);
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionMapper.regionSize());
        this.mappedPosition = NULL_POSITION;
        this.mappedAddress = NULL_ADDRESS;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
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
    public long position() {
        return mappedPosition + offset();//works also for NULL_POSITION
    }

    @Override
    public int offset() {
        return mappedAddress == NULL_ADDRESS ? 0 : (int)(buffer.addressOffset() - mappedAddress);
    }

    @Override
    public boolean isClosed() {
        return regionMapper.isClosed();
    }

    @Override
    public boolean moveTo(final long position) {
        final long regionPosition = regionMetrics.regionPosition(position);
        if (regionPosition == mappedPosition) {
            wrapBuffer(position);
            return true;
        }
        final long addr = regionMapper.map(regionPosition);
        if (addr > NULL_ADDRESS) {
            mappedPosition = regionPosition;
            mappedAddress = addr;
            wrapBuffer(position);
            return true;
        }
        return false;
    }

    @Override
    public boolean findLast(final long startPosition, final long positionIncrement, final Predicate<? super Region> matcher) {
        long lastPosition = NULL_POSITION;
        for (long position = startPosition; moveTo(position) && matcher.test(this); position += positionIncrement) {
            lastPosition = position;
        }
        if (lastPosition != NULL_POSITION) {
            moveTo(lastPosition);
            return true;
        }
        return false;
    }

    @Override
    public boolean binarySearchLast(final long startPosition, final long positionIncrement, final Predicate<? super Region> matcher) {
        if (positionIncrement <= 0) {
            throw new IllegalArgumentException("Position increment most be positive: " + positionIncrement);
        }
        //1) initial low
        if (!moveTo(startPosition) || !matcher.test(this)) {
            return false;
        }
        long lowPosition = startPosition;
        long highPosition = NULL_POSITION;

        //2) find low + high
        while (highPosition == NULL_POSITION && lowPosition + positionIncrement >= 0) {
            long increment = positionIncrement;
            do {
                if (highPosition != NULL_POSITION) {
                    lowPosition = highPosition;
                }
                highPosition = lowPosition + increment;
                if (increment <= 0 || highPosition < 0) {
                    highPosition = NULL_POSITION;
                    break;
                }
                increment <<= 1;
            } while (moveTo(highPosition) && matcher.test(this));
        }

        //3) find middle
        if (highPosition != NULL_POSITION) {
            while (lowPosition + positionIncrement < highPosition) {
                final long midPosition = mid(lowPosition, highPosition);
                if (moveTo(midPosition) && matcher.test(this)) {
                    lowPosition = midPosition;
                } else {
                    highPosition = midPosition;
                }
            }
        }
        moveTo(lowPosition);
        return true;
    }

    private static long mid(final long a, final long b) {
        return (a >>> 1) + (b >>> 1) + (a & b & 0x1L);
    }

    private void wrapBuffer(final long position) {
        final RegionMetrics metrics = regionMetrics;
        final int offset = metrics.regionOffset(position);
        final int length = metrics.regionSize() - offset;
        buffer.wrap(mappedAddress + offset, length);
    }

    @Override
    public void close() {
        buffer.wrap(0, 0);
        mappedPosition = NULL_POSITION;
        mappedAddress = NULL_ADDRESS;
        regionMapper.close();
    }

    @Override
    public String toString() {
        return "DefaultRegion:mapped=" + isMapped() +
                "|start=" + regionStartPosition() +
                "|offset=" + offset() +
                "|bytesAvailable=" + bytesAvailable();
    }
}