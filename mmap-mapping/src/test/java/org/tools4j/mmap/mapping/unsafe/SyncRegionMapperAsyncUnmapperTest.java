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
package org.tools4j.mmap.mapping.unsafe;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.mapping.impl.Constants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link SyncRegionMapperAsyncUnmapper}.
 */
class SyncRegionMapperAsyncUnmapperTest {

    private static final int REGION_SIZE = (int) Constants.REGION_SIZE_GRANULARITY;

    @Test
    void mapsWithoutPreTouch() throws InterruptedException {
        final UnsafeBuffer data = new UnsafeBuffer(ByteBuffer.allocateDirect(REGION_SIZE));
        final FileMapper fileMapper = mock(FileMapper.class);
        when(fileMapper.map(anyLong(), eq(REGION_SIZE), anyBoolean())).thenReturn(data.addressOffset());
        final AsyncRuntime asyncRuntime = mock(AsyncRuntime.class);
        final List<Recurring> recurring = new ArrayList<>();
        doAnswer(invocation -> recurring.add(invocation.getArgument(0))).when(asyncRuntime).register(any());
        final RegionMapper regionMapper = new SyncRegionMapperAsyncUnmapper(asyncRuntime, fileMapper, REGION_SIZE, 32);
        assertEquals(data.addressOffset(), regionMapper.map(0));
        verify(fileMapper).map(0L, REGION_SIZE, false);
        RecurringTasks.closeWhileExecuting(regionMapper::close, recurring.get(0));
    }
}
