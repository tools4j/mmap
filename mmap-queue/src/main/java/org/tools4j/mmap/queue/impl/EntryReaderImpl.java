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
package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Exceptions.invalidIndexException;
import static org.tools4j.mmap.queue.impl.Exceptions.payloadMoveException;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class EntryReaderImpl implements EntryReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerImpl.class);

    private final String queueName;
    private final ReaderMappings mappings;
    private final OffsetMapping header;
    private final ReadingContextImpl context;

    EntryReaderImpl(final String queueName, final ReaderMappings mappings) {
        this.queueName = requireNonNull(queueName);
        this.mappings = requireNonNull(mappings);
        this.header = requireNonNull(mappings.header());
        this.context = new ReadingContextImpl(this);
    }

    @Override
    public long lastIndex() {
        checkNotClosed();
        return Headers.binarySearchLastIndex(header, Index.FIRST);
    }

    @Override
    public boolean hasEntry(final long index) {
        checkNotClosed();
        return Headers.hasNonEmptyHeaderAt(header, index);
    }

    @Override
    public ReadingContext reading(final long index) {
        checkNotClosed();
        return context.init(index);
    }

    @Override
    public ReadingContext readingFirst() {
        return reading(Index.FIRST);
    }

    @Override
    public ReadingContext readingLast() {
        return reading(Index.LAST);
    }

    @Override
    public boolean isClosed() {
        return header.isClosed();
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Entry reader is closed");
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {
            header.close();
            LOGGER.info("Entry reader closed, queue={}", queueName);
        }
    }

    private static final class ReadingContextImpl implements ReadingContext {
        final EntryReaderImpl reader;
        final OffsetMapping header;
        final ReaderMappings mappings;
        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
        long maxIndex;
        long index;
        boolean closed;

        ReadingContextImpl(final EntryReaderImpl reader) {
            this.reader = requireNonNull(reader);
            this.header = requireNonNull(reader.header);
            this.mappings = requireNonNull(reader.mappings);
            this.maxIndex = Index.NULL;
            this.index = Index.NULL;
            this.closed = true;
        }

        ReadingContext init(final long index) {
            if (!isClosed()) {
                close();
                throw new IllegalStateException("Reading context has not been closed");
            }
            if (index < 0 || index > Index.MAX) {
                if (index != Index.LAST) {
                    throw invalidIndexException(reader.readerName(), index);
                }
            }
            final long actualIndex = initPayloadBuffer(index);
            this.index = actualIndex;
            this.closed = false;
            if (actualIndex > maxIndex) {
                maxIndex = actualIndex;
            }
            return this;
        }

        private long initPayloadBuffer(final long index) {
            final long hdr = index != Index.LAST ?
                    Headers.moveAndGetHeader(header, index):
                    Headers.binarySearchLastIndex(header, Math.max(maxIndex, Index.FIRST));
            if (hdr == NULL_HEADER) {
                return Index.NULL;
            }
            final int appenderId = Headers.appenderId(hdr);
            final long position = Headers.payloadPosition(hdr);
            final OffsetMapping payload = mappings.payload(appenderId);
            if (!payload.moveTo(position)) {
                throw payloadMoveException(reader, appenderId, position);
            }
            final int size = payload.buffer().getInt(0);
            this.buffer.wrap(payload.buffer(), Integer.BYTES, size);
            return index;
        }

        @Override
        public DirectBuffer buffer() {
            return buffer;
        }

        @Override
        public long index() {
            return index;
        }

        @Override
        public boolean hasEntry() {
            return index != Index.NULL;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (!closed) {
                buffer.wrap(0, 0);
                index = Index.NULL;
                closed = true;
            }
        }

        @Override
        public String toString() {
            return "ReadingContextImpl" +
                    ":queue=" + reader.queueName +
                    "|index=" + index +
                    "|closed=" + closed;
        }
    }

    String readerName() {
        return queueName + ".entryReader-" + System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "EntryReaderImpl:queue=" + queueName + "|closed=" + isClosed();
    }
}
