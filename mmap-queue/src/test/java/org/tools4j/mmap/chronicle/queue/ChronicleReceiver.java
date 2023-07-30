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
package org.tools4j.mmap.chronicle.queue;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import org.HdrHistogram.Histogram;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.util.HistogramPrinter;
import org.tools4j.mmap.queue.util.MessageCodec;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChronicleReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronicleReceiver.class);

    private final int id;
    private final Thread thread;
    private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    private final AtomicReference<Histogram> atomicHistogram = new AtomicReference<>(null);
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    public ChronicleReceiver(final int id, final Supplier<ExcerptTailer> pollerFactory, final long warmup, final long messages, final int messageLength) {
        Objects.requireNonNull(pollerFactory);
        this.id = id;
        this.thread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            final long maxValue = TimeUnit.SECONDS.toNanos(1);
            final Histogram histogram = new Histogram(1, maxValue, 3);
            final MutableBoolean finished = new MutableBoolean(false);

            try (ExcerptTailer tailer = pollerFactory.get()) {
                int received = 0;
                final MessageCodec messageCodec = new MessageCodec(messageLength);
                final byte[] payload = new byte[messageCodec.payloadLength()];


                while (!finished.get()) {
                    try (DocumentContext context = tailer.readingDocument()) {
                        if (context.isData()) {
                            final Bytes<?> bytes = context.wire().bytes();
                            final long offset = bytes.readPosition();
                            final long addr = bytes.addressForRead(offset);
                            buffer.wrap(addr, messageLength);
                            messageCodec.wrap(buffer);
                            messageCodec.getPayload(payload);
                            buffer.wrap(0, 0);
                            bytes.readPosition(offset + messageLength);

                            long receivedTime = System.nanoTime();
                            received++;

                            final long timeNanos = receivedTime - messageCodec.timestamp();
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

    public void join() throws Throwable {
        thread.join();
        if (uncaughtException.get() != null) {
            throw uncaughtException.get();
        }
    }

    public void printHistogram() {
        final Histogram histogram = atomicHistogram.get();
        if (histogram != null) {
            HistogramPrinter.printHistogram("receiver-" + id, histogram);
        }
    }
}
