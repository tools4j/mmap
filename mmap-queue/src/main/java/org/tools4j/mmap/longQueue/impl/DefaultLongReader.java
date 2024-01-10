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
package org.tools4j.mmap.longQueue.impl;

import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.longQueue.api.LongReader;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.longQueue.api.LongQueue.NULL_VALUE;
import static org.tools4j.mmap.longQueue.impl.RegionAccessors.VALUE_WORD;

public class DefaultLongReader implements LongReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongReader.class);
    private static final Entry EMPTY_ENTRY = new EmptyEntry();
    private final String queueName;
    private final RegionAccessor regionAccessor;
    private final UnsafeBuffer buffer;
    private final DefaultEntry entry;

    private long lastIndex = NULL_INDEX;

    public DefaultLongReader(final String queueName, final RegionAccessor regionAccessor) {
        this.queueName = requireNonNull(queueName);
        this.regionAccessor = requireNonNull(regionAccessor);

        this.buffer = new UnsafeBuffer();
        this.entry = new DefaultEntry();
    }

    @Override
    public long lastIndex() {
        long index = lastIndex;
        while (readValue(++index) != NULL_VALUE) {
            lastIndex = index;
        }
        return lastIndex;
    }

    @Override
    public boolean hasValue(final long index) {
        return readValue(index) != NULL_VALUE;
    }

    @Override
    public Entry read(long index) {
        return entry.init(index);
    }

    @Override
    public Entry readLast() {
        long last = lastIndex();
        if (last != NULL_INDEX) {
            return entry.init(last);
        }
        return EMPTY_ENTRY;
    }

    @Override
    public Entry readFirst() {
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
            return NULL_VALUE;
        }
        return buffer.getLongVolatile(0);
    }

    @Override
    public void close() {
        regionAccessor.close();
        LOGGER.info("Closed poller. queue={}", queueName);
    }

    private class DefaultEntry implements Entry {
        long index = NULL_INDEX;
        long value = NULL_VALUE;

        DefaultEntry init(long index) {
            if (index < 0) {
                throw new IllegalArgumentException("Index cannot be negative: " + index);
            }

            final long value = readValue(index);
            if (value != NULL_VALUE) {
                this.index = index;
                this.value = value;
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
        public long value() {
            return value;
        }

        @Override
        public boolean hasValue() {
            return index != NULL_INDEX;
        }

        private void reset() {
            index = NULL_INDEX;
            value = NULL_VALUE;
        }
    }

    private static class EmptyEntry implements Entry {
        @Override
        public long index() {
            return NULL_INDEX;
        }

        @Override
        public long value() {
            return NULL_VALUE;
        }

        @Override
        public boolean hasValue() {
            return false;
        }
    }
}
