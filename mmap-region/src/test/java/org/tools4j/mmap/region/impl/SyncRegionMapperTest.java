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
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;

public class SyncRegionMapperTest {
    private static final int MAX_DATA_LENGTH = (int)(4 * Constants.REGION_SIZE_GRANULARITY);
    @Mock
    private FileMapper fileMapper;

    private InOrder inOrder;

    private RegionMapper regionMapper;

    private final int regionSize = (int)Constants.REGION_SIZE_GRANULARITY;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        final DirectBuffer data = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_DATA_LENGTH));
        when(fileMapper.map(anyLong(), eq(regionSize))).thenAnswer(invocation -> {
            final long position = invocation.getArgument(0);
            if (position < 0 || position >= MAX_DATA_LENGTH || (position % regionSize) != 0) {
                return NULL_ADDRESS;
            }
            return data.addressOffset() + position;
        });
        inOrder = Mockito.inOrder(fileMapper);
    }

    @AfterEach
    public void cleanup() {
        regionMapper.close();
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 4})
    public void map_and_access_data(final int cacheSize) {
        //given
        regionMapper = RegionMapperFactory.SYNC.create(fileMapper, regionSize, cacheSize, 0);
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final Region region = Region.create(regionMapper);

        //when
        region.moveTo(position);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);
        assertEquals(positionInRegion, region.offset());
        assertEquals(regionSize - positionInRegion, region.bytesAvailable());

        //when - wrap again within the same region
        final int offset = 4;
        region.moveRelativeToRegionStart(offset);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(offset, region.offset());
        assertEquals(regionSize - offset, region.bytesAvailable());

        //when - wrap again at region start
        region.moveTo(regionStartPosition);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(0, region.offset());
        assertEquals(regionSize, region.bytesAvailable());

        //when - close, causes unmap
        final long address = region.buffer().addressOffset();
        region.close();

        //then
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 4})
    public void map_and_unmap(final int cacheSize) {
        //given
        regionMapper = RegionMapperFactory.SYNC.create(fileMapper, regionSize, cacheSize, 0);
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final Region region = Region.create(regionMapper);

        //when
        region.moveTo(position);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when - map again within the same region and check if had been mapped
        region.moveRelativeToRegionStart(0);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());

        //when - close to unmap
        long address = region.buffer().addressOffset();
        region.close();

        //then
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);

        //when - close again should have no effect
        region.close();
        region.close();

        //then
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 4})
    public void map_and_remap(final int cacheSize) {
        //given
        regionMapper = RegionMapperFactory.SYNC.create(fileMapper, regionSize, cacheSize, 0);
        final long position = 456;
        final int offset = (int) (position % regionSize);
        final long regionStartPosition = position - offset;
        final Region region = Region.create(regionMapper);

        //when
        region.moveTo(position);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when
        region.moveToNextRegion(offset);

        //then
        final long nextRegionStartPosition = regionStartPosition + regionSize;
        inOrder.verify(fileMapper, once()).map(nextRegionStartPosition, regionSize);

        //when - map previous region, causing current to unmap
        final long unmapAddress = region.buffer().addressOffset() - region.offset();
        final long prevRegionStartPosition = nextRegionStartPosition - regionSize;
        region.moveToPreviousRegion();

        //then
        if (cacheSize == 1) {
            inOrder.verify(fileMapper, once()).unmap(unmapAddress, nextRegionStartPosition, regionSize);
            inOrder.verify(fileMapper, once()).map(prevRegionStartPosition, regionSize);
        } else {
            //already mapped from before, still in ring cache
            inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());
            inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        }

        //when - map region so that it falls into cache entry 0
        final long lastRegionPosition = 2L * cacheSize * regionSize;
        region.moveTo(lastRegionPosition);

        //then
        inOrder.verify(fileMapper, once()).unmap(anyLong(), eq(prevRegionStartPosition), eq(regionSize));
        inOrder.verify(fileMapper, once()).map(lastRegionPosition, regionSize);
    }

    private static VerificationMode once() {
        return Mockito.times(1);
    }

}