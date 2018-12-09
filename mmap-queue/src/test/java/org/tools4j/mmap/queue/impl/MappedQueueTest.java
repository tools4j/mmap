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

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.region.impl.MappedFile;

public class MappedQueueTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappedQueueTest.class);

    public static void main(String... args) throws Exception {
        final String fileName = FileUtil.sharedMemDir("regiontest").getAbsolutePath();
        LOGGER.info("File: {}", fileName);
        final int regionSize = (int) MappedFile.REGION_SIZE_GRANULARITY * 1024;
        LOGGER.info("regionSize: {}", regionSize);

        final MappedQueue mappedQueue = MappedQueue.syncRingQueue(fileName, regionSize, 64L * 16 * 1024 * 1024 * 4);
//        final MappedQueue mappedQueue = MappedQueue.asyncRingQueue(RegionFactory.AsyncRing.ASYNC_RING_VOLATILE_REQUEST, fileName, regionSize, 64L * 16 * 1024 * 1024 * 4);

        final Appender appender = mappedQueue.appender();
        final Poller poller = mappedQueue.poller();


//        final String testMessage = "#------------------------------------------------#\n";
        final String testMessage = message(100);

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(8 + testMessage.getBytes().length);
        final UnsafeBuffer unsafeBuffer = new UnsafeBuffer(byteBuffer);
        unsafeBuffer.putBytes(8, testMessage.getBytes());

        final int size = 8 + testMessage.getBytes().length;

//        final Histogram histogram = new Histogram(1, TimeUnit.MINUTES.toNanos(10), 3);

        final long messagesPerSecond = 90000;
        final double maxNanosPerMessage = 1000000000.0 / messagesPerSecond;
        final int messages = 2000000;
        final int warmup = 100000;


        final Thread pollerThread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            final Histogram histogram = new Histogram(1, TimeUnit.SECONDS.toNanos(1), 3);
            long lastTimeNanos = 0;
            final DirectBuffer messageBuffer = new UnsafeBuffer();
            int received = 0;
            while (true) {
                if (poller.poll(messageBuffer)) {
                    received++;
                    long end = System.nanoTime();
                    if (received > warmup) {
                        final long startNanos = messageBuffer.getLong(0);
                        final long timeNanos = end - startNanos;
                        histogram.recordValue(timeNanos);
                        lastTimeNanos = Long.max(lastTimeNanos, timeNanos);
                    }
                    if (received == messages) break;
                }
            }
            HistogramPrinter.printHistogram(histogram);
            System.out.println("lastTimeNanos " + lastTimeNanos);
        });
        pollerThread.setName("async-processor");
        pollerThread.setDaemon(true);
        pollerThread.setUncaughtExceptionHandler((t, e) -> LOGGER.error("{}", e));
        pollerThread.start();


        final long start = System.nanoTime();
        for (int i = 0; i < messages; i++) {
            final long time = System.nanoTime();
            unsafeBuffer.putLong(0, time);
            appender.append(unsafeBuffer, 0, size);
            long end = System.nanoTime();
            final long waitUntil = start + (long)((i+1)*maxNanosPerMessage);
            while (end < waitUntil) {
                end = System.nanoTime();
            }
        }

        pollerThread.join();
    }

    private static String message(final int length) {
        final StringBuilder message = new StringBuilder(length);
        message.setLength(length);
        return message.toString();
    }
}