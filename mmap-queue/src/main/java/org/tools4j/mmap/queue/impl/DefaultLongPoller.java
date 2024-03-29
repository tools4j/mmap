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

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Direction;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.LongEntryHandler;
import org.tools4j.mmap.queue.api.LongPoller;
import org.tools4j.mmap.region.api.RegionCursor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.LongQueue.DEFAULT_NULL_VALUE;
import static org.tools4j.mmap.queue.api.Poller.Result.IDLE;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_MOVED_BACKWARD;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_MOVED_FORWARD;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_NOT_MOVED;
import static org.tools4j.mmap.queue.impl.DefaultLongQueue.unmaskNullValue;
import static org.tools4j.mmap.queue.impl.LongQueueRegionCursors.VALUE_WORD;

public class DefaultLongPoller implements LongPoller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongPoller.class);

    private final String queueName;
    private final long nullValue;
    private final RegionCursor regionCursor;
    private final MutableDirectBuffer pollBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
    private long currentIndex = 0;

    public DefaultLongPoller(final String queueName, final long nullValue, final RegionCursor regionReader) {
        this.queueName = requireNonNull(queueName);
        this.nullValue = nullValue;
        this.regionCursor = requireNonNull(regionReader);
    }

    @Override
    public Result poll(final EntryHandler entryHandler) {
        final long value = readValue(currentIndex);
        if (value != DEFAULT_NULL_VALUE) {
            pollBuffer.putLong(0, unmaskNullValue(value, nullValue));
            final Direction nextMove = entryHandler.onEntry(currentIndex, pollBuffer);
            return moveAfterPoll(nextMove);
        }
        return IDLE;
    }

    @Override
    public Result poll(final LongEntryHandler entryHandler) {
        final long value = readValue(currentIndex);
        if (value != DEFAULT_NULL_VALUE) {
            final Direction nextMove = entryHandler.onEntry(currentIndex, unmaskNullValue(value, nullValue));
            return moveAfterPoll(nextMove);
        }
        return IDLE;
    }

    private Result moveAfterPoll(final Direction nextMove) {
        if (nextMove == null) {
            return POLLED_AND_NOT_MOVED;
        }
        switch (nextMove) {
            case FORWARD:
                currentIndex++;
                return POLLED_AND_MOVED_FORWARD;
            case BACKWARD:
                if (currentIndex > 0) {
                    currentIndex--;
                    return POLLED_AND_MOVED_BACKWARD;
                }
                return POLLED_AND_NOT_MOVED;
            case NONE:
                return POLLED_AND_NOT_MOVED;
            default:
                throw new IllegalArgumentException("Unsupported next move: " + nextMove);
        }
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
        while (readValue(currentIndex) != DEFAULT_NULL_VALUE) {
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
        return readValue(index) != DEFAULT_NULL_VALUE;
    }

    private boolean init(final long index) {
        final long value = readValue(index);
        if (value != DEFAULT_NULL_VALUE) {
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
        final RegionCursor cursor = regionCursor;
        final long valuePosition = VALUE_WORD.position(index);
        if (!cursor.moveTo(valuePosition)) {
            return DEFAULT_NULL_VALUE;
        }
        return cursor.buffer().getLongVolatile(0);
    }

    @Override
    public void close() {
        regionCursor.close();//TODO close or shared ?
        LOGGER.info("Closed poller. queue={}", queueName);
    }
}
