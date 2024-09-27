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
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.queue.impl.DefaultIterableContext.MutableReadingContext;
import org.tools4j.mmap.region.api.Region;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.HEADER_WORD;

final class DefaultReader implements Reader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReader.class);
    private static final ReadingContext EMPTY_READING_CONTEXT = new EmptyReadingContext();
    private static final long NULL_HEADER = 0;
    private final String queueName;
    private final QueueRegions regionCursors;
    private final Region headerCursor;
    private final DefaultReadingContext readingContext = new DefaultReadingContext();
    private final DefaultIterableContext<Entry> iterableContext = new DefaultIterableContext<>(this, readingContext);

    private long lastIndex = NULL_INDEX;

    DefaultReader(final String queueName, final QueueRegions regionCursors) {
        this.queueName = requireNonNull(queueName);
        this.regionCursors = requireNonNull(regionCursors);
        this.headerCursor = requireNonNull(regionCursors.header());
    }

    @Override
    public long firstIndex() {
        long index = lastIndex;
        if (index != NULL_INDEX) {
            return 0;
        }
        if (readHeader(++index) != NULL_HEADER) {
            lastIndex = index;
            return 0;
        }
        return NULL_INDEX;
    }

    @Override
    public long lastIndex() {
        long index = lastIndex;
        while (readHeader(++index) != NULL_HEADER) {
            lastIndex = index;
        }
        return lastIndex;
    }

    @Override
    public boolean hasEntry(final long index) {
        return index >= 0 && readHeader(index) != NULL_HEADER;
    }

    @Override
    public ReadingContext reading(long index) {
        return readingContext.init(index);
    }

    @Override
    public ReadingContext readingFirst() {
        return readingContext.init(0);
    }

    @Override
    public ReadingContext readingLast() {
        long last = lastIndex();
        if (last != NULL_INDEX) {
            return readingContext.init(last);
        }
        return EMPTY_READING_CONTEXT;
    }

    @Override
    public IterableContext readingFrom(final long index) {
        return iterableContext.init(index);
    }

    @Override
    public IterableContext readingFromFirst() {
        return iterableContext.init(0);
    }

    @Override
    public IterableContext readingFromLast() {
        return iterableContext.init(Long.MAX_VALUE);
    }

    /**
     * Reads header
     * @param index index value
     * @return header value
     */
    private long readHeader(final long index) {
        final Region header = headerCursor;
        final long headerPosition = HEADER_WORD.position(index);
        if (!header.moveTo(headerPosition)) {
            return NULL_HEADER;
        }
        return header.buffer().getLongVolatile(0);
    }

    @Override
    public void close() {
        readingContext.close();
        regionCursors.close();//TODO close or is this shared?
        LOGGER.info("Closed poller. queue={}", queueName);
    }

    private final class DefaultReadingContext implements MutableReadingContext<Entry> {
        long index = NULL_INDEX;
        DirectBuffer buffer = new UnsafeBuffer(0, 0);

        DefaultReadingContext init(final long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index cannot be negative: " + index);
            }
            if (this.index != NULL_INDEX) {
                close();
                throw new IllegalStateException("ReadingContext is not closed");
            }
            if (!tryInit(index)) {
                close();
            }
            return this;
        }

        @Override
        public boolean tryInit(final long index) {
            final long header = readHeader(index);
            if (header != NULL_HEADER) {
                final short appenderId = Headers.appenderId(header);
                final long payloadPosition = Headers.payloadPosition(header);
                final Region payload = regionCursors.payload(appenderId);
                if (payload.moveTo(payloadPosition)) {
                    final int length = payload.buffer().getInt(0);
                    buffer.wrap(payload.buffer(), 4, length);
                    this.index = index;
                    return true;
                }
            }
            return false;
        }

        @Override
        public Entry entry() {
            return this;
        }

        @Override
        public long index() {
            return index;
        }

        @Override
        public DirectBuffer buffer() {
            return buffer;
        }

        @Override
        public boolean hasEntry() {
            return index != NULL_INDEX;
        }

        @Override
        public void close() {
            if (index != NULL_INDEX) {
                index = NULL_INDEX;
                buffer.wrap(0, 0);
            }
        }
    }

}
