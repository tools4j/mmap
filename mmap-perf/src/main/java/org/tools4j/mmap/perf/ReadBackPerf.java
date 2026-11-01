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
package org.tools4j.mmap.perf;

import org.HdrHistogram.Histogram;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.ReadingContext;

import java.util.concurrent.TimeUnit;

/**
 * Measures reading back a queue after all senders completed: checking indices with an {@link IndexReader}, reading by
 * index with an {@link EntryReader}, and iterating forward and backward with an {@link EntryIterator}. The first
 * {@code warmup} reads are not recorded.
 */
final class ReadBackPerf {
    private static final long MAX_VALUE = TimeUnit.SECONDS.toNanos(1);

    private final Queue queue;
    private final long messages;
    private final long warmup;
    private final MessageCodec codec;
    private final byte[] payload;

    /**
     * @param queue         the queue to read back
     * @param messages      the messages in the queue, of all senders
     * @param warmup        the reads of each measurement not recorded
     * @param messageLength the message length in bytes
     */
    ReadBackPerf(final Queue queue, final long messages, final long warmup, final int messageLength) {
        this.queue = queue;
        this.messages = messages;
        this.warmup = warmup;
        this.codec = new MessageCodec(messageLength);
        this.payload = new byte[codec.payloadLength()];
    }

    void run() {
        readIndices();
        readByIndex();
        readByIterating(true);
        readByIterating(false);
    }

    private void readIndices() {
        final Histogram histogram = new Histogram(1, MAX_VALUE, 3);
        try (final IndexReader reader = queue.createIndexReader()) {
            for (long i = messages - 1, n = 1; i >= 0; i--, n++) {
                final long time = System.nanoTime();
                final boolean exists = reader.hasEntry(i);
                record(histogram, n, System.nanoTime() - time);
                check(exists, i);
            }
        }
        HistogramPrinter.printHistogram("readIndices", histogram);
    }

    private void readByIndex() {
        final Histogram histogram = new Histogram(1, MAX_VALUE, 3);
        try (final EntryReader reader = queue.createEntryReader()) {
            for (long i = messages - 1, n = 1; i >= 0; i--, n++) {
                final long time = System.nanoTime();
                try (final ReadingContext context = reader.reading(i)) {
                    check(context.hasEntry(), i);
                    codec.wrap(context.buffer(), 0, context.buffer().capacity()).getPayload(payload);
                }
                record(histogram, n, System.nanoTime() - time);
            }
        }
        HistogramPrinter.printHistogram("readByIndex", histogram);
    }

    private void readByIterating(final boolean forward) {
        final Histogram histogram = new Histogram(1, MAX_VALUE, 3);
        long n = 0;
        try (final EntryIterator iterator = queue.createEntryIterator();
             final IterableContext context = forward ? iterator.readingFromFirst() : iterator.readingFromLast().backward()) {
            long time = System.nanoTime();
            for (final Entry entry : context) {
                codec.wrap(entry.buffer(), 0, entry.buffer().capacity()).getPayload(payload);
                final long end = System.nanoTime();
                record(histogram, ++n, end - time);
                time = end;
            }
        }
        check(n == messages, n);
        HistogramPrinter.printHistogram("readByIterating(" + (forward ? "forward" : "backward") + ")", histogram);
    }

    private void record(final Histogram histogram, final long count, final long nanos) {
        histogram.recordValue(Math.min(nanos, MAX_VALUE));
        if (count == warmup) {
            histogram.reset();
        }
    }

    private static void check(final boolean condition, final long value) {
        if (!condition) {
            throw new IllegalStateException("Read back failed at " + value);
        }
    }
}
