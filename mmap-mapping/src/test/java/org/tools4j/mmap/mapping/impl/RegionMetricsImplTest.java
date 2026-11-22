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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link RegionMetricsImpl}.
 */
class RegionMetricsImplTest {

    private static final int REGION_SIZE = 4096;

    private static final int OVERLAP_SIZE = 1024;

    private final RegionMetricsImpl metrics = new RegionMetricsImpl(REGION_SIZE, OVERLAP_SIZE);

    @Test
    void regionOffsetAndPosition() {
        assertEquals(REGION_SIZE, metrics.regionSize());
        assertEquals(0, metrics.regionOffset(0));
        assertEquals(REGION_SIZE - 1, metrics.regionOffset(REGION_SIZE - 1));
        assertEquals(1, metrics.regionOffset(3L * REGION_SIZE + 1));
        assertEquals(0, metrics.regionPosition(REGION_SIZE - 1));
        assertEquals(3L * REGION_SIZE, metrics.regionPosition(3L * REGION_SIZE + 1));
    }

    @Test
    void regionIndexAndPositionByIndexAreInverse() {
        assertEquals(0, metrics.regionIndex(REGION_SIZE - 1));
        assertEquals(2, metrics.regionIndex(2L * REGION_SIZE));
        final long largeIndex = 1L << 40;
        assertEquals(largeIndex, metrics.regionIndex(largeIndex * REGION_SIZE + 7));
        for (final long index : new long[]{0, 1, 2, 1000, largeIndex}) {
            assertEquals(index * REGION_SIZE, metrics.regionPositionByIndex(index));
            assertEquals(index, metrics.regionIndex(metrics.regionPositionByIndex(index)));
        }
    }

    @Test
    void regionSizeOfOneMapsEveryPositionToItsOwnRegion() {
        final RegionMetricsImpl unitMetrics = new RegionMetricsImpl(1, 0);
        assertEquals(0, unitMetrics.regionOffset(5));
        assertEquals(5, unitMetrics.regionPosition(5));
        assertEquals(5, unitMetrics.regionIndex(5));
        assertEquals(5, unitMetrics.regionPositionByIndex(5));
    }

    @Test
    void regionSizeMustBePositivePowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(-REGION_SIZE, 0));
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(3000, 0));
    }

    @Test
    void overlapAddsToMappingSizeOnly() {
        assertEquals(OVERLAP_SIZE, metrics.overlapSize());
        assertEquals(REGION_SIZE + OVERLAP_SIZE, metrics.mappingSize());
        assertEquals(REGION_SIZE - 1, metrics.regionOffset(REGION_SIZE - 1));
        assertEquals(1, metrics.regionIndex(REGION_SIZE));
        final RegionMetricsImpl noOverlap = new RegionMetricsImpl(REGION_SIZE, 0);
        assertEquals(0, noOverlap.overlapSize());
        assertEquals(REGION_SIZE, noOverlap.mappingSize());
        assertEquals(2 * REGION_SIZE, new RegionMetricsImpl(REGION_SIZE, REGION_SIZE).mappingSize());
    }

    @Test
    void maxLengthAtPositionEndsAtOverlapEnd() {
        assertEquals(REGION_SIZE + OVERLAP_SIZE, metrics.maxLengthAtPosition(0));
        assertEquals(REGION_SIZE + OVERLAP_SIZE, metrics.maxLengthAtPosition(3L * REGION_SIZE));
        assertEquals(REGION_SIZE + OVERLAP_SIZE - 100, metrics.maxLengthAtPosition(REGION_SIZE + 100L));
        assertEquals(OVERLAP_SIZE + 1, metrics.maxLengthAtPosition(REGION_SIZE - 1L));
        assertEquals(1, new RegionMetricsImpl(REGION_SIZE, 0).maxLengthAtPosition(REGION_SIZE - 1L));
    }

    @Test
    void safeLengthIsOverlapPlusAlignment() {
        assertEquals(OVERLAP_SIZE + 1, metrics.safeLength(1));
        assertEquals(OVERLAP_SIZE + Long.BYTES, metrics.safeLength(Long.BYTES));
        assertEquals(REGION_SIZE + OVERLAP_SIZE, metrics.safeLength(REGION_SIZE));
        for (final int alignment : new int[]{1, Long.BYTES, REGION_SIZE}) {
            final long lastAlignedPosition = 2L * REGION_SIZE - alignment;
            assertEquals(metrics.safeLength(alignment), metrics.maxLengthAtPosition(lastAlignedPosition),
                    "alignment " + alignment);
        }
    }

    @Test
    void overlapSizeMustBeBetweenZeroAndRegionSize() {
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(REGION_SIZE, -1));
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(REGION_SIZE, REGION_SIZE + 1));
        final int maxRegionSize = 1 << 30;
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(maxRegionSize, maxRegionSize),
                "mapping size exceeds int");
        assertEquals(Integer.MAX_VALUE, new RegionMetricsImpl(maxRegionSize, maxRegionSize - 1).mappingSize());
    }

    @Test
    void toStringShowsGeometry() {
        assertEquals("RegionMetricsImpl:regionSize=4096|overlapSize=1024", metrics.toString());
    }
}
