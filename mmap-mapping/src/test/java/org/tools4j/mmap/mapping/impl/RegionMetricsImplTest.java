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

    private final RegionMetricsImpl metrics = new RegionMetricsImpl(REGION_SIZE);

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
        final RegionMetricsImpl unitMetrics = new RegionMetricsImpl(1);
        assertEquals(0, unitMetrics.regionOffset(5));
        assertEquals(5, unitMetrics.regionPosition(5));
        assertEquals(5, unitMetrics.regionIndex(5));
        assertEquals(5, unitMetrics.regionPositionByIndex(5));
    }

    @Test
    void regionSizeMustBePositivePowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(0));
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(-REGION_SIZE));
        assertThrows(IllegalArgumentException.class, () -> new RegionMetricsImpl(3000));
    }

    @Test
    void toStringShowsRegionSize() {
        assertEquals("RegionMetricsImpl:regionSize=4096", metrics.toString());
    }
}
