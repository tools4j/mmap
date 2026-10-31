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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.api.AsyncRuntime.Recurring;
import org.tools4j.mmap.mapping.impl.Constants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;

/**
 * Unit test for {@link AsyncRunAheadRegionMapper} mapping ahead at the file mapper's mapping limit and when mapping
 * fails; the async mapper is executed by the test thread.
 */
class AsyncRunAheadRegionMapperTest {

    private static final int REGION_SIZE = (int) Constants.REGION_SIZE_GRANULARITY;
    private static final int REGIONS = 4;

    private final UnsafeBuffer data = new UnsafeBuffer(ByteBuffer.allocateDirect(REGIONS * REGION_SIZE));
    private final List<Recurring> recurring = new ArrayList<>();
    private final FileMapper fileMapper = mock(FileMapper.class);
    private AsyncRunAheadRegionMapper regionMapper;

    private RegionMapper regionMapper(final long mappingLimit, final int mapAhead, final int mapAheadCacheSize) {
        final AsyncRuntime asyncRuntime = mock(AsyncRuntime.class);
        doAnswer(invocation -> recurring.add(invocation.getArgument(0))).when(asyncRuntime).register(any());
        doAnswer(invocation -> recurring.remove(invocation.getArgument(0))).when(asyncRuntime).deregister(any());
        when(fileMapper.mappingLimit()).thenReturn(mappingLimit);
        when(fileMapper.map(anyLong(), eq(REGION_SIZE))).thenAnswer(invocation -> address(invocation.getArgument(0)));
        regionMapper = new AsyncRunAheadRegionMapper(asyncRuntime, new SyncRegionMapper(fileMapper, REGION_SIZE),
                4, true, mapAhead, mapAheadCacheSize);
        assertEquals(1, recurring.size());
        return regionMapper;
    }

    private long address(final long position) {
        if (position + REGION_SIZE > REGIONS * REGION_SIZE) {
            throw new IllegalArgumentException("Attempt to map region at " + position + " exceeds max file size");
        }
        return data.addressOffset() + position;
    }

    /** Executes the async mapper until idle, returning the number of executions that performed work */
    private int executeAsyncMapper() {
        int work = 0;
        for (int i = 0; i < 100 && recurring.get(0).execute() > 0; i++) {
            work++;
        }
        return work;
    }

    @AfterEach
    void close() {
        if (regionMapper != null) {
            regionMapper.close();
        }
    }

    @Test
    void mapAheadStopsAtMappingLimit() {
        final RegionMapper mapper = regionMapper(REGIONS * REGION_SIZE, 2, 0);
        for (int region = 0; region < REGIONS; region++) {
            assertEquals(address(region * REGION_SIZE), mapper.map(region * REGION_SIZE));
            executeAsyncMapper();
        }
        verify(fileMapper, never()).map(longThat(position -> position >= REGIONS * REGION_SIZE), anyInt());
    }

    @Test
    void failedMapAheadHandsSlotBackToRequester() {
        //single map-ahead slot shared by all positions
        final RegionMapper mapper = regionMapper(Long.MAX_VALUE, 1, 1);
        final long failing = REGION_SIZE;
        when(fileMapper.map(failing, REGION_SIZE))
                .thenThrow(new IllegalStateException("mapping failed"))
                .thenAnswer(invocation -> address(failing));

        assertEquals(address(0), mapper.map(0));
        assertEquals(1, executeAsyncMapper(), "failed map-ahead executed once, not retried");
        verify(fileMapper, times(1)).map(failing, REGION_SIZE);

        //failed map-ahead is retried synchronously, which requests the next region ahead in the same slot
        assertEquals(address(failing), mapper.map(failing));
        verify(fileMapper, times(2)).map(failing, REGION_SIZE);
        assertEquals(1, executeAsyncMapper(), "slot handed back and next region mapped ahead");
        verify(fileMapper, times(1)).map(2L * REGION_SIZE, REGION_SIZE);
        assertNotEquals(NULL_ADDRESS, mapper.map(2L * REGION_SIZE));
        verify(fileMapper, times(1)).map(2L * REGION_SIZE, REGION_SIZE);
    }
}
