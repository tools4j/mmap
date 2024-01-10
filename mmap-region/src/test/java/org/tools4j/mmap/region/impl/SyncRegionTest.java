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

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SyncRegionTest {
    @Mock
    private DirectBuffer directBuffer;
    @Mock
    private FileMapper fileMapper;

    private InOrder inOrder;

    private Region region;

    private final int length = 128;

    @Before
    public void setUp() {
        region = new SyncRegion(fileMapper, length);
        inOrder = inOrder(directBuffer, fileMapper);
    }

    @Test
    public void wrap_map_and_unmap() {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);

        //when and then
        assertTrue(region.wrap(position, directBuffer));

        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);
        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, length - positionInRegion);

        //when - wrap again within the same region
        final int offset = 4;
        region.wrap(regionStartPosition + offset, directBuffer);

        //then
        inOrder.verify(directBuffer).wrap(expectedAddress + offset, length - offset);
        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);

        //when
        region.map(regionStartPosition);
        //then
        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);

        //when unmap request
        assertTrue(region.unmap());

        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);

        assertTrue(region.unmap());
        inOrder.verify(fileMapper, times(0)).unmap(expectedAddress, regionStartPosition, length);
    }

    @Test
    public void map_and_unmap() {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);

        assertTrue(region.map(regionStartPosition));

        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);

        //when - map again within the same region and check if had been mapped
        assertTrue(region.map(regionStartPosition));

        //then
        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);

        assertTrue(region.unmap());

        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);

        assertTrue(region.unmap());
        inOrder.verify(fileMapper, times(0)).unmap(expectedAddress, regionStartPosition, length);
    }

    @Test
    public void map_and_remap() {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);

        //when - request mapping
        assertTrue(region.map(regionStartPosition));

        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);

        //when send unmap request
        final long prevRegionStartPosition = regionStartPosition - length;
        final long prevExpectedAddress = expectedAddress - length;
        when(fileMapper.map(prevRegionStartPosition, length)).thenReturn(prevExpectedAddress);

        assertTrue(region.map(prevRegionStartPosition));

        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
        inOrder.verify(fileMapper, times(1)).map(prevRegionStartPosition, length);

    }

    @Test
    public void map_and_close() {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;
        assertEquals(length, region.size());

        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);

        //when - request mapping
        assertTrue(region.map(regionStartPosition));

        //then
        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);

        //when
        region.close();

        //then
        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
    }

}