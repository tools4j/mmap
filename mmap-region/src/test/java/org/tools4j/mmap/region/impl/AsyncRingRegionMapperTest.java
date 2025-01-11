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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.RegionMapper;
import org.tools4j.mmap.region.unsafe.RegionMappers;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;

public class AsyncRingRegionMapperTest {
    private static final int MAX_DATA_LENGTH = (int)(4 * Constants.REGION_SIZE_GRANULARITY);
    @Mock
    private AsyncRuntime asyncRuntime;
    @Mock
    private FileMapper fileMapper;

    private InOrder inOrder;

    private RegionMapper regionMapper;

    private final List<Recurring> asyncRecurringList = new CopyOnWriteArrayList<>();

    private final int regionSize = (int)Constants.REGION_SIZE_GRANULARITY;
    private final int cacheSize = 4;
    private final int regionsToMapAhead = 0;

    @BeforeEach
    public void setUp() {
        asyncRecurringList.clear();
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            asyncRecurringList.add(invocation.getArgument(0));
            return null;
        }).when(asyncRuntime).register(any());
        doAnswer(invocation -> {
            //noinspection SuspiciousMethodCalls
            asyncRecurringList.remove(invocation.getArgument(0));
            return null;
        }).when(asyncRuntime).deregister(any());
        final DirectBuffer data = new UnsafeBuffer(ByteBuffer.allocateDirect(MAX_DATA_LENGTH));
        when(fileMapper.map(anyLong(), eq(regionSize))).thenAnswer(invocation -> {
            final long position = invocation.getArgument(0);
            if (position < 0 || position >= MAX_DATA_LENGTH || (position % regionSize) != 0) {
                return NULL_ADDRESS;
            }
            return data.addressOffset() + position;
        });
        regionMapper = RegionMappers.createAsyncRingRegionMapper(asyncRuntime, asyncRuntime, fileMapper, regionSize,
                cacheSize, regionsToMapAhead);
        inOrder = Mockito.inOrder(asyncRuntime, fileMapper);
        assertEquals(2, asyncRecurringList.size());
    }

    @AfterEach
    public void cleanup() {
        try (final Invoker ignored = startAsyncInvoker()) {
            regionMapper.close();
        }
    }

    @Test
    public void map_and_access_data() {
        //given
        final long position = 456;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final OffsetMapping mapping = Mappings.offsetMapping(regionMapper, true);

        //when: map ahead
        regionMapper.map(regionStartPosition);
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when
        mapping.moveTo(position);

        //then
        inOrder.verify(fileMapper, never()).map(regionStartPosition, regionSize);
        assertEquals(positionInRegion, mapping.offset());
        assertEquals(regionSize - positionInRegion, mapping.bytesAvailable());

        //when - wrap again within the same region
        final int offset = 4;
        mapping.moveToCurrentRegion(offset);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(offset, mapping.offset());
        assertEquals(regionSize - offset, mapping.bytesAvailable());

        //when - wrap again at region start
        mapping.moveTo(regionStartPosition);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        assertEquals(0, mapping.offset());
        assertEquals(regionSize, mapping.bytesAvailable());

        //when - close, causes unmap
        final long address = mapping.buffer().addressOffset();
        try (final Invoker ignored = startAsyncInvoker()) {
            mapping.close();
        }

        //then
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
    }

    @Test
    public void map_and_unmap() {
        //given
        final long position = regionSize + 123;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final OffsetMapping mapping = Mappings.offsetMapping(regionMapper, true);

        //when: map ahead
        regionMapper.map(regionStartPosition);
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when
        mapping.moveTo(position);

        //then
        inOrder.verify(fileMapper, never()).map(regionStartPosition, regionSize);

        //when - map again within the same region and check if had been mapped
        mapping.moveToCurrentRegion(0);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());

        //when - close to unmap
        long address = mapping.buffer().addressOffset();
        try (final Invoker ignored = startAsyncInvoker()) {
            mapping.close();
        }

        //then
        inOrder.verify(fileMapper, once()).unmap(address, regionStartPosition, regionSize);
        inOrder.verify(asyncRuntime, twice()).deregister(notNull());

        //when - close again should have no effect
        mapping.close();
        mapping.close();

        //then
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void map_and_remap() {
        //given
        final long position = regionSize + 123;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        final OffsetMapping mapping = Mappings.offsetMapping(regionMapper, true);

        //when
        mapping.moveTo(position);
        asyncRecurringList.forEach(Recurring::execute);
        mapping.moveTo(position);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //when - map previous region, causing current to unmap
        final long prevRegionStartPosition = regionStartPosition - regionSize;
        mapping.moveToPreviousRegion();

        //then
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());

        //when: async unmapping
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, once()).map(prevRegionStartPosition, regionSize);
    }

    private static VerificationMode once() {
        return Mockito.times(1);
    }
    private static VerificationMode twice() {
        return Mockito.times(2);
    }

    private class Invoker implements Runnable, AutoCloseable {
        volatile Thread thread;
        @Override
        public void run() {
            thread = Thread.currentThread();
            while (thread != null) {
                asyncRecurringList.forEach(Recurring::execute);
            }
        }

        @Override
        public void close() {
            final Thread thread = this.thread;
            if (thread != null) {
                this.thread = null;
                try {
                    thread.join(1000);
                } catch (final InterruptedException e) {
                    //ignore
                }
            }
        }
    }
    Invoker startAsyncInvoker() {
        final Invoker invoker = new Invoker();
        final Thread thread = new Thread(invoker);
        thread.setName("async-invoker");
        thread.start();
        return invoker;
    }

}