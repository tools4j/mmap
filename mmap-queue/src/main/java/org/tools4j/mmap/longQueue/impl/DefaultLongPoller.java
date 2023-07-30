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
package org.tools4j.mmap.longQueue.impl;

import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.longQueue.api.EntryHandler;
import org.tools4j.mmap.longQueue.api.LongPoller;
import org.tools4j.mmap.longQueue.api.LongQueue;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.longQueue.api.LongPoller.Result.ADVANCED;
import static org.tools4j.mmap.longQueue.api.LongPoller.Result.NOT_AVAILABLE;
import static org.tools4j.mmap.longQueue.api.LongPoller.Result.RETAINED;
import static org.tools4j.mmap.longQueue.api.LongPoller.Result.RETREATED;
import static org.tools4j.mmap.longQueue.impl.RegionAccessors.VALUE_WORD;

public class DefaultLongPoller implements LongPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongPoller.class);

    private final String queueName;
    private final RegionAccessor regionAccessor;
    private final UnsafeBuffer buffer;
    private long currentIndex = 0;

    public DefaultLongPoller(final String queueName, final RegionAccessor regionAccessor) {
        this.queueName = requireNonNull(queueName);
        this.regionAccessor = requireNonNull(regionAccessor);

        this.buffer = new UnsafeBuffer();
    }

    @Override
    public Result poll(final EntryHandler entryHandler) {
        final long value = readValue(currentIndex);
        if (value != LongQueue.NULL_VALUE) {

            final EntryHandler.NextMove nextMove = entryHandler.onEntry(currentIndex, value);
            if (nextMove == null) {
                return RETAINED;
            }

            switch (nextMove) {
                case ADVANCE:
                    currentIndex++;
                    return ADVANCED;
                case RETREAT:
                    if (currentIndex > 0) {
                        currentIndex--;
                        return RETREATED;
                    }
                    return RETAINED;
                case RETAIN:
                    return RETAINED;
            }
        }
        return NOT_AVAILABLE;
    }

    @Override
    public boolean moveToIndex(final long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + index);
        }

        if (init(index)) {
            return true;
        } else {
            //index could be the last one but having no entry yet,
            //so check if previous index has entry
            if (index > 0 && hasEntry(index - 1)) {
                currentIndex = index;
                return true;
            }
        }
        return false;
    }

    @Override
    public long moveToEnd() {
        while (readValue(currentIndex) != LongQueue.NULL_VALUE) {
            currentIndex++;
        }
        return currentIndex;
    }

    @Override
    public boolean moveToStart() {
        return moveToIndex(0);
    }

    @Override
    public long currentIndex() {
        return currentIndex;
    }

    @Override
    public boolean hasEntry(final long index) {
        return readValue(index) != LongQueue.NULL_VALUE;
    }

    private boolean init(final long index) {
        final long value = readValue(index);
        if (value != LongQueue.NULL_VALUE) {
            currentIndex = index;
            return true;
        }
        return false;
    }

    /**
     * Reads header
     * @param index index value
     * @return header value
     */
    private long readValue(final long index) {
        long valuePosition = VALUE_WORD.position(index);
        if (!regionAccessor.wrap(valuePosition, buffer)) {
            return LongQueue.NULL_VALUE;
        }
        return buffer.getLongVolatile(0);
    }

    @Override
    public void close() {
        regionAccessor.close();
        LOGGER.info("Closed poller. queue={}", queueName);
    }
}
