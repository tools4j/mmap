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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionCursor;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BackgroundMapAheadRegionMapperTest {
    private static final int MAX_DATA_LENGTH = 512;
    @Mock
    private AsyncRuntime asyncRuntime;
    @Mock
    private FileMapper fileMapper;

    private InOrder inOrder;

    private RegionMapper regionMapper;

    private Recurring asyncRecurring;

    private final int regionSize = 128;

    @BeforeEach
    public void setUp() {
        doAnswer(invocation -> {
            asyncRecurring = invocation.getArgument(0);
            return null;
        }).when(asyncRuntime).register(any());
        final DirectBuffer data = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_DATA_LENGTH));
        when(fileMapper.map(anyLong(), eq(regionSize))).thenAnswer(invocation -> {
            final long position = invocation.getArgument(0);
            if (position < 0 || position >= MAX_DATA_LENGTH || (position % regionSize) != 0) {
                return FileMapper.NULL_ADDRESS;
            }
            return data.addressOffset() + position;
        });
        final RegionMetrics regionMetrics = new PowerOfTwoRegionMetrics(regionSize);
        regionMapper = RegionMapperFactory.ahead("AHEAD", asyncRuntime, true).create(fileMapper, regionMetrics);
        inOrder = Mockito.inOrder(asyncRuntime, fileMapper);
        assertNotNull(asyncRecurring);
    }

    @AfterEach
    public void cleanup() {
        regionMapper.close();
    }

    @Test
    public void map_and_access_data() {
        //given
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final RegionCursor rider = RegionCursor.noWait(regionMapper);

        //when: map ahead
        regionMapper.map(regionStartPosition, null);
        asyncRecurring.execute();

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when
        rider.moveTo(position);

        //then
        inOrder.verify(fileMapper, never()).map(regionStartPosition, regionSize);
        assertEquals(positionInRegion, rider.offset());
        assertEquals(regionSize - positionInRegion, rider.bytesAvailable());

        //when - wrap again within the same region
        final int offset = 4;
        rider.moveRelativeToRegionStart(offset);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(offset, rider.offset());
        assertEquals(regionSize - offset, rider.bytesAvailable());

        //when - wrap again at region start
        rider.moveTo(regionStartPosition);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(0, rider.offset());
        assertEquals(regionSize, rider.bytesAvailable());

        //when - close, causes unmap
        final long address = rider.buffer().addressOffset();
        rider.close();
        asyncRecurring.execute();

        //then
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
    }

    @Test
    public void map_and_unmap() {
        //given
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final RegionCursor rider = RegionCursor.noWait(regionMapper);

        //when: map ahead
        regionMapper.map(regionStartPosition, null);
        asyncRecurring.execute();

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when
        rider.moveTo(position);

        //then
        inOrder.verify(fileMapper, never()).map(regionStartPosition, regionSize);

        //when - map again within the same region and check if had been mapped
        rider.moveRelativeToRegionStart(0);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());

        //when - close to unmap
        long address = rider.buffer().addressOffset();
        rider.close();
        asyncRecurring.execute();

        //then
        inOrder.verify(asyncRuntime, once()).deregister(asyncRecurring);
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);
        inOrder.verify(asyncRuntime, once()).close();

        //when - close again should have no effect
        rider.close();
        rider.close();

        //then
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void map_and_remap() {
        //given
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final RegionCursor rider = RegionCursor.noWait(regionMapper);

        //when
        rider.moveTo(position);
        asyncRecurring.execute();
        rider.moveTo(position);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when - map previous region, causing current to unmap
        final long unmapAddress = rider.buffer().addressOffset() - rider.offset();
        final long prevRegionStartPosition = regionStartPosition - regionSize;
        rider.moveToPreviousRegion();

        //then
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());

        //when: async unmapping
        asyncRecurring.execute();

        //then
        inOrder.verify(fileMapper, once()).map(prevRegionStartPosition, regionSize);
    }

    private static VerificationMode once() {
        return Mockito.times(1);
    }

}