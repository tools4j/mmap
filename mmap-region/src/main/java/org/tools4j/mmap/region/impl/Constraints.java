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

    public static void validateAddress(final long address) {
        if (address <= NULL_ADDRESS) {
            throw new IllegalArgumentException("Invalid address " + address);
        }
    }

    public static void validatePositionState(final long position) {
        if (position < 0) {
            throw new IllegalStateException("Invalid current position");
        }
    }

    public static void validatePosition(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative, but was " + position);
        }
    }

    public static void validatePosition(final long position, final long positionGranularity) {
        assert BitUtil.isPowerOfTwo(positionGranularity);
        if (position < 0 || 0 != (position & (positionGranularity - 1))) {
            throw new IllegalArgumentException("Invalid position " + position +
                    " for mapping with position granularity " + positionGranularity);
        }
    }

    public static void validateRegionPosition(final long position, final RegionMetrics regionMetrics) {
        validateRegionPosition(position, regionMetrics.regionSize());
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
        assert position >= 0;
        if (position + delta < 0) {
            throw new IllegalArgumentException("Invalid position delta " + delta + " from start position " + position);
        }
    }

    public static void validatePositionDelta(final long position, final long delta, final long positionGranularity) {
        assert position >= 0;
        assert BitUtil.isPowerOfTwo(positionGranularity);
        final long pos = position + delta;
        if (pos < 0 || 0 != (pos & (positionGranularity - 1))) {
            throw new IllegalArgumentException("Invalid position delta " + delta + " from start position " + position +
                    " for mapping with position granularity " + positionGranularity);
        }
    }

    public static void validateLimit(final long position, final long limit, final int maxLength) {
        if (position > limit || (limit - position) > maxLength) {
            throw new IllegalArgumentException("Invalid limit " + limit + " for position " + position +
                    " and adaptive mapping with max-length " + maxLength);
        }
    }

    public static void validateLength(final int length, final int maxLength) {
        if (length < 0 || length >= maxLength) {
            throw new IllegalArgumentException("Invalid length " + length + " for adaptive mapping with max-length " +
                    maxLength);
        }
    }

    public static void validateRegionSize(final int regionSize) {
        if (!BitUtil.isPowerOfTwo(regionSize) || regionSize % REGION_SIZE_GRANULARITY != 0) {
            throw new IllegalArgumentException("Region size must be a power of two and a multiple of " +
                    REGION_SIZE_GRANULARITY + " but was " + regionSize);
        }
    }

    public static void validatePositionGranularity(final int positionGranularity, final int regionSize) {
        assert BitUtil.isPowerOfTwo(regionSize);
        if (!BitUtil.isPowerOfTwo(positionGranularity) || regionSize % positionGranularity != 0) {
            throw new IllegalArgumentException("Position granularity must be a power of two and aligned with region size " +
                    regionSize + " but was " + positionGranularity);
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

    public static void validateRegionLruCacheSize(final int cacheSize) {
        validateNonNegative("Region LRU cache size", cacheSize);
    }

    public static void validateRegionsToMapAhead(final int regionsToMapAhead) {
        validateGreaterThanZero("Regions to map ahead", regionsToMapAhead);
    }

    public static void validateAheadMappingCacheSize(final int aheadMappingCacheSize) {
        if (aheadMappingCacheSize > 0) {
            validatePowerOfTwo("Ahead-mapping cache size", aheadMappingCacheSize);
        }
    }

    public static void validateUnmappingCacheSize(final int cacheSize) {
        validateNonNegative("Unmapping cache size", cacheSize);
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
