/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
///**
// * The MIT License (MIT)
// *
// * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
//import java.nio.channels.FileChannel;
//
//import org.agrona.DirectBuffer;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.InOrder;
//import org.mockito.Mock;
//import org.mockito.runners.MockitoJUnitRunner;
//
//import org.tools4j.mmap.region.api.FileSizeEnsurer;
//import org.tools4j.mmap.region.api.RegionMapper;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.Mockito.inOrder;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.when;
//
//@RunWith(MockitoJUnitRunner.class)
//public class SyncRegionTest {
//    @Mock
//    private DirectBuffer directBuffer;
//    @Mock
//    private FileChannel fileChannel;
//    @Mock
//    private RegionMapper.IoMapper ioMapper;
//    @Mock
//    private RegionMapper.IoUnmapper ioUnmapper;
//    @Mock
//    private FileSizeEnsurer fileSizeEnsurer;
//
//    private InOrder inOrder;
//
//    private Region region;
//
//    private int regionSize = 128;
//    private FileChannel.MapMode mapMode = FileChannel.MapMode.READ_WRITE;
//
//    @Before
//    public void setUp() {
//        region = new SyncRegion(
//                () -> fileChannel,
//                ioMapper, ioUnmapper, fileSizeEnsurer,
//                mapMode, regionSize);
//        inOrder = inOrder(directBuffer, fileChannel, ioMapper, ioUnmapper, fileSizeEnsurer);
//    }
//
//    @Test
//    public void wrap_map_and_unmap() {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int) (position % regionSize);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
//        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);
//
//        //when and then
//        assertThat(region.wrap(position, directBuffer)).isNotEqualTo(0);
//
//        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);
//        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, regionSize - positionInRegion);
//
//        //when - wrap again within the same region
//        final int offset = 4;
//        region.wrap(regionStartPosition + offset, directBuffer);
//
//        //then
//        inOrder.verify(directBuffer).wrap(expectedAddress + offset, regionSize - offset);
//        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//        //when
//        region.map(regionStartPosition);
//        //then
//        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//        //when unmap request
//        assertThat(region.unmap()).isTrue();
//
//        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
//
//        assertThat(region.unmap()).isTrue();
//        inOrder.verify(ioUnmapper, never()).unmap(fileChannel, expectedAddress, regionSize);
//    }
//
//    @Test
//    public void map_and_unmap() {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int) (position % regionSize);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
//        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);
//
//        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);
//
//        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//        //when - map again within the same region and check if had been mapped
//        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);
//
//        //then
//        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//        assertThat(region.unmap()).isTrue();
//
//        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
//
//        assertThat(region.unmap()).isTrue();
//        inOrder.verify(ioUnmapper, never()).unmap(fileChannel, expectedAddress, regionSize);
//    }
//
//    @Test
//    public void map_and_remap() {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int) (position % regionSize);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
//        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);
//
//        //when - request mapping
//        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);
//
//        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//
//        //when send unmap request
//        final long prevRegionStartPosition = regionStartPosition - regionSize;
//        final long prevExpectedAddress = expectedAddress - regionSize;
//        when(ioMapper.map(fileChannel, mapMode, prevRegionStartPosition, regionSize)).thenReturn(prevExpectedAddress);
//        when(fileSizeEnsurer.ensureSize(regionStartPosition)).thenReturn(true);
//
//
//        assertThat(region.map(prevRegionStartPosition)).isNotEqualTo(Region.NULL);
//
//        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
//        inOrder.verify(ioMapper).map(fileChannel, mapMode, prevRegionStartPosition, regionSize);
//
//    }
//
//    @Test
//    public void map_and_close() {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int) (position % regionSize);
//        final long regionStartPosition = position - positionInRegion;
//        assertThat(region.size()).isEqualTo(regionSize);
//
//        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
//        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);
//
//        //when - request mapping
//        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);
//
//        //then
//        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);
//
//        //when
//        region.close();
//
//        //then
//        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
//    }
//
//}