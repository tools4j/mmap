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

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.perf.PerfOptions.ReaderType;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.util.Iterator;

import static java.util.Objects.requireNonNull;

/**
 * {@link PerfQueue} for an mmap {@link Queue}, writing through an {@link Appender} and reading through a
 * {@link Poller} or a {@linkplain IterableContext#continuous() continuous} {@link EntryIterator}.
 */
public final class MmapPerfQueue implements PerfQueue {

    private final QueueConfig config;
    private final ReaderType readerType;
    private final Queue queue;

    public MmapPerfQueue(final File directory, final QueueConfig config, final ReaderType readerType) {
        this.config = requireNonNull(config);
        this.readerType = requireNonNull(readerType);
        this.queue = Queue.open(directory, OpenMode.OPEN_OR_CREATE, config);
    }

    @Override
    public Writer createWriter() {
        return new MmapWriter(queue.createAppender());
    }

    @Override
    public Reader createReader() {
        return switch (readerType) {
            case POLLER -> new PollerReader(queue.createPoller());
            case ITERATOR -> new IteratorReader(queue.createEntryIterator());
        };
    }

    @Override
    public void describeSettings(final StringBuilder sb) {
        sb.append("queue                 : mmap\n");
        sb.append("reader                : ").append(readerType == ReaderType.POLLER ? "poller" :
                "continuous iterator").append('\n');
        ConfigPrinter.describeConfig(config, sb);
    }

    @Override
    public void close() {
        queue.close();
    }

    private static final class MmapWriter implements Writer {
        final Appender appender;
        AppendingContext context;
        int length;

        MmapWriter(final Appender appender) {
            this.appender = requireNonNull(appender);
        }

        @Override
        public MutableDirectBuffer claim(final int length) {
            this.context = appender.appending(length);
            this.length = length;
            return context.buffer();
        }

        @Override
        public void commit() {
            final AppendingContext ctx = context;
            context = null;
            ctx.commit(length);
        }

        @Override
        public void close() {
            appender.close();
        }
    }

    private static final class PollerReader implements Reader, EntryHandler {
        final Poller poller;
        MessageHandler handler;

        PollerReader(final Poller poller) {
            this.poller = requireNonNull(poller);
        }

        @Override
        public boolean poll(final MessageHandler handler) {
            this.handler = handler;
            final int result = poller.poll(this);
            if (result < Poller.PENDING_OPEN) {
                throw new IllegalStateException("Polling failed with result " + result + " for " + poller);
            }
            return result == Poller.ENTRY_POLLED;
        }

        @Override
        public long onEntry(final long index, final DirectBuffer buffer, final int offset, final int length) {
            handler.onMessage(buffer, offset, length);
            return Move.NEXT;
        }

        @Override
        public void close() {
            poller.close();
        }
    }

    private static final class IteratorReader implements Reader {
        final EntryIterator entryIterator;
        final IterableContext context;
        final Iterator<Entry> entries;

        IteratorReader(final EntryIterator entryIterator) {
            this.entryIterator = requireNonNull(entryIterator);
            this.context = entryIterator.readingFromFirst().continuous();
            this.entries = context.iterator();
        }

        @Override
        public boolean poll(final MessageHandler handler) {
            if (entries.hasNext()) {
                final DirectBuffer buffer = entries.next().buffer();
                handler.onMessage(buffer, 0, buffer.capacity());
                return true;
            }
            return false;
        }

        @Override
        public void close() {
            context.close();
            entryIterator.close();
        }
    }
}
