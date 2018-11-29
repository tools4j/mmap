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
package org.tools4j.mmap.region.impl;

import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.tools4j.mmap.region.api.AsyncRegion;
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.MappableRegion;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.spockito.Spockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@Spockito.Unroll({
        "| testFactory                   |",
        "|-------------------------------|",
        "| VOLATILE_STATE_MACHINE_REGION |",
        "| ATOMIC_STATE_MACHINE_REGION   |",
        "| VOLATILE_REQUEST_REGION       |",
})
@Spockito.Name("[{row}]: {testFactory}")
@RunWith(Spockito.class)
public class AsyncRegionTest {

    interface AsyncRegionFactory {
        AsyncRegion create(Supplier<FileChannel> fileChannelSupplier,
                           MappableRegion.IoMapper ioMapper,
                           MappableRegion.IoUnmapper ioUnmapper,
                           FileSizeEnsurer fileSizeEnsurer,
                           FileChannel.MapMode mapMode,
                           int length,
                           long timeout,
                           TimeUnit timeUnits);
    }

    @SuppressWarnings("unused")
    enum TestFactory {
        VOLATILE_STATE_MACHINE_REGION(AsyncVolatileStateMachineRegion::new),
        ATOMIC_STATE_MACHINE_REGION(AsyncAtomicStateMachineRegion::new),
        VOLATILE_REQUEST_REGION(AsyncVolatileRequestRegion::new);

        private AsyncRegionFactory factory;

        TestFactory(final AsyncRegionFactory factory) {
            this.factory = Objects.requireNonNull(factory);
        }
    }

    @Spockito.Ref
    @SuppressWarnings("unused")
    private TestFactory testFactory;

    @Mock
    private DirectBuffer directBuffer;
    @Mock
    private FileChannel fileChannel;
    @Mock
    private MappableRegion.IoMapper ioMapper;
    @Mock
    private MappableRegion.IoUnmapper ioUnmapper;
    @Mock
    private FileSizeEnsurer fileSizeEnsurer;

    private InOrder inOrder;

    private AsyncRegion region;

    private int regionSize = 128;
    private FileChannel.MapMode mapMode = FileChannel.MapMode.READ_WRITE;
    private long timeoutMillis = 200;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        region = testFactory.factory.create(() -> fileChannel,
                ioMapper, ioUnmapper, fileSizeEnsurer,
                mapMode, regionSize, timeoutMillis, TimeUnit.MILLISECONDS);
        inOrder = inOrder(directBuffer, fileChannel, ioMapper, ioUnmapper, fileSizeEnsurer);
    }

    @Test
    public void wrap_false_when_no_async_mapping() {

        final int len = region.wrap(10, directBuffer);

        assertThat(len).isEqualTo(0);
        inOrder.verify(directBuffer, never()).wrap(anyLong(), anyInt());
        inOrder.verify(ioMapper, never()).map(same(fileChannel), same(mapMode), anyLong(), same(regionSize));
    }

    @Test
    public void wrap_map_and_unmap() throws Exception {
        //given
        final AtomicInteger length = new AtomicInteger();
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();

        //when - request mapping
        final Thread thread1 = new Thread(() -> {
            length.set(region.wrap(position, directBuffer));
        });

        //and when - process mapping request
        final Thread thread2 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        //then
        assertThat(length.get()).isNotEqualTo(0);
        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);
        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, regionSize - positionInRegion);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();
        //check that region is mapped
        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);

        //when - wrap again within the same region
        final int offset = 4;
        region.wrap(regionStartPosition + offset, directBuffer);

        //then
        inOrder.verify(directBuffer).wrap(expectedAddress + offset, regionSize - offset);
        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);

        //when
        //check that region is mapped
        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);
        //then
        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();

        //when send unmap request
        assertThat(region.unmap()).isFalse();

        //and when - process unmapping request
        final Thread thread3 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread3.start();
        thread3.join();

        //then
        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);

        assertThat(region.unmap()).isNotEqualTo(Region.NULL);
        inOrder.verify(ioUnmapper, never()).unmap(fileChannel, expectedAddress, regionSize);
    }

    @Test
    public void map_and_unmap() throws Exception {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();

        //when - request mapping
        assertThat(region.map(regionStartPosition)).isEqualTo(Region.NULL);

        //and when - process mapping request
        final Thread thread1 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread1.start();
        thread1.join();

        //then
        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();

        //once region is mapped, wrap should be non-blocking
        assertThat(region.wrap(position, directBuffer)).isNotEqualTo(Region.NULL);
        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, regionSize - positionInRegion);

        //when - map again within the same region and check if had been mapped
        assertThat(region.map(regionStartPosition)).isNotEqualTo(Region.NULL);

        //then
        inOrder.verify(ioMapper, never()).map(fileChannel, mapMode, regionStartPosition, regionSize);

        //check that there is nothing to process
        assertThat(region.processRequest()).isFalse();

        //when send unmap request
        assertThat(region.unmap()).isFalse();

        //and when - process unmapping request
        final Thread thread2 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread2.start();
        thread2.join();

        //then
        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);

        assertThat(region.unmap()).isNotEqualTo(Region.NULL);
        inOrder.verify(ioUnmapper, never()).unmap(fileChannel, expectedAddress, regionSize);
    }

    @Test
    public void map_and_remap() throws Exception {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);

        //when - request mapping
        assertThat(region.map(regionStartPosition)).isEqualTo(Region.NULL);

        //and when - process mapping request
        final Thread thread1 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread1.start();
        thread1.join();

        //then
        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);


        //when send unmap request
        final long prevRegionStartPosition = regionStartPosition - regionSize;
        final long prevExpectedAddress = expectedAddress - regionSize;
        when(ioMapper.map(fileChannel, mapMode, prevRegionStartPosition, regionSize)).thenReturn(prevExpectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition)).thenReturn(true);

        assertThat(region.map(prevRegionStartPosition)).isEqualTo(Region.NULL);

        //and when - process unmapping request
        final Thread thread2 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread2.start();
        thread2.join();

        //then
        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
        inOrder.verify(ioMapper).map(fileChannel, mapMode, prevRegionStartPosition, regionSize);
    }

    @Test
    public void map_and_close() throws Exception {
        //given
        final long expectedAddress = 1024;
        final long position = 4567;
        final int positionInRegion = (int) (position % regionSize);
        final long regionStartPosition = position - positionInRegion;
        assertThat(region.size()).isEqualTo(regionSize);

        when(ioMapper.map(fileChannel, mapMode, regionStartPosition, regionSize)).thenReturn(expectedAddress);
        when(fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)).thenReturn(true);

        //when - request mapping
        assertThat(region.map(regionStartPosition)).isEqualTo(Region.NULL);

        //and when - process mapping request
        final Thread thread1 = new Thread(() -> {
            boolean processed;
            do {
                processed = region.processRequest();
            } while (!processed);
        });
        thread1.start();
        thread1.join();

        //then
        inOrder.verify(ioMapper).map(fileChannel, mapMode, regionStartPosition, regionSize);

        //when
        region.close();

        //and
        assertThat(region.processRequest()).isTrue();

        //then
        inOrder.verify(ioUnmapper).unmap(fileChannel, expectedAddress, regionSize);
    }

}