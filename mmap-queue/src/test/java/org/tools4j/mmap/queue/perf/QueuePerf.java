/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.tools4j.mmap.region.config.SharingPolicy;
import org.tools4j.mmap.region.impl.Constants;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.queue.util.ConfigPrinter.printConfig;

/**
 * Best results with
 * <pre>
 *      cacheSize = 256,
 *      regionsToMapAhead = 128,
 *      regionSize=REGION_SIZE_GRANULARITY * 64
 *      maxHeaderFileSize(1024 * 1024 * 1024)
 *      maxPayloadFileSize(1024 * 1024 * 1024)
 *
        receiver-0: Percentiles (micros)
            min    : 0.0
            50%    : 0.125
            90%    : 0.375
            99%    : 0.792
            99.9%  : 5.959
            99.99% : 28.927
            99.999%: 182.911
            max    : 252.543
            count  : 10000000

        receiver-1: Percentiles (micros)
            min    : 0.0
            50%    : 0.125
            90%    : 0.375
            99%    : 0.875
            99.9%  : 6.167
            99.99% : 28.095
            99.999%: 169.599
            max    : 248.191
            count  : 10000000
 *
 *          AsyncRunAheadRegionMapper:...statistics=(async=1091|sync=1|busy=138684).
 * </pre>
 *
 *
 * with smaller region size:
 * <pre>
 *      mapper thread per queue element
 *
 *      cacheSize = 256,
 *      regionsToMapAhead = 128,
 *      aheadMappingCacheSize = 256,
 *      regionSize=REGION_SIZE_GRANULARITY * 16
 *      maxHeaderFileSize(1024 * 1024 * 1024)
 *      maxPayloadFileSize(1024 * 1024 * 1024)

        receiver-0: Percentiles (micros)
            min    : 0.041
            50%    : 0.333
            90%    : 0.416
            99%    : 1.291
            99.9%  : 6.667
            99.99% : 43.263
            99.999%: 300.287
            max    : 373.247
            count  : 10000000

        receiver-1: Percentiles (micros)
            min    : 0.041
            50%    : 0.333
            90%    : 0.416
            99%    : 1.458
            99.9%  : 8.671
            99.99% : 50.751
            99.999%: 379.135
            max    : 402.431

        AsyncRunAheadRegionMapper:...statistics=(async=4365|sync=1|busy=554482).



         QueueConfig:
             regionSize            : 64K
             cacheSize             : 256
             regionsToMapAhead     : 64
             aheadMappingCacheSize : 256
             maxHeaderFileSize     : 1G
             maxPayloadFileSize    : 1G

         receiver-0: Percentiles (micros)
             min    : 0.041
             50%    : 0.333
             90%    : 0.416
             99%    : 1.916
             99.9%  : 6.875
             99.99% : 107.519
             99.999%: 236.927
             max    : 273.407
             count  : 10000000

         receiver-1: Percentiles (micros)
             min    : 0.041
             50%    : 0.333
             90%    : 0.416
             99%    : 1.916
             99.9%  : 7.543
             99.99% : 47.263
             99.999%: 194.175
             max    : 230.399
             count  : 10000000

             statistics=(async=17457|sync=4|busy=1100040)
 * </pre>
 */
public class QueuePerf {
    private static final long MAX_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(300);
    private static final Logger LOGGER = LoggerFactory.getLogger(QueuePerf.class);

    public static void main(final String... args) throws Throwable {
        final Path tempDir = Files.createTempDirectory(QueuePerf.class.getSimpleName());
        tempDir.toFile().deleteOnExit();

//        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 1024);
//        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 64);
//        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 32);
//        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 16);
        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 4);
//        final int regionSize = (int) (Constants.REGION_SIZE_GRANULARITY * 2);
//        final int cacheSize = 4;
//        final int regionsToMapAhead = 2;
//        final int aheadMappingCacheSize = 4;
        final int cacheSize = 8;
        final int regionsToMapAhead = 4;
        final int aheadMappingCacheSize = 8;
//        final int cacheSize = 16;
//        final int regionsToMapAhead = 8;
//        final int aheadMappingCacheSize = 16;
//        final int cacheSize = 32;
//        final int regionsToMapAhead = 16;
//        final int aheadMappingCacheSize = 32;
//        final int cacheSize = 64;
//        final int regionsToMapAhead = 32;
//        final int aheadMappingCacheSize = 64;
//        final int cacheSize = 256;
//        final int regionsToMapAhead = 128;
//        final int aheadMappingCacheSize = 256;
//        final int cacheSize = 256;
//        final int regionsToMapAhead = 64;
//        final int aheadMappingCacheSize = 256;
//        final int regionsToMapAhead = 0;
        //final SharingPolicy sharingPolicy = SharingPolicy.PER_THREAD;
        final SharingPolicy sharingPolicy = SharingPolicy.SHARED;
        final QueueConfig config = QueueConfig.configure()
                .appenderConfig(conf -> conf
                        .mappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(false)
                                .asyncMapping(async -> async
                                        .mappingRuntimeShared(sharingPolicy)
                                        .regionsToMapAhead(regionsToMapAhead)
                                        .aheadMappingCacheSize(aheadMappingCacheSize)
                                )
                        )
                )
                .pollerConfig(conf -> conf
                        .headerMappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(false)
                                .asyncMapping(async -> async
                                        .mappingRuntimeShared(sharingPolicy)
                                        .regionsToMapAhead(regionsToMapAhead)
                                        .aheadMappingCacheSize(aheadMappingCacheSize)
                                )
                        )
                        .payloadMappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(false)
                                .asyncMapping(async -> async
                                        .mappingRuntimeShared(sharingPolicy)
                                        .regionsToMapAhead(regionsToMapAhead)
                                        .aheadMappingCacheSize(aheadMappingCacheSize)
                                )
                        )
