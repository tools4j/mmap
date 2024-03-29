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

import org.agrona.BitUtil;
import org.tools4j.mmap.region.api.RegionMetrics;

import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;

public enum Constraints {
    ;
    public static void nonNegative(final long value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " value annot be negative, but was " + value);
        }
    }
    public static void greaterThanZero(final long value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " value must be positive, but was " + value);
        }
    }

    public static void validPosition(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative, but was " + position);
        }
    }

    public static void validPositionState(final long position) {
        if (position < 0) {
            throw new IllegalStateException("Invalid current position");
        }
    }

    public static void validRegionOffset(final int offset, final RegionMetrics regionMetrics) {
        validRegionOffset(offset, regionMetrics.regionSize());
    }

    public static void validRegionOffset(final int offset, final int regionSize) {
        if (offset < 0 || offset >= regionSize) {
            throw new IllegalArgumentException("Invalid region offset " + offset + " for region size " + regionSize);
        }
    }

    public static void validRegionWrapParameters(final int offset, final int length, final RegionMetrics regionMetrics) {
        validRegionWrapParameters(offset, length, regionMetrics.regionSize());
    }

    public static void validRegionWrapParameters(final int offset, final int length, final int regionSize) {
        validRegionOffset(offset, regionSize);
        if (length < 0 || offset + length > regionSize) {
            throw new IllegalArgumentException("Invalid length " + length + " for region offset " + offset +
                    " and region size " + regionSize);
        }
    }

    public static void validRegionSize(final int regionSize) {
        if (!BitUtil.isPowerOfTwo(regionSize) || regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("Region size must be a power of two and a multiple of " +
                    REGION_SIZE_GRANULARITY + " but was " + regionSize);
        }
    }

    public static void validRegionCacheSize(final int regionCacheSize) {
        if (!BitUtil.isPowerOfTwo(regionCacheSize)) {
            throw new IllegalArgumentException("Region cache size must be a power of two but was " + regionCacheSize);
        }
    }

}
