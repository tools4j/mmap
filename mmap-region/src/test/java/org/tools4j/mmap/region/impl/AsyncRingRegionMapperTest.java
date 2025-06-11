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
import org.tools4j.mmap.region.api.ElasticMapping;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.RegionMapper;
import org.tools4j.mmap.region.unsafe.RegionMappers;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

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

@SuppressWarnings("FieldCanBeLocal")
public class AsyncRingRegionMapperTest {
    private static final int MAX_CACHE_SIZE = 4;//see parameterized test params
    private static final int MAX_DATA_LENGTH = (int)((1 + 2*MAX_CACHE_SIZE) * Constants.REGION_SIZE_GRANULARITY);
    @Mock
    private AsyncRuntime asyncRuntime;
    @Mock
    private FileMapper fileMapper;

    private InOrder inOrder;

    private RegionMapper regionMapper;

    private final List<Recurring> asyncRecurringList = new CopyOnWriteArrayList<>();

    private final int regionSize = (int)Constants.REGION_SIZE_GRANULARITY;
    private final int cacheSize = 4;
    private final int lruCacheSize = 0;
    private final int regionsToMapAhead = 1;
    private final int mapAheadCacheSize = 1;
    private final int unmapCacheSize = 4;
    private final boolean deferUnmap = true;

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
        final AtomicBoolean closed = new AtomicBoolean();
        when(fileMapper.isClosed()).thenAnswer(invocation -> closed.get());
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(fileMapper).close();
        regionMapper = new LoggingRegionMapper(RegionMappers.createAsyncRunAheadRegionMapper(
                new LoggingFileMapper(fileMapper), regionSize, cacheSize, lruCacheSize, deferUnmap,
                asyncRuntime, regionsToMapAhead, mapAheadCacheSize, asyncRuntime, unmapCacheSize
        ));
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
        final int offsetInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - offsetInRegion;
        final ElasticMapping mapping = Mappings.elasticMapping(regionMapper, true);

        //when: move to position
        mapping.moveTo(regionStartPosition);
        asyncRecurringList.forEach(Recurring::execute);

        //then: mapped
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);

        //and: mapped ahead
        for (int i = 0; i < regionsToMapAhead; i++) {
            final long regionPosition = regionStartPosition + (i + 1) * regionSize;
            inOrder.verify(fileMapper, once()).map(regionPosition, regionSize);
        }

        //when: move to same position
        mapping.moveTo(position);

        //then
        inOrder.verify(fileMapper, never()).map(regionStartPosition, regionSize);
        assertEquals(offsetInRegion, mapping.offset());
        assertEquals(regionSize - offsetInRegion, mapping.bytesAvailable());

        //when: move again within the same region
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

        //when: close
        final long address = mapping.buffer().addressOffset();
        try (final Invoker ignored = startAsyncInvoker()) {
            mapping.close();
        }

        //then: all unmapped
        inOrder.verify(fileMapper, once()).unmap(regionStartPosition, address, regionSize);
        for (int i = 0; i < regionsToMapAhead; i++) {
            final long regionPosition = regionStartPosition + (i + 1) * regionSize;
            inOrder.verify(fileMapper, once()).unmap(eq(regionPosition), anyLong(), eq(regionSize));
        }
    }

    @Test
    public void map_then_unmap() {
        //given
        final long position = 123;
        final int offsetInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - offsetInRegion;
        final ElasticMapping mapping = Mappings.elasticMapping(regionMapper, true);

        //when: map ahead
        regionMapper.map(regionStartPosition);
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);
        for (int i = 0; i < regionsToMapAhead; i++) {
            final long regionPosition = regionStartPosition + (i + 1) * regionSize;
            inOrder.verify(fileMapper, once()).map(regionPosition, regionSize);
        }

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
        inOrder.verify(fileMapper, once()).unmap(regionStartPosition, address, regionSize);
        inOrder.verify(asyncRuntime, once()).deregister(notNull());
        for (int i = 0; i < regionsToMapAhead; i++) {
            final long regionPosition = regionStartPosition + (i + 1) * regionSize;
            inOrder.verify(fileMapper, once()).unmap(eq(regionPosition), anyLong(), eq(regionSize));
        }
        inOrder.verify(asyncRuntime, once()).deregister(notNull());

        //when - close again should have no effect
        mapping.close();
        mapping.close();

        //then
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());
    }

    @Test
    public void map_and_remap() {
        //given
        final long position = regionSize + 123;
        final int offsetInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - offsetInRegion;
        final ElasticMapping mapping = Mappings.elasticMapping(regionMapper, true);

        //when
        mapping.moveTo(position);
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, once()).map(regionStartPosition, regionSize);
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());

        //and: map ahead, but backwards as position start is >0
        long regionPosition = regionStartPosition;
        for (int i = 0; i < regionsToMapAhead && regionPosition >= 0; i++) {
            regionPosition -= regionSize;
            inOrder.verify(fileMapper, once()).map(regionPosition, regionSize);
        }
        inOrder.verifyNoMoreInteractions();

        //when - map previous region, already mapped
        mapping.moveToPreviousRegion();
        asyncRecurringList.forEach(Recurring::execute);

        //then
        inOrder.verify(fileMapper, never()).map(anyLong(), anyInt());
        inOrder.verify(fileMapper, never()).unmap(anyLong(), anyLong(), anyInt());
    }

    private static VerificationMode once() {
        return Mockito.times(1);
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