/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
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
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.Region;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SyncRegionTest {
    @Mock
    private DirectBuffer directBuffer;
    @Mock
    private FileChannel fileChannel;
    @Mock
    private Region.IoMapper ioMapper;
    @Mock
    private Region.IoUnMapper ioUnMapper;
    @Mock
    private FileSizeEnsurer fileSizeEnsurer;

    private InOrder inOrder;

    private Region region;

    private int length = 128;
    private FileChannel.MapMode mapMode = FileChannel.MapMode.READ_WRITE;

    @Before
    public void setUp() throws Exception {
        region = new SyncRegion(
                () -> fileChannel,
                ioMapper, ioUnMapper, fileSizeEnsurer,
                mapMode, length);
        inOrder = inOrder(directBuffer, fileChannel, ioMapper, ioUnMapper, fileSizeEnsurer);
    }

    @Test
    public void wrap_map_and_unmap() throws Exception {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, length)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + length)).thenReturn(true);

        //when and then
        assertThat(region.wrap(position, directBuffer)).isTrue();

        inOrder.verify(ioMapper, times(1)).map(fileChannel, mapMode, regionStartPosition, length);
        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, length - positionInRegion);

        //when - wrap again within the same region
        final int offset = 4;
        region.wrap(regionStartPosition + offset, directBuffer);

        //then
        inOrder.verify(directBuffer).wrap(expectedAddress + offset, length - offset);
        inOrder.verify(ioMapper, times(0)).map(fileChannel, mapMode, regionStartPosition, length);

        //when
        region.map(regionStartPosition);
        //then
        inOrder.verify(ioMapper, times(0)).map(fileChannel, mapMode, regionStartPosition, length);

        //when unmap request
        assertThat(region.unmap()).isTrue();

        inOrder.verify(ioUnMapper, times(1)).unmap(fileChannel, expectedAddress, length);

        assertThat(region.unmap()).isTrue();
        inOrder.verify(ioUnMapper, times(0)).unmap(fileChannel, expectedAddress, length);
    }

    @Test
    public void map_and_unmap() throws Exception {
        //given
        final AtomicBoolean hadBeenMapped = new AtomicBoolean();
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, length)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + length)).thenReturn(true);

        assertThat(region.map(regionStartPosition)).isTrue();

        inOrder.verify(ioMapper, times(1)).map(fileChannel, mapMode, regionStartPosition, length);

        //when - map again within the same region and check if had been mapped
        assertThat(region.map(regionStartPosition)).isTrue();

        //then
        inOrder.verify(ioMapper, times(0)).map(fileChannel, mapMode, regionStartPosition, length);

        assertThat(region.unmap()).isTrue();

        inOrder.verify(ioUnMapper, times(1)).unmap(fileChannel, expectedAddress, length);

        assertThat(region.unmap()).isTrue();
        inOrder.verify(ioUnMapper, times(0)).unmap(fileChannel, expectedAddress, length);
    }

    @Test
    public void map_and_remap() throws Exception {
        //given
        final AtomicBoolean hadBeenMapped = new AtomicBoolean();
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, length)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + length)).thenReturn(true);

        //when - request mapping
        assertThat(region.map(regionStartPosition)).isTrue();

        inOrder.verify(ioMapper, times(1)).map(fileChannel, mapMode, regionStartPosition, length);


        //when send unmap request
        final long prevRegionStartPosition = regionStartPosition - length;
        final long prevExpectedAddress = expectedAddress - length;
        when(ioMapper.map(fileChannel, mapMode, prevRegionStartPosition, length)).thenReturn(prevExpectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition)).thenReturn(true);


        assertThat(region.map(prevRegionStartPosition)).isTrue();

        inOrder.verify(ioUnMapper, times(1)).unmap(fileChannel, expectedAddress, length);
        inOrder.verify(ioMapper, times(1)).map(fileChannel, mapMode, prevRegionStartPosition, length);

    }

    @Test
    public void map_and_close() throws Exception {
        //given
        final AtomicBoolean hadBeenMapped = new AtomicBoolean();
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % length);
        final long regionStartPosition = position - positionInRegion;
        assertThat(region.size()).isEqualTo(length);

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, length)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + length)).thenReturn(true);

        //when - request mapping
        assertThat(region.map(regionStartPosition)).isTrue();

        //then
        inOrder.verify(ioMapper, times(1)).map(fileChannel, mapMode, regionStartPosition, length);

        //when
        region.close();

        //then
        inOrder.verify(ioUnMapper, times(1)).unmap(fileChannel, expectedAddress, length);
    }

}