//                        .mappingStrategy(cfg -> cfg
//                                .regionSize(regionSize)
//                                .cacheSize(cacheSize)
//                                .deferUnmapping(false)
//                                .asyncMapping(async -> async
//                                        .mappingRuntime(newMappingRuntimeInstance())
//                                        .regionsToMapAhead(regionsToMapAhead)
//                                        .aheadMappingCacheSize(aheadMappingCacheSize))
//                                .asyncUnmapping(true)
//                               //.asyncMapping(false)
//                        )
                )
                .entryReaderConfig(conf -> conf
                        .mappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(true)
                                .asyncMapping(false)
                                .asyncUnmapping(true)
                        )
                )
                .indexReaderConfig(conf -> conf
                        .headerMappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(true)
                                .asyncMapping(false)
                                .asyncUnmapping(true)
                        )
                )
                .entryIteratorConfig(conf -> conf
                        .mappingStrategy(cfg -> cfg
                                .regionSize(regionSize)
                                .cacheSize(cacheSize)
                                .deferUnmapping(false)
                                .asyncMapping(true)
                                .asyncUnmapping(true)
                        )
                )
                .rollHeaderFile(true)
                .rollPayloadFiles(true)
                .expandHeaderFile(false)
                .expandPayloadFiles(false)
//                .maxHeaderFileSize(64 * 1024 * 1024)
//                .maxPayloadFileSize(256 * 1024 * 1024)
//                .maxHeaderFileSize(256L * regionSize)
//                .maxPayloadFileSize(256L * regionSize)
//                .maxHeaderFileSize(1024 * 1024 * 1024)
//                .maxPayloadFileSize(1024 * 1024 * 1024)
//                .maxHeaderFileSize(256 * 1024 * 1024)
//                .maxPayloadFileSize(1024 * 1024 * 1024)
                .maxHeaderFileSize(256 * 1024 * 1024)
                .maxPayloadFileSize(2L * 1024 * 1024 * 1024)
//                .maxHeaderFileSize(64L * 1024 * 1024 * 1024) //for expanding
//                .maxPayloadFileSize(64L * 1024 * 1024 * 1024) //for expanding
                .headerFilesToCreateAhead(0)
                .payloadFilesToCreateAhead(0)
                .toImmutableQueueConfig();

//        final long messagesPerSecond = 3_000_000;
        final long messagesPerSecond = 1_000_000;
//        final long messagesPerSecond = 500_000;
        final int messages = 11_000_000;
        final int warmup = 1_000_000;
//        final int messages = 110_000_000;
//        final int warmup = 10_000_000;
        final int messageLength = 100;

        try (final Queue queue = Queue.create(new File(tempDir.toFile(), "perfQ"), config)) {
//        try (final Queue queue = Queue.create(new File(tempDir.toFile(), "perfQ"))) {
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

            printConfig(config);
            sender.printResult();
            receiver0.printHistogram();
            receiver1.printHistogram();

            LOGGER.info("Queue written: {}", queue);
            readIndices(queue, messages, warmup);
            readByIndex(queue, messages, warmup, messageLength);
            readByIterate(queue, messages, warmup, messageLength, true);
            readByIterate(queue, messages, warmup, messageLength, false);
            LOGGER.info("Queue closing: {}", queue);
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
                final long timeNanos = System.nanoTime() - time;
                assertTrue(exists);
                histogram.recordValue(Math.min(timeNanos, maxValue));
            }
            HistogramPrinter.printHistogram("readIndices", histogram);
            final boolean exists = indexReader.hasEntry((long) messages * 2);
            assertFalse(exists);
        }
    }
}