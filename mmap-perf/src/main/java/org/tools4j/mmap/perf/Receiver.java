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
import org.agrona.DirectBuffer;
import org.tools4j.mmap.perf.PerfQueue.MessageHandler;
import org.tools4j.mmap.perf.PerfQueue.Reader;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Receives messages from all senders and records the latency from claiming the message in the sender until reading it
 * in the receiver, after a warmup phase. Busy-spins while no message is available.
 */
public final class Receiver {
    private static final long MAX_VALUE = TimeUnit.SECONDS.toNanos(1);

    private final Worker worker;
    private final PerfQueue queue;
    private final long warmup;
    private final long messages;
    private final MessageCodec codec;
    private final byte[] payload;
    private final Histogram histogram = new Histogram(1, MAX_VALUE, 3);
    private long received;

    /**
     * Creates a receiver reading all messages from the queue on a thread of its own.
     *
     * @param receiverId    the receiver ID used in the thread name
     * @param threadFactory the factory for the receiver thread
     * @param queue         the queue to read from
     * @param warmup        the number of messages received before recording latencies
     * @param messages      the number of messages to receive, including warmup messages
     * @param messageLength the length of each message in bytes
     */
    public Receiver(final int receiverId,
                    final ThreadFactory threadFactory,
                    final PerfQueue queue,
                    final long warmup,
                    final long messages,
                    final int messageLength) {
        this.worker = new Worker("receiver-" + receiverId, threadFactory, this::run);
        this.queue = requireNonNull(queue);
        this.warmup = warmup;
        this.messages = messages;
        this.codec = new MessageCodec(messageLength);
        this.payload = new byte[codec.payloadLength()];
    }

    Worker worker() {
        return worker;
    }

    private void run() {
        final MessageHandler handler = this::onMessage;
        try (final Reader reader = queue.createReader()) {
            while (received < messages) {
                if (!reader.poll(handler)) {
                    Thread.onSpinWait();
                }
            }
        }
    }

    private void onMessage(final DirectBuffer buffer, final int offset, final int length) {
        final long time = System.nanoTime();
        codec.wrap(buffer, offset, length).getPayload(payload);
        histogram.recordValue(Math.min(time - codec.timestamp(), MAX_VALUE));
        if (++received == warmup) {
            histogram.reset();
        }
    }

    /** Must be invoked only after the receiver has been {@link Worker#join(long) joined} */
    public void printHistogram() {
        HistogramPrinter.printHistogram(worker.name(), histogram);
    }
}
