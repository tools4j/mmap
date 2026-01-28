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

import org.agrona.BitUtil;
import org.tools4j.mmap.region.api.RegionMetrics;

import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;

public enum Constraints {
    ;
    public static void validateNonNegative(final String name, final long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " value annot be negative, but was " + value);
        }
    }
    public static void validateGreaterThanZero(final String name, final long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " value must be positive, but was " + value);
        }
    }

    public static void validatePosition(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative, but was " + position);
        }
    }

    public static void validatePositionState(final long position) {
        if (position < 0) {
            throw new IllegalStateException("Invalid current position");
        }
    }

    public static void validateRegionPosition(final long position, final RegionMetrics regionMetrics) {
        validateRegionPosition(position, regionMetrics.regionSize());
    }

    public static void validateAddress(final long address) {
        if (address <= NULL_ADDRESS) {
            throw new IllegalArgumentException("Invalid address " + address);
        }
    }

    public static void validateRegionPosition(final long position, final int regionSize) {
        assert BitUtil.isPowerOfTwo(regionSize);
        if (position < 0 || 0 != (position & (regionSize - 1))) {
            throw new IllegalArgumentException("Invalid region position " + position + " for region size " + regionSize);
        }
    }

    public static void validateRegionOffset(final int offset, final RegionMetrics regionMetrics) {
        validateRegionOffset(offset, regionMetrics.regionSize());
    }

    public static void validateRegionOffset(final int offset, final int regionSize) {
        if (offset < 0 || offset >= regionSize) {
            throw new IllegalArgumentException("Invalid region offset " + offset + " for region size " + regionSize);
        }
    }

    public static void validatePositionDelta(final long position, final long delta) {
        if (position + delta < 0) {
            throw new IllegalArgumentException("Invalid position delta " + delta + " from start position " + position);
        }
    }

    public static void validateLimit(final long position, final long limit, final RegionMetrics regionMetrics) {
        final long maxLimit = regionMetrics.regionPosition(position) + regionMetrics.regionSize();
        if (position > limit || limit > maxLimit) {
            throw new IllegalArgumentException("Invalid limit " + limit + " for position " + position +
                    " and region size " + regionMetrics.regionSize());
        }
    }

    public static int validateLength(final long position, final int length, final RegionMetrics regionMetrics) {
        //NOTE: below also works for NULL_POSITION
        final int offset = regionMetrics.regionOffset(position);
        if (length == -1) {
            return regionMetrics.regionSize() - offset;
        } else if (0 <= length && offset + length <= regionMetrics.regionSize()) {
            return length;
        }
        throw new IllegalArgumentException("Invalid length " + length + " for position " + position +
                " and region size " + regionMetrics.regionSize());
    }

    public static void validateRegionSlice(final int offset, final int length, final RegionMetrics regionMetrics) {
        validateRegionSlice(offset, length, regionMetrics.regionSize());
    }

    public static void validateRegionSlice(final int offset, final int length, final int regionSize) {
        validateRegionOffset(offset, regionSize);
        if (length < 0 || offset + length > regionSize) {
            throw new IllegalArgumentException("Invalid slice length " + length + " for offset " + offset +
                    " and region size " + regionSize);
        }
    }

    public static void validateRegionSize(final int regionSize) {
        if (!BitUtil.isPowerOfTwo(regionSize) || regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("Region size must be a power of two and a multiple of " +
                    REGION_SIZE_GRANULARITY + " but was " + regionSize);
        }
    }

    public static void validateMaxFileSize(final long maxFileSize) {
        if (!BitUtil.isPowerOfTwo(maxFileSize) || maxFileSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("Max file size must be a power of two and a multiple of " +
                    REGION_SIZE_GRANULARITY + " but was " + maxFileSize);
        }
    }

    public static void validateRegionCacheSize(final int cacheSize) {
        validatePowerOfTwo("Region cache size", cacheSize);
    }

    public static void validateRegionsToMapAhead(final int regionsToMapAhead) {
        validateNonNegative("Regions to map ahead", regionsToMapAhead);
    }

    public static void validateFilesToCreateAhead(final int filesToCreateAhead) {
        validateNonNegative("Files to create ahead", filesToCreateAhead);
    }

    public static void validateMaxAppenders(final int maxAppenders) {
        if (maxAppenders == 1 || maxAppenders == 64 || maxAppenders == 256) {
            return;
        }
        throw new IllegalArgumentException("Max appenders must be one of [1, 64, 256]");
    }

    public static void validatePowerOfTwo(final String name, final int value) {
        if (!BitUtil.isPowerOfTwo(value)) {
            throw new IllegalArgumentException(name + " must be a power of two but was " + value);
        }
    }

    public static void validateNotClosed(final Closeable closeable) {
        if (closeable.isClosed()) {
            throw new IllegalStateException("Already closed: " + closeable);
        }
    }

}
