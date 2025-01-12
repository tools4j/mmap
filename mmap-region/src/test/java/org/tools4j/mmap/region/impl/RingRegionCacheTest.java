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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link RingCacheRegionMapper}
 */
public class RingRegionCacheTest {
    private RegionMapper region1;
    private RegionMapper region2;
    private RegionMapper region3;
    private RegionMapper region4;

    private RingCacheRegionMapper regionCache;
    private final int regionSize = 128;

    @BeforeEach
    public void setup() {
        final int cacheSize = 4;
        final List<RegionMapper> regions = new ArrayList<>(cacheSize);
        final RegionMetrics regionMetrics = new PowerOfTwoRegionMetrics(regionSize);
        final FileMapper fileMapper = mock(FileMapper.class);
        regionCache = new RingCacheRegionMapper(fileMapper,
                RegionMapperFactories.factory("RegionFactory", false, () -> {}, (fMapper, rMetrics, cFinalizer) -> {
                    final RegionMapper mapper = Mockito.mock(RegionMapper.class);
                    regions.add(mapper);
                    return mapper;
                }), regionMetrics, cacheSize, 0, () -> {});
        region1 = regions.get(0);
        region2 = regions.get(1);
        region3 = regions.get(2);
        region4 = regions.get(3);
    }

    @Test
    public void map() {
        //given
        final InOrder inOrder = Mockito.inOrder(region1, region2, region3, region4);
        final DirectBuffer buffer = new UnsafeBuffer();
        long position;

        //when
        position = 7L * regionSize + 45;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region4).map(position, buffer);

        //when
        position = 7L * regionSize + 60;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region4).map(position, buffer);

        //when
        position = 7L * regionSize + 130;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region1).map(position, buffer);

        //when
        position = 9L * regionSize + 20;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region2).map(position, buffer);

        //when
        position = 10L * regionSize + 22;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region3).map(position, buffer);

        //when
        position = 9L * regionSize;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region2).map(position, buffer);

        //when
        position = 10L * regionSize;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region3).map(position, buffer);

        //when
        position = 11L * regionSize;
        regionCache.map(position, buffer);

        //then
        inOrder.verify(region4).map(position, buffer);
    }

    @Test
    public void close() {
        //given
        final InOrder inOrder = Mockito.inOrder(region1, region2, region3, region4);

        //when
        regionCache.close();

        //verify
        inOrder.verify(region1).close(anyLong());
        inOrder.verify(region2).close(anyLong());
        inOrder.verify(region3).close(anyLong());
        inOrder.verify(region4).close(anyLong());
    }
}