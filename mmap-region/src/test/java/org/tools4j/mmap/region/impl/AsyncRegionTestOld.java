/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
///*
// * The MIT License (MIT)
// *
// * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
//import org.agrona.DirectBuffer;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.mockito.InOrder;
//import org.mockito.Mock;
//import org.mockito.Mockito;
//import org.mockito.verification.VerificationMode;
//import org.tools4j.mmap.region.api.FileMapper;
//
//import java.util.Objects;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertFalse;
//import static org.junit.jupiter.api.Assertions.assertTrue;
//import static org.mockito.ArgumentMatchers.anyInt;
//import static org.mockito.ArgumentMatchers.anyLong;
//import static org.mockito.ArgumentMatchers.same;
//import static org.mockito.Mockito.inOrder;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.times;
//import static org.mockito.Mockito.when;
//import static org.mockito.MockitoAnnotations.openMocks;
//
//public class AsyncRegionTestOld {
//
//    interface AsyncRegionFactory {
//        AsyncRegion create(FileMapper fileMapper, int length, long timeout, TimeUnit timeUnits);
//    }
//
//    @SuppressWarnings("unused")
//    enum TestFactory {
//        VOLATILE_STATE_MACHINE_REGION(DefaultRegionViewport::new);
//
//        private final AsyncRegionFactory factory;
//
//        TestFactory(final AsyncRegionFactory factory) {
//            this.factory = Objects.requireNonNull(factory);
//        }
//    }
//
//    @Mock
//    private DirectBuffer directBuffer;
//    @Mock
//    private FileMapper fileMapper;
//
//    private InOrder inOrder;
//
//    private AsyncRegion region;
//
//    private final int length = 128;
//
//    @BeforeEach
//    public void setUp() {
//        openMocks(this);
//
//        long timeoutMillis = 2000;
//        region = TestFactory.VOLATILE_STATE_MACHINE_REGION.factory.create(fileMapper, length, timeoutMillis, TimeUnit.MILLISECONDS);
//        inOrder = inOrder(directBuffer, fileMapper);
//    }
//
//    @Test
//    public void wrap_false_when_no_async_mapping() {
//
//        final boolean wrapped = region.wrap(10, directBuffer);
//
//        assertFalse(wrapped);
//        inOrder.verify(directBuffer, never()).wrap(anyLong(), anyInt());
//        inOrder.verify(fileMapper, never()).map(anyLong(), same(length));
//    }
//
//    @Test
//    public void wrap_map_and_unmap() throws Exception {
//        //given
//        final AtomicBoolean wrapped = new AtomicBoolean();
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int)(position % length);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//
//        //when - request mapping
//        final Thread thread1 = new Thread(() -> wrapped.set(region.wrap(position, directBuffer)));
//
//        //and when - process mapping request
//        final Thread thread2 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread1.start();
//        thread2.start();
//        thread1.join();
//        thread2.join();
//
//        //then
//        assertTrue(wrapped.get());
//        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);
//        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, length - positionInRegion);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//        //check that region is mapped
//        assertTrue(region.map(regionStartPosition));
//
//        //when - wrap again within the same region
//        final int offset = 4;
//        region.wrap(regionStartPosition + offset, directBuffer);
//
//        //then
//        inOrder.verify(directBuffer).wrap(expectedAddress + offset, length - offset);
//        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);
//
//        //when
//        //check that region is mapped
//        assertTrue(region.map(regionStartPosition));
//        //then
//        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//
//        //when send unmap request
//        assertFalse(region.unmap());
//
//        //and when - process unmapping request
//        final Thread thread3 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread3.start();
//        thread3.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
//
//        assertTrue(region.unmap());
//        inOrder.verify(fileMapper, times(0)).unmap(expectedAddress, regionStartPosition, length);
//    }
//
//    @Test
//    public void map_and_unmap() throws Exception {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int)(position % length);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//
//        //when - request mapping
//        assertFalse(region.map(regionStartPosition));
//
//        //and when - process mapping request
//        final Thread thread1 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread1.start();
//        thread1.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//
//        //once region is mapped, wrap should be non-blocking
//        assertTrue(region.wrap(position, directBuffer));
//        inOrder.verify(directBuffer).wrap(expectedAddress + positionInRegion, length - positionInRegion);
//
//        //when - map again within the same region and check if had been mapped
//        assertTrue(region.map(regionStartPosition));
//
//        //then
//        inOrder.verify(fileMapper, times(0)).map(regionStartPosition, length);
//
//        //check that there is nothing to process
//        assertFalse(region.processRequest());
//
//        //when send unmap request
//        assertFalse(region.unmap());
//
//        //and when - process unmapping request
//        final Thread thread2 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread2.start();
//        thread2.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
//
//        assertTrue(region.unmap());
//        inOrder.verify(fileMapper, times(0)).unmap(expectedAddress, regionStartPosition, length);
//    }
//
//    @Test
//    public void map_and_remap() throws Exception {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int)(position % length);
//        final long regionStartPosition = position - positionInRegion;
//
//        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);
//
//        //when - request mapping
//        assertFalse(region.map(regionStartPosition));
//
//        //and when - process mapping request
//        final Thread thread1 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread1.start();
//        thread1.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);
//
//        //when send unmap request
//        final long prevRegionStartPosition = regionStartPosition - length;
//        final long prevExpectedAddress = expectedAddress - length;
//        when(fileMapper.map(prevRegionStartPosition, length)).thenReturn(prevExpectedAddress);
//
//        assertFalse(region.map(prevRegionStartPosition));
//
//        //and when - process unmapping request
//        final Thread thread2 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread2.start();
//        thread2.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
//        inOrder.verify(fileMapper, times(1)).map(prevRegionStartPosition, length);
//    }
//
//    @Test
//    public void map_and_close() throws Exception {
//        //given
//        final long expectedAddress = 1024;
//        final long position = 4567;
//        final int positionInRegion = (int)(position % length);
//        final long regionStartPosition = position - positionInRegion;
//        assertEquals(length, region.size());
//
//        when(fileMapper.map(regionStartPosition, length)).thenReturn(expectedAddress);
//
//        //when - request mapping
//        assertFalse(region.map(regionStartPosition));
//
//        //and when - process mapping request
//        final Thread thread1 = new Thread(() -> {
//            boolean processed;
//            do {
//                processed = region.processRequest();
//            } while (!processed);
//        });
//        thread1.start();
//        thread1.join();
//
//        //then
//        inOrder.verify(fileMapper, times(1)).map(regionStartPosition, length);
//
//        //when
//        region.close();
//
//        //and
//        assertTrue(region.processRequest());
//
//        //then
//        inOrder.verify(fileMapper, times(1)).unmap(expectedAddress, regionStartPosition, length);
//    }
//
//    private static VerificationMode once() {
//        return Mockito.times(1);
//    }
//
//}