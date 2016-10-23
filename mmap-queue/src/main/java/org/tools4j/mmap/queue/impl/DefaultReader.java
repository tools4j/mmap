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
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.HeaderCodec.HEADER_WORD;

public class DefaultReader implements Reader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultReader.class);
    private static final ReadingContext EMPTY_READING_CONTEXT = new EmptyReadingContext();
    private static final long NULL_HEADER = 0;
    private final String queueName;
    private final RegionAccessorSupplier regionAccessor;
    private final RegionAccessor headerAccessor;
    private final UnsafeBuffer headerBuffer;
    private final UnsafeBuffer payloadBuffer;
    private final DefaultReadingContext readingContext;

    private long lastIndex = NULL_INDEX;

    public DefaultReader(final String queueName, final RegionAccessorSupplier regionAccessor) {
        this.queueName = requireNonNull(queueName);
        this.regionAccessor = requireNonNull(regionAccessor);
        this.headerAccessor = regionAccessor.header();

        this.headerBuffer = new UnsafeBuffer();
        this.payloadBuffer = new UnsafeBuffer();
        this.readingContext = new DefaultReadingContext();
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
        return readHeader(index) != NULL_HEADER;
    }

    @Override
    public ReadingContext read(long index) {
        return readingContext.init(index);
    }

    @Override
    public ReadingContext readLast() {
        long last = lastIndex();
        if (last != NULL_INDEX) {
            return readingContext.init(last);
        }
        return EMPTY_READING_CONTEXT;
    }

    @Override
    public ReadingContext readFirst() {
        return readingContext.init(0);
    }

    /**
     * Reads header
     * @param index index value
     * @return header value
     */
    private long readHeader(final long index) {
        long headerPosition = HEADER_WORD.position(index);
        if (!headerAccessor.wrap(headerPosition, headerBuffer)) {
            return NULL_HEADER;
        }
        return headerBuffer.getLongVolatile(0);
    }

    @Override
    public void close() {
        readingContext.close();
        regionAccessor.close();
        LOGGER.info("Closed poller. queue={}", queueName);
    }

    private class DefaultReadingContext implements ReadingContext {
        long index = NULL_INDEX;
        DirectBuffer buffer = new UnsafeBuffer();

        DefaultReadingContext init(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index cannot be negative: " + index);
            }

            final long header = readHeader(index);
            if (header != NULL_HEADER) {
                short appenderId = HeaderCodec.appenderId(header);
                long payloadPosition = HeaderCodec.payloadPosition(header);

                if (!regionAccessor.payload(appenderId).wrap(payloadPosition, payloadBuffer)) {
                    LOGGER.error("Failed to wrap payload buffer to position {} of appender {}", payloadPosition, appenderId);
                    reset();
                    return this;
                }
                final int length = payloadBuffer.getInt(0);
                buffer.wrap(payloadBuffer, 4, length);
                this.index = index;
            } else {
                reset();
            }
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

        private void reset() {
            index = NULL_INDEX;
            buffer.wrap(0, 0);
        }

        @Override
        public void close() {
            reset();
        }
    }

    private static class EmptyReadingContext implements ReadingContext {
        UnsafeBuffer emptyBuffer = new UnsafeBuffer();
        @Override
        public long index() {
            return NULL_INDEX;
        }

        @Override
        public DirectBuffer buffer() {
            return emptyBuffer;
        }

        @Override
        public boolean hasEntry() {
            return false;
        }

        @Override
        public void close() {
            //no op
        }
    }
}
