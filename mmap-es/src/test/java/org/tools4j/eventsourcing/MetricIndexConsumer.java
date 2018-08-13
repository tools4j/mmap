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
package org.tools4j.eventsourcing;

import org.HdrHistogram.Histogram;
import org.tools4j.eventsourcing.api.Poller;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class MetricIndexConsumer implements Poller.IndexConsumer {
    final int expected;
    final int warmup;
    final AtomicBoolean stop;
    final Histogram histogram = new Histogram(1, TimeUnit.SECONDS.toNanos(1), 3);

    int received = 0;

    public MetricIndexConsumer(final int expected, final int warmup, final AtomicBoolean stop) {
        this.expected = expected;
        this.warmup = warmup;
        this.stop = Objects.requireNonNull(stop);
    }

    @Override
    public void accept(final long index, final int source, final long sourceId, final long eventTimeNanos) {
        long receivedTime = System.nanoTime();
        received ++;
        if (received > warmup) {
            final long timeNanos = receivedTime - eventTimeNanos;
            histogram.recordValue(timeNanos);
        }
        if (received == expected) {
            HistogramPrinter.printHistogram(histogram);
            stop.set(true);
        }
    }
}
