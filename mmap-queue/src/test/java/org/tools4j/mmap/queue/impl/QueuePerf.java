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
package org.tools4j.mmap.queue.impl;

import org.HdrHistogram.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Direction;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.queue.api.Reader.Entry;
import org.tools4j.mmap.queue.api.Reader.IterableContext;
import org.tools4j.mmap.queue.api.Reader.ReadingContext;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.queue.util.MessageCodec;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.RegionRingFactories;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class QueuePerf {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueuePerf.class);

    public static void main(final String... args) throws Throwable {
        final Path tempDir = Files.createTempDirectory(QueuePerf.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

        try (AsyncRuntime asyncRuntime = AsyncRuntime.createDefault()) {
            final RegionRingFactory regionRingFactory = RegionRingFactories.async(asyncRuntime);
            final String name = "sample";

            final QueueBuilder builder = Queue.builder(name, tempDir.toString(), regionRingFactory);

            final long messagesPerSecond = 2_000_000;
            final int messages = 20_000_000;
            final int warmup = 200_000;
            final int messageLength = 256;

            try (Queue queue = builder.build()) {
                LOGGER.info("Queue created: {}", queue);

                final Sender sender = new Sender((byte) 0, queue::createAppender, messagesPerSecond, messages, messageLength);
                final Receiver receiver0 = new Receiver(0, queue::createPoller, warmup, messageLength);
                final Receiver receiver1 = new Receiver(1, queue::createPoller, warmup, messageLength);

                sender.start();
                receiver0.start();
                receiver1.start();

                sender.join();
                receiver0.join();
                receiver1.join();
                receiver0.printHistogram();
                receiver1.printHistogram();

                readByIndex(queue, messages, warmup, messageLength);
                readByIterate(queue, messages, warmup, messageLength, Direction.FORWARD);
                readByIterate(queue, messages, warmup, messageLength, Direction.BACKWARD);
            }
        }
        FileUtil.deleteRecursively(tempDir.toFile());
    }

    private static void readByIndex(final Queue queue, final int messages, final int warmup, final int messageLength) {
        try (Reader reader = queue.createReader()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MessageCodec messageCodec = new MessageCodec(messageLength);
            final byte[] payload = new byte[messageCodec.payloadLength()];

            for (int i = messages - 1; i >= 0; i--) {
                if (i == messages - warmup - 1) {
                    histogram.reset();
                }
                final long time = System.nanoTime();
                try(ReadingContext context = reader.reading(i)) {
                    if (context.hasEntry()) {
                        messageCodec.wrap(context.buffer());
                        messageCodec.getPayload(payload);
                    }
                }
                final long timeNanos = System.nanoTime() - time;
                histogram.recordValue(Math.min(timeNanos, maxValue));
            }
            HistogramPrinter.printHistogram("readAtIndex", histogram);
            final boolean exists = reader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
    private static void readByIterate(final Queue queue, final int messages, final int warmup, final int messageLength, final Direction direction) {
        try (Reader reader = queue.createReader()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MessageCodec messageCodec = new MessageCodec(messageLength);
            final byte[] payload = new byte[messageCodec.payloadLength()];

            final long resetPoint = direction == Direction.FORWARD ? warmup : messages - warmup - 1;
            try (final IterableContext context = (direction == Direction.FORWARD ? reader.readingFromFirst() : reader.readingFromLast())) {
                long time = System.nanoTime();
                for (final Entry entry : context.iterate(direction)) {
                    if (entry.index() == resetPoint) {
                        histogram.reset();
                        time = System.nanoTime();
                    }
                    messageCodec.wrap(entry.buffer());
                    messageCodec.getPayload(payload);
                    final long timeNanos = System.nanoTime() - time;
                    histogram.recordValue(Math.min(timeNanos, maxValue));
                    time = System.nanoTime();
                }
            }
            HistogramPrinter.printHistogram("readByIterate(" + direction + ")", histogram);
            final boolean exists = reader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
}