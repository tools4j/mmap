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
package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.LongReader;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.LongQueue.DEFAULT_NULL_VALUE;
import static org.tools4j.mmap.queue.impl.DefaultLongQueue.unmaskNullValue;
import static org.tools4j.mmap.queue.impl.RegionAccessors.VALUE_WORD;

public class DefaultLongReader implements LongReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongReader.class);
    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(0, 0);
    private final String queueName;
    private final long nullValue;
    private final RegionAccessor regionAccessor;
    private final UnsafeBuffer buffer;
    private final DefaultLongReadingContext entry;

    private final LongReadingContext nullEntryContext = new NullEntryContext();
    private long lastIndex = NULL_INDEX;

    public DefaultLongReader(final String queueName, final long nullValue, final RegionAccessor regionAccessor) {
        this.queueName = requireNonNull(queueName);
        this.nullValue = nullValue;
        this.regionAccessor = requireNonNull(regionAccessor);

        this.buffer = new UnsafeBuffer();
        this.entry = new DefaultLongReadingContext();
    }

    @Override
    public long lastIndex() {
        long index = lastIndex;
        while (readValue(++index) != DEFAULT_NULL_VALUE) {
            lastIndex = index;
        }
        return lastIndex;
    }

    @Override
    public boolean hasEntry(final long index) {
        return readValue(index) != DEFAULT_NULL_VALUE;
    }

    @Override
    public LongReadingContext read(long index) {
        return entry.init(index);
    }

    @Override
    public LongReadingContext readLast() {
        long last = lastIndex();
        if (last != NULL_INDEX) {
            return entry.init(last);
        }
        return nullEntryContext;
    }

    @Override
    public LongReadingContext readFirst() {
        return entry.init(0);
    }

    /**
     * Reads value
     *
     * @param index index value
     * @return value
     */
    private long readValue(final long index) {
        long position = VALUE_WORD.position(index);
        if (!regionAccessor.wrap(position, buffer)) {
            return DEFAULT_NULL_VALUE;
        }
        return buffer.getLongVolatile(0);
    }

    @Override
    public void close() {
        regionAccessor.close();
        LOGGER.info("Closed poller. queue={}", queueName);
    }

    private final class DefaultLongReadingContext implements LongReadingContext {
        long index = NULL_INDEX;
        long value = nullValue;

        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[Long.BYTES]);
        DirectBuffer bufferForReading = EMPTY_BUFFER;

        DefaultLongReadingContext init(final long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index cannot be negative: " + index);
            }

            final long value = readValue(index);
            if (value != DEFAULT_NULL_VALUE) {
                this.index = index;
                this.value = value;
            } else {
                close();
            }
            return this;
        }

        @Override
        public long index() {
            return index;
        }

        @Override
        public DirectBuffer buffer() {
            if (bufferForReading == EMPTY_BUFFER && index != NULL_INDEX) {
                buffer.putLong(0, value());
                bufferForReading = buffer;
            }
            return bufferForReading;
        }

        @Override
        public long value() {
            return unmaskNullValue(value, nullValue);
        }

        @Override
        public boolean hasEntry() {
            return index != NULL_INDEX;
        }

        @Override
        public void close() {
            if (index != NULL_INDEX) {
                index = NULL_INDEX;
                value = DEFAULT_NULL_VALUE;
                bufferForReading = EMPTY_BUFFER;
            }
        }
    }

    private final class NullEntryContext implements LongReadingContext {
        @Override
        public long index() {
            return NULL_INDEX;
        }
        @Override
        public DirectBuffer buffer() {
            return EMPTY_BUFFER;
        }

        @Override
        public long value() {
            return nullValue;
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
