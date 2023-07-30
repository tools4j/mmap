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
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.DocumentContext;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.util.MessageCodec;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class ChronicleSender {
    private static final double NANOS_IN_SECOND = 1_000_000_000.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronicleSender.class);

    private final Thread thread;
    private final UnsafeBuffer bytesWrapper = new UnsafeBuffer(0, 0);
    private final AtomicReference<Throwable> uncaughtException = new AtomicReference<>();

    public ChronicleSender(final byte publisherId,
                           final Supplier<ExcerptAppender> appenderFactory,
                           final long messagesPerSecond,
                           final long messages,
                           final int messageLength) {
        Objects.requireNonNull(appenderFactory);

        this.thread = new Thread(() -> {
            LOGGER.info("started: {}", Thread.currentThread());
            try (ExcerptAppender appender = appenderFactory.get()) {

                final double maxNanosPerMessage = NANOS_IN_SECOND / messagesPerSecond;
                final MessageCodec testMessage = new MessageCodec(messageLength);
                final byte[] payload = new byte[testMessage.payloadLength()];

                final long start = System.nanoTime();
                for (int i = 0; i < messages; i++) {

                    final long lastMessageIdx = messages - 1;
                    long time = System.nanoTime();
                    try (final DocumentContext context = appender.writingDocument()) {
                        try {
                            final Bytes<?> bytes = context.wire().bytes();
                            bytes.ensureCapacity(messageLength);
                            final long dstOffset = bytes.writePosition();
                            final long addr = bytes.addressForWrite(dstOffset);
                            bytesWrapper.wrap(addr, messageLength);
                            testMessage.wrap(bytesWrapper)
                              .publisherId(publisherId)
                              .putPayload(payload)
                              .terminal(i == lastMessageIdx)
                              .timestamp(time);
                            bytesWrapper.wrap(0, 0);
                            bytes.writeSkip(messageLength);
                        } catch (final Throwable t) {
                            context.rollbackOnClose();
                            throw t;
                        }
                    }

                    long end = System.nanoTime();
                    final long waitUntil = start + (long) ((i + 1) * maxNanosPerMessage);
                    while (end < waitUntil) {
                        end = System.nanoTime();
                    }
                }
            }
        });
        thread.setName("sender");
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
}
