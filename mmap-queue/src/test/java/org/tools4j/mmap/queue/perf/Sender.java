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

import org.agrona.hints.ThreadHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.util.MessageCodec;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class Sender {
    private static final double NANOS_IN_SECOND = 1_000_000_000.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(Sender.class);

    private final Thread thread;
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    public Sender(final byte publisherId,
                  final Supplier<Appender> appenderFactory,
                  final long messagesPerSecond,
                  final long messages,
                  final int messageLength) {
        Objects.requireNonNull(appenderFactory);

        this.thread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            final double seconds;
            try (final Appender appender = appenderFactory.get()) {
                final byte pubId = publisherId;
                final long count = messages;
                final int bytes = messageLength;
                final double maxNanosPerMessage = NANOS_IN_SECOND / messagesPerSecond;

                final MessageCodec testMessage = new MessageCodec(bytes);
                final byte[] payload = new byte[testMessage.payloadLength()];

                final long start = System.nanoTime();
                final long lastMessageIdx = count - 1;
                for (int i = 0; i < count; i++) {
                    try (final AppendingContext context = appender.appending(bytes)) {
                        testMessage
                                .wrap(context.buffer(), 0, bytes)
                                .publisherId(pubId)
                                .putPayload(payload)
                                .terminal(i == lastMessageIdx)
                                .timestamp(System.nanoTime());
                        final long index = context.commit(bytes);
                        if (index < 0) {
                            LOGGER.warn("Failed to append message {}, error code {}", i, index);
                        }
                    }

                    final double nanosUntilNow = (i + 1) * maxNanosPerMessage;
                    while (System.nanoTime() - start < nanosUntilNow) {
                        ThreadHints.onSpinWait();
                    }
                }
                seconds = (System.nanoTime() - start)/NANOS_IN_SECOND;
            }
            LOGGER.info("{} messages appended in {}s, which is {} messages/s",
                    messages, (float)seconds, (float)(messages/seconds));
            LOGGER.info("completed: {}", Thread.currentThread());
        });
        thread.setName("sender");
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
}
