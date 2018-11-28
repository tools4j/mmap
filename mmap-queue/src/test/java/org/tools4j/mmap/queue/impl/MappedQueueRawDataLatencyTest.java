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
package org.tools4j.mmap.queue.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Enumerator;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.queue.util.WaitLatch;
import org.tools4j.mmap.region.api.RegionFactory;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.MappedFile;

@RunWith(Parameterized.class)
public class MappedQueueRawDataLatencyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappedQueueRawDataLatencyTest.class);
    private static final Supplier<RegionRingFactory> SYNC = () -> RegionRingFactory.forSync(RegionFactory.SYNC);

    private enum PollerFactory implements Function<MappedQueue, Poller> {
        ORIGINAL() {
            @Override
            public Poller apply(final MappedQueue mappedQueue) {
                return mappedQueue.poller();
            }
        },
        ADAPTED_FROM_ENUMERATOR() {
            @Override
            public Poller apply(final MappedQueue mappedQueue) {
                return new Poller() {
                    private final Enumerator enumerator = mappedQueue.enumerator();
                    @Override
                    public boolean poll(final DirectBuffer buffer) {
                        if (enumerator.hasNextMessage()) {
                            final DirectBuffer enumBuffer = enumerator.readNextMessage();
                            buffer.wrap(enumBuffer);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void close() {
                        enumerator.close();
                    }
                };
            }
        }
    }

    private final long messagesPerSecond;
    private final int numberOfBytes;
    private final Function<MappedQueue, Poller> pollerFactory;


    private MappedQueue queue;
    private Appender appender;
    private Poller poller;

    @Parameterized.Parameters(name = "{index}: MPS={0}, NBYTES={1}, POLLER={2}")
    public static Collection<?> testRunParameters() {
        return Arrays.asList(new Object[][] {
                { 160000, 100, PollerFactory.ORIGINAL},
                { 500000, 100, PollerFactory.ORIGINAL},
                { 160000, 100, PollerFactory.ORIGINAL},
                { 500000, 100, PollerFactory.ORIGINAL},
                { 160000, 1000, PollerFactory.ORIGINAL},
                { 500000, 1000, PollerFactory.ORIGINAL},
                { 160000, 1000, PollerFactory.ORIGINAL},
                { 500000, 1000, PollerFactory.ORIGINAL},

                { 160000, 100, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 500000, 100, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 160000, 100, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 500000, 100, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 160000, 1000, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 500000, 1000, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 160000, 1000, PollerFactory.ADAPTED_FROM_ENUMERATOR},
                { 500000, 1000, PollerFactory.ADAPTED_FROM_ENUMERATOR},

        });
    }

    public MappedQueueRawDataLatencyTest(final long messagesPerSecond,
                                         final int numberOfBytes,
                                         final Function<MappedQueue, Poller> pollerFactory) {
        this.messagesPerSecond = messagesPerSecond;
        this.numberOfBytes = numberOfBytes;
        this.pollerFactory = Objects.requireNonNull(pollerFactory);
    }

    @Before
    public void setup() throws Exception {
        final String fileName = FileUtil.sharedMemDir("regiontest").getAbsolutePath();
        LOGGER.info("File: {}", fileName);
        final int regionSize = (int) Math.max(MappedFile.REGION_SIZE_GRANULARITY, 1L << 16);
        LOGGER.info("regionSize: {}", regionSize);

        final RegionRingFactory regionRingFactory = SYNC.get();
        queue = new MappedQueue(fileName, regionSize, regionRingFactory, 4, 1,64L * 16 * 1024 * 1024 * 4);
        regionRingFactory.onComplete();

        appender = queue.appender();
        poller = pollerFactory.apply(queue);
    }

    @After
    public void tearDown() throws Exception {
        if (appender != null) {
            appender.close();
            appender = null;
        }
        if (poller != null) {
            poller.close();
            poller = null;
        }
        if (queue != null) {
            queue.close();
            queue = null;
        }
    }

    @Test
    public void latencyTest() throws Exception {
        //given
        final long histogramMax = TimeUnit.SECONDS.toNanos(1);
        final int w = 100000;//warmup
        final int c = 200000;//counted
//        final int w = 500;//warmup
//        final int c = 500;//counted
        final int n = w+c;
        final long maxTimeToRunSeconds = 30;

        System.out.println("\twarmup + count      : " + w + " + " + c + " = " + n);
        System.out.println("\tmessagesPerSecond   : " + messagesPerSecond);
        System.out.println("\tmessageSize         : " + numberOfBytes + " bytes");
        System.out.println("\tmaxTimeToRunSeconds : " + maxTimeToRunSeconds);
        System.out.println();

        final AtomicBoolean terminate = new AtomicBoolean(false);
        final LongSupplier clock = System::nanoTime;
        final Histogram histogram = new Histogram(1, histogramMax, 3);
        final WaitLatch pubSubReadyLatch = new WaitLatch(2);
        final WaitLatch receivedAllLatch = new WaitLatch(1);
        final AtomicInteger count = new AtomicInteger();

        //when
        final Thread subscriberThread = new Thread(() -> {
            try {
                final AtomicLong t0 = new AtomicLong();
                final AtomicLong t1 = new AtomicLong();
                final AtomicLong t2 = new AtomicLong();
                final DirectBuffer buffer = new UnsafeBuffer();
                pubSubReadyLatch.countDown();
                while (!terminate.get()) {
                    if (poller.poll(buffer)) {
                        if (count.get() == 0) t0.set(clock.getAsLong());
                        else if (count.get() == w - 1) t1.set(clock.getAsLong());
                        else if (count.get() == n - 1) t2.set(clock.getAsLong());
                        int pos = 0;
                        long sendTime = buffer.getLong(pos);
                        while (pos < numberOfBytes) {
                            if (pos + 8 <= numberOfBytes) {
                                buffer.getLong(pos);pos+=8;
                                pos += 8;
                            } else {
                                buffer.getByte(pos);pos+=1;
                                pos++;
                            }
                        }
                        buffer.wrap(0, 0);
                        final long time = clock.getAsLong();
                        final int cnt = count.incrementAndGet();
                        if (cnt <= n) {
                            if (time - sendTime > histogramMax) {
                                //throw new RuntimeException("bad data in message " + cnt + ": time=" + time + ", sendTime=" + sendTime + ", dt=" + (time - sendTime));
                                histogram.recordValue(histogramMax);
                            } else {
                                histogram.recordValue(time - sendTime);
                            }
                        }
                        if (cnt == w) {
                            histogram.reset();
                        }
                        if (count.get() >= n) {
                            receivedAllLatch.countDown();
                            break;
                        }
                    }
                }
                final int cnt = count.get();
                System.out.println((t2.get() - t0.get())/1000f + " us total receiving time (" + cnt + " messages, " + (t2.get() - t0.get())/(1000f*cnt) + " us/message, " + cnt/((t2.get()-t0.get())/1000000000f) + " messages/second)");
            } catch (final Throwable t) {
                t.printStackTrace();
                System.err.println("failed after receiving " + count + " messages");
                receivedAllLatch.countDown();
            }
        });
        subscriberThread.setName("subscriber-thread");
        subscriberThread.start();

        //publisher
        final Thread publisherThread = new Thread(() -> {
            final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[numberOfBytes]);
            final double periodNs = 1000000000.0/messagesPerSecond;
            pubSubReadyLatch.countDown();
            pubSubReadyLatch.awaitThrowOnTimeout(5, TimeUnit.SECONDS);
            long cnt = 0;
            final long t0 = clock.getAsLong();
            while (cnt < n && !terminate.get()) {
                long tCur = clock.getAsLong();
                while (tCur - t0 < cnt * periodNs) {
                    tCur = clock.getAsLong();
                }
                final long time = clock.getAsLong();
                int pos = 0;
                buffer.putLong(pos, time);
                while (pos < numberOfBytes) {
                    if (pos + 8 <= numberOfBytes) {
                        buffer.putLong(pos, time + pos);
                        pos += 8;
                    } else {
                        buffer.putByte(pos, (byte)(time + pos));
                        pos++;
                    }
                }
                appender.append(buffer, 0, numberOfBytes);
                cnt++;
            }
            final long t1 = clock.getAsLong();
            System.out.println((t1 - t0) / 1000f + " us total publishing time (cnt=" + cnt + ", " + (t1 - t0)/(1000f * cnt) + " us/message, " + (cnt * 1000000000f) / (t1 - t0) + " messages/second)");
        });
        publisherThread.setName("publisher-thread");
        publisherThread.start();;

        //then
        if (!receivedAllLatch.await(maxTimeToRunSeconds, TimeUnit.SECONDS)) {
            terminate.set(true);
            System.err.println("timeout after receiving " + count + " messages.");
            throw new RuntimeException("simulation timed out");
        }
        terminate.set(true);

        publisherThread.join(2000);

        System.out.println();
        HistogramPrinter.printHistogram(histogram);
    }

    public static void main(String... args) throws Exception {
        final int byteLen = 2000;
//        final int[] messagesPerSec = {160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000, 160000};
        final int[] messagesPerSec = {160000, 160000, 160000, 160000, 160000, 160000};
//        final int[] messagesPerSec = {160000, 500000};
//        final int[] messagesPerSec = {160000};
        for (final int mps : messagesPerSec) {
            final MappedQueueRawDataLatencyTest latencyTest = new MappedQueueRawDataLatencyTest(mps, byteLen, MappedQueue::poller);
            latencyTest.setup();
            try {
                latencyTest.latencyTest();
            } finally {
                latencyTest.tearDown();
            }
        }
    }
}