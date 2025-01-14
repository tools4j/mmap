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
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.queue.util.MessageCodec;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Receiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(Receiver.class);

    private final int id;
    private final Thread thread;
    private final AtomicReference<Histogram> atomicHistogram = new AtomicReference<>(null);
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    public Receiver(final int id, final Supplier<Poller> pollerFactory, final long warmup, final int messageLength) {
        Objects.requireNonNull(pollerFactory);
        this.id = id;
        this.thread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            final MutableBoolean finished = new MutableBoolean(false);
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);

            final EntryHandler messageHandler = new EntryHandler() {
                final MessageCodec messageCodec = new MessageCodec(messageLength);
                final byte[] payload = new byte[messageCodec.payloadLength()];

                int received = 0;

                @Override
                public long onEntry(final long index, final DirectBuffer buffer, final int offset, final int length) {
                    final long end = System.nanoTime();
                    received++;
                    final MessageCodec code = messageCodec;
                    code.wrap(buffer, offset, length);
                    code.getPayload(payload);

                    final long timeNanos = end - code.timestamp();
                    histogram.recordValue(Math.min(timeNanos, maxValue));

                    if (received == warmup) {
                        histogram.reset();
                    }
                    if (code.terminal()) {
                        finished.set(true);
                    }
                    return Move.NEXT;
                }
            };

            try (final Poller poller = pollerFactory.get()) {
                while (!finished.get()) {
                    poller.poll(messageHandler);
                }
                atomicHistogram.set(histogram);
            }
            LOGGER.info("completed: {}", Thread.currentThread());
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
