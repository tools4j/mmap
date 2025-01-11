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
package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.region.api.OffsetMapping;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Exceptions.invalidIndexException;
import static org.tools4j.mmap.queue.impl.Exceptions.payloadMoveException;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class EntryIteratorImpl implements EntryIterator {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntryIteratorImpl.class);

    private final String queueName;
    private final ReaderMappings mappings;
    private final OffsetMapping header;
    private final IterableContextImpl context;

    EntryIteratorImpl(final String queueName, final ReaderMappings mappings) {
        this.queueName = requireNonNull(queueName);
        this.mappings = requireNonNull(mappings);
        this.header = requireNonNull(mappings.header());
        this.context = new IterableContextImpl(this);
    }

    @Override
    public IterableContext readingFrom(final long index) {
        checkNotClosed();
        return context.init(index);
    }

    @Override
    public IterableContext readingFromFirst() {
        return readingFrom(Index.FIRST);
    }

    @Override
    public IterableContext readingFromLast() {
        return readingFrom(Index.LAST);
    }

    @Override
    public IterableContext readingFromEnd() {
        return null;
    }

    @Override
    public boolean isClosed() {
        return header.isClosed();
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Entry iterator is closed");
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {
            mappings.close();
            LOGGER.info("Entry iterator closed, queue={}", queueName);
        }
    }

    private static final class IterableContextImpl implements IterableContext, Iterator<Entry>, Entry {
        static final int FORWARD = 1;
        static final int CLOSED = 0;
        final EntryIteratorImpl iterator;
        final OffsetMapping header;
        final ReaderMappings mappings;
        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
        long startIndex;
        int increment;
        long index;
        long nextIndex;
        long nextHeader;
        long maxIndex;

        IterableContextImpl(final EntryIteratorImpl iterator) {
            this.iterator = requireNonNull(iterator);
            this.header = requireNonNull(iterator.header);
            this.mappings = requireNonNull(iterator.mappings);
            this.startIndex = Index.NULL;
            this.increment = CLOSED;
            this.index = Index.NULL;
            this.nextIndex = Index.NULL;
            this.nextHeader = NULL_HEADER;
            this.maxIndex = Index.NULL;
        }

        IterableContext init(final long index) {
            if (!isClosed()) {
                close();
                throw new IllegalStateException("Iterable context has not been closed");
            }
            this.startIndex = actualStartIndex(index);
            this.increment = FORWARD;
            this.index = Index.NULL;
            this.nextIndex = Index.NULL;
            this.nextHeader = NULL_HEADER;
            return this;
        }

        private long actualStartIndex(final long index) {
            if (index >= 0 && index <= Index.MAX) {
                return index;
            }
            if (index < Index.LAST) {
                throw invalidIndexException(iterator.iteratorName(), index);
            }
            final long lastIndex = Headers.binarySearchLastIndex(header, Math.max(maxIndex, Index.FIRST));
            return lastIndex != Index.NULL ? (index == Index.LAST ? lastIndex : lastIndex + 1) : Index.FIRST;
        }

        @Override
        public Iterator<Entry> iterator() {
            if (isClosed()) {
                throw new IllegalStateException("Iterable context is closed");
            }
            nextIndex = startIndex;
            nextHeader = NULL_HEADER;
            index = Index.NULL;
            return this;
        }

        private void updateMaxIndex(final long index) {
            if (index > maxIndex) {
                maxIndex = index;
            }
        }

        @Override
        public IterableContext reverse() {
            increment = -increment;
            return this;
        }

        @Override
        public boolean hasNext() {
            final long next = nextIndex;
            if (next == Index.NULL) {
                return false;
            }
            long hdr = nextHeader;
            if (hdr != NULL_HEADER) {
                return true;
            }
            hdr = Headers.moveAndGetHeader(header, next);
            if (hdr != NULL_HEADER) {
                updateMaxIndex(next);
                nextHeader = hdr;
                return true;
            }
            nextIndex = Index.NULL;
            nextHeader = NULL_HEADER;
            return false;
        }

        @Override
        public Entry next() {
            if (hasNext()) {
                final int inc = increment;
                final long cur = moveToNextAndInitPayload(nextIndex, nextHeader);
                final long next = cur + inc;
                index = cur;
                nextIndex = inc != 0 && next >= Index.FIRST && next <= Index.MAX ? next : Index.NULL;
                nextHeader = NULL_HEADER;
                return this;
            }
            throw new NoSuchElementException();
        }

        private long moveToNextAndInitPayload(final long index, final long hdr) {
            if (hdr == NULL_HEADER) {
                buffer.wrap(0, 0);
                return Index.NULL;
            }
            final int appenderId = Headers.appenderId(hdr);
            final long position = Headers.payloadPosition(hdr);
            final OffsetMapping payload = mappings.payload(appenderId);
            if (!payload.moveTo(position)) {
                throw payloadMoveException(iterator, appenderId, position);
            }
            final int size = payload.buffer().getInt(0);
            buffer.wrap(payload.buffer(), Integer.BYTES, size);
            return index;
        }

        @Override
        public DirectBuffer buffer() {
            return buffer;
        }

        @Override
        public long startIndex() {
            return startIndex;
        }

        @Override
        public long index() {
            return index;
        }

        @Override
        public boolean isClosed() {
            return increment == CLOSED;
        }

        @Override
        public void close() {
            if (!isClosed()) {
                buffer.wrap(0, 0);
                index = Index.NULL;
                startIndex = Index.NULL;
                nextIndex = Index.NULL;
                increment = CLOSED;
            }
        }

        @Override
        public String toString() {
            return "IterableContextImpl" +
                    ":queue=" + iterator.queueName +
                    "|startIndex=" + startIndex +
                    "|index=" + index +
                    "|reverse=" + (increment < 0) +
                    "|closed=" + isClosed();
        }
    }

    String iteratorName() {
        return queueName + ".entryIterator-" + System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "EntryIteratorImpl:queue=" + queueName + "|closed=" + isClosed();
    }

}
