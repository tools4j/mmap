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
///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
// *
// * Permission is hereby granted, free of charge, to any person obtaining a copy
// * of this software and associated documentation files (the "Software"), to deal
// * in the Software without restriction, including without limitation the rights
// * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// * copies of the Software, and to permit persons to whom the Software is
// * furnished to do so, subject to the following conditions:
// *
// * The above copyright notice and this permission notice shall be included in all
// * copies or substantial portions of the Software.
// *
// * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// * SOFTWARE.
// */
//package org.tools4j.mmap.region.impl;
//
//import org.agrona.DirectBuffer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InOrder;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.tools4j.mmap.region.api.AsyncRuntime;
//import org.tools4j.mmap.region.api.FileMapper;
//import org.tools4j.mmap.region.api.Region;
//import org.tools4j.mmap.region.api.RegionMetrics;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.junit.jupiter.api.Assertions.assertSame;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.when;
//
///**
// * Unit test for {@link AsyncRegionManager}
// */
//@ExtendWith(MockitoExtension.class)
//public class AsyncRegionManagerTest {
//    private Region region1;
//    private Region region2;
//    private Region region3;
//    private Region region4;
//
//    private InOrder inOrder;
//
//    @Mock
//    private AsyncRuntime asyncRuntime;
//    @Mock
//    private FileMapper fileMapper;
//
//    private RegionManager regionManager;
//    private final int regionSize = 128;
//    private final int regionCacheSize = 4;
//    private final int regionsToMapAhead = 3;
//    private final boolean stopRuntimeOnClose = true;
//
//    @BeforeEach
//    public void setup() {
//        final int cacheSize = 4;
//        final List<Region> regions = new ArrayList<>(cacheSize);
//        final RegionMetrics regionMetrics = new PowerOfTwoRegionMetrics(regionSize);
//        regionManager = new AsyncRegionManager(asyncRuntime, fileMapper, regionMetrics, regionCacheSize,
//                regionsToMapAhead, stopRuntimeOnClose);
//            regions.add(region);
//            return region;
//        }, cacheSize);
//        region1 = regions.get(0);
//        region2 = regions.get(1);
//        region3 = regions.get(2);
//        region4 = regions.get(3);
//    }
//
//    @Test
//    public void wrap() {
//        //when + then
//        assertSame(region4, regionManager.get(7L * regionSize + 45));
//        assertSame(region1, regionManager.get(7L * regionSize + 60));
//        assertSame(region2, regionManager.get(8L * regionSize + 20));
//
//        //then also
//        inOrder.verify(region3).map(10L * regionSize);
//
//        //when
//        when(region4.wrap(7L * regionSize + 60, directBuffer)).thenReturn(true);
//        regionManager.wrap(7L * regionSize + 60, directBuffer);
//
//        inOrder.verify(region1, times(0)).map(8L * regionSize);
//        inOrder.verify(region2, times(0)).map(9L * regionSize);
//        inOrder.verify(region3, times(0)).map(10L * regionSize);
//
//        //when
//        when(region1.wrap(8L * regionSize + 20, directBuffer)).thenReturn(true);
//        regionManager.wrap(8L * regionSize + 20, directBuffer);
//
//        //then
//        inOrder.verify(region2).map(9L * regionSize);
//        inOrder.verify(region3).map(10L * regionSize);
//        inOrder.verify(region4).map(11L * regionSize);
//
//        //when - backwards
//        when(region3.wrap(6L * regionSize + 80, directBuffer)).thenReturn(true);
//        regionManager.wrap(6L * regionSize + 80, directBuffer);
//
//        //then
//        inOrder.verify(region2).map(5L * regionSize);
//        inOrder.verify(region1).map(4L * regionSize);
//        inOrder.verify(region4).map(3L * regionSize);
//    }
//
//    @Test
//    public void close() {
//        //when
//        regionManager.close();
//
//        //verify
//        inOrder.verify(region1).close();
//        inOrder.verify(region2).close();
//        inOrder.verify(region3).close();
//        inOrder.verify(region4).close();
//    }
//
//    @Test
//    public void wrap_when_backwards_direction_approaching_0_position() {
//        final Region[] regions = new Region[]{region1, region2, region3, region4};
//        regionManager = new RegionRingAccessor(regions, regionSize, 1, onClose);
//
//        when(region2.wrap(regionSize + 45, directBuffer)).thenReturn(true);
//        regionManager.wrap(regionSize + 45, directBuffer);
//        inOrder.verify(region2).wrap(regionSize + 45, directBuffer);
//        inOrder.verify(region3).map(2L * regionSize);
//
//        when(region1.wrap(45, directBuffer)).thenReturn(true);
//
//        //when
//        regionManager.wrap(45, directBuffer);
//        inOrder.verify(region1).wrap(45, directBuffer);
//        inOrder.verify(region4, never()).map(anyLong());
//        inOrder.verify(region2).unmap();
//    }
//
//    @Test
//    public void size() {
//        assertEquals(regionSize, regionManager.size());
//    }
//}