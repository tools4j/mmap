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
package org.tools4j.mmap.longQueue.impl;

import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.LongQueue;
import org.tools4j.mmap.queue.api.LongReader;
import org.tools4j.mmap.queue.impl.LongQueueBuilder;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.RegionRingFactories;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class LongQueuePerf {
    private static final Logger LOGGER = LoggerFactory.getLogger(LongQueuePerf.class);

    public static void main(final String... args) throws Throwable {
        final Path tempDir = Files.createTempDirectory(LongQueuePerf.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

        try (AsyncRuntime asyncRuntime = AsyncRuntime.createDefault()) {
            final RegionRingFactory regionRingFactory = RegionRingFactories.async(asyncRuntime);
            final String name = "perf";

            final LongQueueBuilder builder = LongQueue.builder(name, tempDir.toString(), regionRingFactory);

            final long messagesPerSecond = 50_000;
            final int messages = 20_000_000;
            final int warmup = 200_000;

            try (LongQueue queue = builder.build()) {
                LOGGER.info("Queue created: {}", queue);

                final LongSender sender = new LongSender(queue::createAppender, messagesPerSecond, messages);
                final LongReceiver receiver0 = new LongReceiver(0, queue::createPoller, warmup, messages);
                final LongReceiver receiver1 = new LongReceiver(1, queue::createPoller, warmup, messages);

                sender.start();
                receiver0.start();
                receiver1.start();

                sender.join();
                receiver0.join();
                receiver1.join();
                receiver0.printHistogram();
                receiver1.printHistogram();

                readByIndex(queue, messages, warmup);
            }
        }
        FileUtil.deleteRecursively(tempDir.toFile());
    }

    private static void readByIndex(final LongQueue queue, final int messages, final int warmup) {
        try (LongReader reader = queue.createReader()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);

            for (int i = messages - 1; i >= 0; i--) {
                final long time = System.nanoTime();
                reader.read(i);
                final long timeNanos = System.nanoTime() - time;
                histogram.recordValue(Math.min(timeNanos, maxValue));
                if (i == messages - warmup) {
                    histogram.reset();
                }
            }
            HistogramPrinter.printHistogram("readAtIndex", histogram);
            final boolean exists = reader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
}