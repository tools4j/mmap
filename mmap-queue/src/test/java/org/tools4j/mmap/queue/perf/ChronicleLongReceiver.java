/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.HdrHistogram.Histogram;
import org.agrona.collections.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.util.HistogramPrinter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChronicleLongReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronicleLongReceiver.class);

    private final int id;
    private final Thread thread;
    private final AtomicReference<Histogram> atomicHistogram = new AtomicReference<>(null);
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    public ChronicleLongReceiver(final int id, final Supplier<ExcerptTailer> pollerFactory, final long warmup, final long messages) {
        Objects.requireNonNull(pollerFactory);
        this.id = id;
        this.thread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MutableBoolean finished = new MutableBoolean(false);

            try (ExcerptTailer tailer = pollerFactory.get()) {
                int received = 0;

                while (!finished.get()) {
                    try (DocumentContext context = tailer.readingDocument()) {
                        if (context.isData()) {
                            final Bytes<?> bytes = context.wire().bytes();
                            final long createdTime = bytes.readLong();
                            long receivedTime = System.nanoTime();

                            received++;

                            final long timeNanos = receivedTime - createdTime;
                            histogram.recordValue(Math.min(timeNanos, maxValue));

                            if (received == warmup) {
                                histogram.reset();
                            }
                            if (received == messages) {
                                finished.set(true);
                            }
                        }
                    }
                }
                atomicHistogram.set(histogram);
            }

        });
        thread.setName("receiver-" + id);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> uncaughtException.set(e));
    }

    public void start() {
        thread.start();
    }

    public boolean join(final long maxWaitTime, final TimeUnit timeUnit) throws Throwable {
        thread.join(timeUnit.toMillis(maxWaitTime));
        if (uncaughtException.get() != null) {
            throw uncaughtException.get();
        }
        return !thread.isAlive();
    }

    public void printHistogram() {
        final Histogram histogram = atomicHistogram.get();
        if (histogram != null) {
            HistogramPrinter.printHistogram("receiver-" + id, histogram);
        }
    }
}
