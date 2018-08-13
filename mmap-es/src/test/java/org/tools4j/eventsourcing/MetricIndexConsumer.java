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
