/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.perf;

import org.HdrHistogram.Histogram;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.queue.util.MessageCodec;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.impl.Constants;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QueuePerf {
    private static final long MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(300);
    private static final Logger LOGGER = LoggerFactory.getLogger(QueuePerf.class);

    public static void main(final String... args) throws Throwable {
        final Path tempDir = Files.createTempDirectory(QueuePerf.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

        final AtomicInteger mappers = new AtomicInteger();
        final Supplier<AsyncRuntime> mapperRuntimeSupplier = () -> AsyncRuntime.create("mapper-" +
                mappers.getAndIncrement(), BusySpinIdleStrategy.INSTANCE, true);
        final AsyncRuntime unmapperRuntime = AsyncRuntime.create("unmapper", new BackoffIdleStrategy(), false);

        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 1024);   //~4MB
        final int cacheSize = 64;
        final int regionsToMapAhead = 32;
        final QueueConfig config = QueueConfig.configure()
//                .mappingStrategy(MappingStrategy.defaultSyncMappingStrategy())
                .mappingStrategy(cfg -> cfg
                        .regionSize(regionSize)
                        .cacheSize(cacheSize)
                        .regionsToMapAhead(regionsToMapAhead)
//                        .mappingAsyncRuntimeSupplier(mapperRuntimeSupplier)
                        .mappingAsyncRuntime(mapperRuntimeSupplier.get())
                        .unmappingAsyncRuntime(unmapperRuntime)
//                        .mappingAsyncRuntimeSupplier(mapperRuntimeSupplier)
//                        .unmappingAsyncRuntimeSupplier(unmapperRuntimeSupplier)
                )
//                .payloadMappingStrategy(cfg -> cfg
//                        .regionSize(regionSize)
//                        .cacheSize(cacheSize)
//                        .regionsToMapAhead(regionsToMapAhead)
//                        .mappingAsyncRuntime(mapperRuntimeSupplier.get())
//                        .unmappingAsyncRuntime(unmapperRuntimeSupplier.get())
////                        .mappingAsyncRuntimeSupplier(mapperRuntimeSupplier)
////                        .unmappingAsyncRuntimeSupplier(unmapperRuntimeSupplier)
//                )
                .entryReaderConfig(conf -> conf
                        .mappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .regionsToMapAhead(0)
                        )
                )
                .indexReaderConfig(conf -> conf
                        .headerMappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .regionsToMapAhead(0)
                        )
                )
                .expandHeaderFile(false)
                .expandPayloadFiles(false)
                .maxHeaderFileSize(64 * 1024 * 1024)
                .maxPayloadFileSize(64 * 1024 * 1024)
                .headerFilesToCreateAhead(0)
                .payloadFilesToCreateAhead(0)
                .toImmutableQueueConfig();

        final long messagesPerSecond = 1_000_000;
        final int messages = 11_000_000;
        final int warmup = 1_000_000;
        final int messageLength = 10;

        try (final Queue queue = Queue.create(new File(tempDir.toFile(), "perfQ"), config)) {
            LOGGER.info("Queue created: {}", queue);

            final Sender sender = new Sender((byte) 0, queue::createAppender, messagesPerSecond, messages, messageLength);
            final Receiver receiver0 = new Receiver(0, queue::createPoller, warmup, messageLength);
            final Receiver receiver1 = new Receiver(1, queue::createPoller, warmup, messageLength);

            sender.start();
            receiver0.start();
            receiver1.start();

            final long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(MAX_WAIT_MILLIS);
            final long startTime = System.nanoTime();
            assertTrue(sender.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            assertTrue(receiver0.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            assertTrue(receiver1.join(System.nanoTime() + maxWaitNanos - startTime, TimeUnit.NANOSECONDS));
            receiver0.printHistogram();
            receiver1.printHistogram();

            readIndices(queue, messages, warmup);
            readByIndex(queue, messages, warmup, messageLength);
            readByIterate(queue, messages, warmup, messageLength, true);
            readByIterate(queue, messages, warmup, messageLength, false);
        }
        FileUtil.deleteRecursively(tempDir.toFile());
    }

    private static void readByIndex(final Queue queue, final int messages, final int warmup, final int messageLength) {
        try (final EntryReader reader = queue.createEntryReader()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MessageCodec messageCodec = new MessageCodec(messageLength);
            final byte[] payload = new byte[messageCodec.payloadLength()];

            for (int i = messages - 1; i >= 0; i--) {
                if (i == messages - warmup - 1) {
                    histogram.reset();
                }
                final long time = System.nanoTime();
                try (final ReadingContext context = reader.reading(i)) {
                    if (context.hasEntry()) {
                        messageCodec.wrap(context.buffer());
                        messageCodec.getPayload(payload);
                    }
                }
                final long timeNanos = System.nanoTime() - time;
                histogram.recordValue(Math.min(timeNanos, maxValue));
            }
            HistogramPrinter.printHistogram("readByIndex", histogram);
            final boolean exists = reader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
    private static void readByIterate(final Queue queue, final int messages, final int warmup, final int messageLength, final boolean forward) {
        try (final EntryIterator iterator = queue.createEntryIterator()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MessageCodec messageCodec = new MessageCodec(messageLength);
            final byte[] payload = new byte[messageCodec.payloadLength()];

            final long resetPoint = forward ? warmup : messages - warmup - 1;
            try (final IterableContext context = forward ? iterator.readingFromFirst() : iterator.readingFromLast().reverse()) {
                long time = System.nanoTime();
                for (final Entry entry : context) {
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
            HistogramPrinter.printHistogram("readByIterate(forward=" + forward + ")", histogram);
        }
    }

    private static void readIndices(final Queue queue, final int messages, final int warmup) {
        try (final IndexReader indexReader = queue.createIndexReader()) {
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);

            for (int i = messages - 1; i >= 0; i--) {
                if (i == messages - warmup - 1) {
                    histogram.reset();
                }
                final long time = System.nanoTime();
                final boolean exists = indexReader.hasEntry(i);
                assertTrue(exists);
                final long timeNanos = System.nanoTime() - time;
                histogram.recordValue(Math.min(timeNanos, maxValue));
            }
            HistogramPrinter.printHistogram("readIndices", histogram);
            final boolean exists = indexReader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
}