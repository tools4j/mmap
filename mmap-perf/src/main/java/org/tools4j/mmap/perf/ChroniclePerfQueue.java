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

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.DocumentContext;
import net.openhft.chronicle.wire.WireType;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.io.File;

import static java.util.Objects.requireNonNull;

/**
 * {@link PerfQueue} for a Chronicle Queue, writing through an {@link ExcerptAppender} and reading through an
 * {@link ExcerptTailer}, both accessing the document bytes directly like the mmap queue does.
 */
public final class ChroniclePerfQueue implements PerfQueue {

    private static final WireType WIRE_TYPE = WireType.BINARY_LIGHT;

    private final ChronicleQueue queue;

    public ChroniclePerfQueue(final File directory) {
        this.queue = ChronicleQueue.singleBuilder(directory)
                .wireType(WIRE_TYPE)
                .build();
    }

    @Override
    public Writer createWriter() {
        return new ChronicleWriter(queue.acquireAppender());
    }

    @Override
    public Reader createReader() {
        return new ChronicleReader(queue.createTailer());
    }

    @Override
    public void describeSettings(final StringBuilder sb) {
        sb.append("queue                 : chronicle\n");
        sb.append("wireType              : ").append(WIRE_TYPE).append('\n');
        sb.append("rollCycle             : ").append(queue.rollCycle()).append('\n');
    }

    @Override
    public void close() {
        queue.close();
    }

    private static final class ChronicleWriter implements Writer {
        final ExcerptAppender appender;
        final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);
        DocumentContext context;
        Bytes<?> bytes;
        int length;

        ChronicleWriter(final ExcerptAppender appender) {
            this.appender = requireNonNull(appender);
        }

        @Override
        public MutableDirectBuffer claim(final int length) {
            final DocumentContext ctx = appender.writingDocument();
            final Bytes<?> b = ctx.wire().bytes();
            b.ensureCapacity(b.writePosition() + length);
            buffer.wrap(b.addressForWrite(b.writePosition()), length);
            this.context = ctx;
            this.bytes = b;
            this.length = length;
            return buffer;
        }

        @Override
        public void commit() {
            buffer.wrap(0, 0);
            bytes.writeSkip(length);
            bytes = null;
            final DocumentContext ctx = context;
            context = null;
            ctx.close();
        }

        @Override
        public void close() {
            appender.close();
        }
    }

    private static final class ChronicleReader implements Reader {
        final ExcerptTailer tailer;
        final UnsafeBuffer buffer = new UnsafeBuffer(0, 0);

        ChronicleReader(final ExcerptTailer tailer) {
            this.tailer = requireNonNull(tailer);
        }

        @Override
        public boolean poll(final MessageHandler handler) {
            try (final DocumentContext context = tailer.readingDocument()) {
                if (!context.isData()) {
                    return false;
                }
                final Bytes<?> bytes = context.wire().bytes();
                final long position = bytes.readPosition();
                final int length = (int) bytes.readRemaining();
                buffer.wrap(bytes.addressForRead(position), length);
                handler.onMessage(buffer, 0, length);
                buffer.wrap(0, 0);
                bytes.readPosition(position + length);
                return true;
            }
        }

        @Override
        public void close() {
            tailer.close();
        }
    }
}
