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
import org.tools4j.mmap.queue.api.LongReader;
import org.tools4j.mmap.queue.impl.DefaultIterableContext.MutableReadingContext;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.impl.EmptyBuffer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.LongQueue.DEFAULT_NULL_VALUE;
import static org.tools4j.mmap.queue.impl.DefaultLongQueue.unmaskNullValue;
import static org.tools4j.mmap.queue.impl.LongQueueRegionMappers.VALUE_WORD;

public class DefaultLongReader implements LongReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongReader.class);
    private final String queueName;
    private final long nullValue;
    private final RegionMapper regionMapper;
    private final DefaultLongReadingContext readingContext = new DefaultLongReadingContext();
    private final DefaultLongIterableContext iterableContext = new DefaultLongIterableContext();

    private final LongReadingContext nullEntryContext = new NullEntryContext();
    private long lastIndex = NULL_INDEX;

    public DefaultLongReader(final String queueName, final long nullValue, final RegionMapper regionMapper) {
        this.queueName = requireNonNull(queueName);
        this.nullValue = nullValue;
        this.regionMapper = requireNonNull(regionMapper);
    }

    @Override
    public long firstIndex() {
        long index = lastIndex;
        if (index != NULL_INDEX) {
            return 0;
        }
        if (readValue(++index) != DEFAULT_NULL_VALUE) {
            lastIndex = index;
            return 0;
        }
        return NULL_INDEX;
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
        return index >= 0 && readValue(index) != DEFAULT_NULL_VALUE;
    }

    @Override
    public LongReadingContext reading(final long index) {
        return index >= 0 ? readingContext.init(index) : nullEntryContext;
    }

    @Override
    public LongReadingContext readingFirst() {
        return readingContext.init(0);
    }

    @Override
    public LongReadingContext readingLast() {
        long last = lastIndex();
        if (last != NULL_INDEX) {
            return readingContext.init(last);
        }
        return nullEntryContext;
    }

    @Override
    public LongIterableContext readingFrom(final long index) {
        return iterableContext.init(index);
    }

    @Override
    public LongIterableContext readingFromFirst() {
        return iterableContext.init(0);
    }

    @Override
    public LongIterableContext readingFromLast() {
        return iterableContext.init(Long.MAX_VALUE);
    }

    /**
     * Reads value
     *
     * @param index index value
     * @return value
     */
    private long readValue(final long index) {
        final Region region;
        final long position = VALUE_WORD.position(index);
        if (!(region = regionMapper.map(position)).isReady()) {
            return DEFAULT_NULL_VALUE;
        }
        return region.buffer().getLongVolatile(0);
    }

    @Override
    public void close() {
        regionMapper.close();//TODO close or shared ?
        LOGGER.info("Closed poller. queue={}", queueName);
    }

    private final class DefaultLongReadingContext implements LongReadingContext, MutableReadingContext<LongEntry> {
        long index = NULL_INDEX;
        long value = nullValue;

        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[Long.BYTES]);
        DirectBuffer bufferForReading = EmptyBuffer.INSTANCE;

        DefaultLongReadingContext init(final long index) {
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
            final long value = readValue(index);
            if (value != DEFAULT_NULL_VALUE) {
                this.index = index;
                this.value = value;
                this.bufferForReading = EmptyBuffer.INSTANCE;
                return true;
            }
            return false;
        }

        @Override
        public LongEntry entry() {
            return this;
        }

        @Override
        public long index() {
            return index;
        }

        @Override
        public DirectBuffer buffer() {
            if (bufferForReading == EmptyBuffer.INSTANCE && index != NULL_INDEX) {
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
                bufferForReading = EmptyBuffer.INSTANCE;
            }
        }
    }

    private final class DefaultLongIterableContext extends DefaultIterableContext<LongEntry> implements LongIterableContext {

        DefaultLongIterableContext() {
            super(DefaultLongReader.this, readingContext);
        }

        @Override
        DefaultLongIterableContext init(final long index) {
            super.init(index);
            return this;
        }
    }

    private final class NullEntryContext extends EmptyReadingContext<LongEntry> implements LongReadingContext {
        @Override
        public long value() {
            return nullValue;
        }
    }
}
