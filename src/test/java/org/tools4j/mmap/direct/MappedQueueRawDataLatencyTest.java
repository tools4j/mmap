/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.direct;

import org.HdrHistogram.Histogram;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.octtech.bw.ByteWatcher;
import org.tools4j.mmap.util.FileUtil;
import org.tools4j.mmap.util.HistogramPrinter;
import org.tools4j.mmap.util.WaitLatch;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@RunWith(Parameterized.class)
public class MappedQueueRawDataLatencyTest {

    private final long messagesPerSecond;
    private final int numberOfBytes;

    private MappedQueue queue;
    private Appender appender;
    private Enumerator enumerator;
    private ByteWatcher byteWatcher;

    @Parameterized.Parameters(name = "{index}: MPS={0}, NBYTES={1}, AFFINITY={2}")
    public static Collection testRunParameters() {
        return Arrays.asList(new Object[][] {
                { 160000, 100},
                { 500000, 100},
                { 160000, 100},
                { 500000, 100},
        });
    }

    public MappedQueueRawDataLatencyTest(final long messagesPerSecond,
                                         final int numberOfBytes) {
        this.messagesPerSecond = messagesPerSecond;
        this.numberOfBytes = numberOfBytes;
    }

    @Before
    public void setup() throws Exception {
//        queue = OneToManyQueue.createOrReplace(FileUtil.tmpDirFile("queue").getAbsolutePath());
//        queue = OneToManyIndexedQueue.createOrReplace(FileUtil.tmpDirFile("queue").getAbsolutePath());
//        queue = OneToManyQueue.createOrReplace(FileUtil.tmpDirFile("queue").getAbsolutePath(), 1L<<12);
        queue = OneToManyIndexedQueue.createOrReplace(FileUtil.tmpDirFile("queue").getAbsolutePath(), 1L<<12, 1L<<12);
        appender = queue.appender();
        enumerator = queue.enumerator();
        //byteWatcher = ByteWatcherPrinter.watch();
    }

    @After
    public void tearDown() throws Exception {
        if (appender != null) {
            appender.close();
            appender = null;
        }
        if (enumerator != null) {
            enumerator.close();
            enumerator = null;
        }
        if (queue != null) {
            queue.close();
            queue = null;
        }
        if (byteWatcher != null) {
            byteWatcher.shutdown();
            byteWatcher = null;
        }
    }

    @Test
    public void latencyTest() throws Exception {
        //given
        final long histogramMax = TimeUnit.SECONDS.toNanos(1);
        final int w = 200000;//warmup
        final int c = 100000;//counted
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
                pubSubReadyLatch.countDown();
                while (!terminate.get()) {
                    if (enumerator.hasNextMessage()) {
                        final MessageReader reader = enumerator.readNextMessage();
                        if (count.get() == 0) t0.set(clock.getAsLong());
                        else if (count.get() == w - 1) t1.set(clock.getAsLong());
                        else if (count.get() == n - 1) t2.set(clock.getAsLong());
                        long sendTime = reader.getInt64();
                        for (int i = 8; i < numberOfBytes; ) {
                            if (i + 8 <= numberOfBytes) {
                                reader.getInt64();
                                i += 8;
                            } else {
                                reader.getInt8();
                                i++;
                            }
                        }
                        reader.finishReadMessage();
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
            final long periodNs = 1000000000/messagesPerSecond;
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
                final MessageWriter writer = appender.appendMessage();
                writer.putInt64(time);
                for (int i = 8; i < numberOfBytes; ) {
                    if (i + 8 <= numberOfBytes) {
                        writer.putInt64(time + i);
                        i += 8;
                    } else {
                        writer.putInt8((byte)(time + i));
                        i++;
                    }
                }
                writer.finishAppendMessage();
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
        final int byteLen = 94;
        final int[] messagesPerSec = {160000, 500000};
//        final int[] messagesPerSec = {160000};
        for (final int mps : messagesPerSec) {
            final MappedQueueRawDataLatencyTest latencyTest = new MappedQueueRawDataLatencyTest(mps, byteLen);
            latencyTest.setup();
            try {
                latencyTest.latencyTest();
            } finally {
                latencyTest.tearDown();
            }
        }
    }
}
