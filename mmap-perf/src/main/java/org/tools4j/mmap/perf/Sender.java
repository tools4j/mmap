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

import org.tools4j.mmap.perf.PerfQueue.Writer;

import java.util.concurrent.ThreadFactory;

import static java.util.Objects.requireNonNull;

/**
 * Sends messages at a given rate, each containing the time when the message was claimed in the queue. The rate is
 * achieved by busy-spinning until a message is due; senders falling behind send the overdue messages back-to-back.
 */
public final class Sender {
    private static final double NANOS_IN_SECOND = 1_000_000_000.0;

    private final Worker worker;
    private final byte senderId;
    private final PerfQueue queue;
    private final long messagesPerSecond;
    private final long messages;
    private final int messageLength;
    private volatile double achievedMessagesPerSecond;

    /**
     * Creates a sender writing messages to the queue on a thread of its own.
     *
     * @param senderId          the sender ID, encoded in each message
     * @param threadFactory     the factory for the sender thread
     * @param queue             the queue to write to
     * @param messagesPerSecond the message rate, or zero to send as fast as possible
     * @param messages          the number of messages to send
     * @param messageLength     the length of each message in bytes
     */
    public Sender(final int senderId,
                  final ThreadFactory threadFactory,
                  final PerfQueue queue,
                  final long messagesPerSecond,
                  final long messages,
                  final int messageLength) {
        this.worker = new Worker("sender-" + senderId, threadFactory, this::run);
        this.senderId = (byte) senderId;
        this.queue = requireNonNull(queue);
        this.messagesPerSecond = messagesPerSecond;
        this.messages = messages;
        this.messageLength = messageLength;
    }

    Worker worker() {
        return worker;
    }

    private void run() {
        final MessageCodec codec = new MessageCodec(messageLength);
        final byte[] payload = new byte[codec.payloadLength()];
        final double nanosPerMessage = messagesPerSecond > 0 ? NANOS_IN_SECOND / messagesPerSecond : 0;
        try (final Writer writer = queue.createWriter()) {
            final long start = System.nanoTime();
            for (long i = 0; i < messages; i++) {
                final long time = System.nanoTime();
                codec.wrap(writer.claim(messageLength), 0, messageLength)
                        .timestamp(time)
                        .senderId(senderId)
                        .putPayload(payload);
                writer.commit();
                final long due = start + (long) ((i + 1) * nanosPerMessage);
                while (System.nanoTime() - due < 0) {
                    Thread.onSpinWait();
                }
            }
            //measured before closing the writer, closing is not part of sending
            achievedMessagesPerSecond = messages * NANOS_IN_SECOND / (System.nanoTime() - start);
        }
    }

    /** Prints the achieved send rate to standard out. */
    public void printResult() {
        System.out.println(worker.name() + ":\n\tmsg/s  : " + (float) achievedMessagesPerSecond + "\n");
    }
}
