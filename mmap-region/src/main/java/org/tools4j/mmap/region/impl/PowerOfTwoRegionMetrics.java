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

import org.agrona.BitUtil;
import org.tools4j.mmap.region.api.RegionMetrics;

public final class PowerOfTwoRegionMetrics implements RegionMetrics {

    private final int regionSize;
    private final int offsetMask;
    private final long regionMask;
    private final int regionShift;

    public PowerOfTwoRegionMetrics(final int regionSize) {
        if (!BitUtil.isPowerOfTwo(regionSize)) {
            throw new IllegalArgumentException("Region size must be a positive power of 2: " + regionSize);
        }
        this.regionSize = regionSize;
        this.offsetMask = regionSize - 1;
        this.regionMask = -regionSize;
        this.regionShift = Integer.SIZE - Integer.numberOfLeadingZeros(regionSize - 1);
    }

    @Override
    public int regionSize() {
        return regionSize;
    }

    @Override
    public int regionOffset(final long position) {
        return (int)(position & offsetMask);
    }

    @Override
    public long regionPosition(final long position) {
        return position & regionMask;
    }

    @Override
    public long regionIndex(final long position) {
        return position >>> regionShift;
    }

    @Override
    public long regionPositionByIndex(final long index) {
        return index << regionShift;
    }

    @Override
    public String toString() {
        return "RegionMetrics:regionSize=" + regionSize;
    }
}
