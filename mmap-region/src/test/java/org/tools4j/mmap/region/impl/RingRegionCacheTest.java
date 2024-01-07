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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RingRegionCache}
 */
public class RingRegionCacheTest {
    private MutableRegion region1;
    private MutableRegion region2;
    private MutableRegion region3;
    private MutableRegion region4;

    private RegionCache regionCache;
    private final int regionSize = 128;

    @BeforeEach
    public void setup() {
        final int cacheSize = 4;
        final List<MutableRegion> regions = new ArrayList<>(cacheSize);
        final RegionMetrics regionMetrics = new PowerOfTwoRegionMetrics(regionSize);
        regionCache = new RingRegionCache(regionMetrics, metrics -> {
            final MutableRegion region = Mockito.mock(MutableRegion.class);
            final MappingState mappingState = Mockito.mock(MappingState.class);
            when(region.mappingState()).thenReturn(mappingState);
            regions.add(region);
            return region;
        }, cacheSize);
        region1 = regions.get(0);
        region2 = regions.get(1);
        region3 = regions.get(2);
        region4 = regions.get(3);
    }

    @Test
    public void get() {
        //when + then
        assertSame(region4, regionCache.get(7L * regionSize + 45));
        assertSame(region4, regionCache.get(7L * regionSize + 60));
        assertSame(region1, regionCache.get(7L * regionSize + 130));
        assertSame(region2, regionCache.get(9L * regionSize + 20));
        assertSame(region3, regionCache.get(10L * regionSize + 22));
        assertSame(region2, regionCache.get(9L * regionSize));
        assertSame(region3, regionCache.get(10L * regionSize));
        assertSame(region4, regionCache.get(11L * regionSize));
    }

    @Test
    public void close() {
        //given
        final InOrder inOrder = Mockito.inOrder(region1.mappingState(), region2.mappingState(),
                region3.mappingState(), region4.mappingState());

        //when
        regionCache.close();

        //verify
        inOrder.verify(region1.mappingState()).close();
        inOrder.verify(region2.mappingState()).close();
        inOrder.verify(region3.mappingState()).close();
        inOrder.verify(region4.mappingState()).close();
    }
}