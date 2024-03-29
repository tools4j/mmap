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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.LongAppender;
import org.tools4j.mmap.region.api.RegionCursor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.LongQueue.DEFAULT_NULL_VALUE;
import static org.tools4j.mmap.queue.impl.DefaultLongQueue.maskNullValue;
import static org.tools4j.mmap.queue.impl.LongQueueRegionCursors.VALUE_WORD;

public class DefaultLongAppender implements LongAppender {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLongAppender.class);
    private static final long NOT_INITIALISED = -1;
    private final long nullValue;
    private final RegionCursor regionCursor;
    private long currentPosition = NOT_INITIALISED;
    private long currentIndex = NOT_INITIALISED;

    public DefaultLongAppender(final long nullValue, final RegionCursor regionCursor) {
        this.nullValue = nullValue;
        this.regionCursor = requireNonNull(regionCursor);
        advanceToLastAppendPosition();
    }

    @Override
    public long append(final long value) {
        if (value == nullValue) {
            throw new IllegalArgumentException("Value cannot be equal to null value: " + nullValue);
        }
        final long maskedValue = maskNullValue(value, nullValue);

        boolean onLastAppendPosition = advanceToLastAppendPosition();
        if (!onLastAppendPosition) {
            return MOVE_TO_END_ERROR;
        }

        final RegionCursor cursor = regionCursor;
        if (cursor.moveTo(currentPosition)) {
            while (!cursor.buffer().compareAndSetLong(0, DEFAULT_NULL_VALUE, maskedValue)) {
                if (!moveToLastPosition()) {
                    return WRAP_REGION_ERROR;
                }
            }

            final long appendIndex = currentIndex;
            currentIndex++;
            currentPosition = VALUE_WORD.position(currentIndex);

            return appendIndex;
        } else {
            LOGGER.error("Failed to wrap buffer for position {}", currentPosition);
            return WRAP_REGION_ERROR;
        }
    }

    private boolean moveToLastPosition() {
        currentIndex++;
        currentPosition = VALUE_WORD.position(currentIndex);
        long value;
        final RegionCursor cursor = regionCursor;
        do {
            if (cursor.moveTo(currentPosition)) {
                value = cursor.buffer().getLongVolatile(0);
                if (value != DEFAULT_NULL_VALUE) {
                    currentPosition = VALUE_WORD.position(++currentIndex);
                }
            } else {
                LOGGER.error("Failed to map region at {} when advancing to last append position",
                        currentPosition);
                return false;
            }
        } while (value != DEFAULT_NULL_VALUE);
        return true;
    }

    private boolean advanceToLastAppendPosition() {
        if (currentPosition == NOT_INITIALISED) {
            currentIndex = 0;
            currentPosition = VALUE_WORD.position(currentIndex);
            long value;
            do {
                final RegionCursor cursor = regionCursor;
                if (cursor.moveTo(currentPosition)) {
                    value = cursor.buffer().getLongVolatile(0);
                    if (value != DEFAULT_NULL_VALUE) {
                        currentPosition = VALUE_WORD.position(++currentIndex);
                    }
                } else {
                    LOGGER.error("Failed to wrap buffer for position {} when advancing to last append position",
                            currentPosition);
                    return false;
                }
            } while (value != DEFAULT_NULL_VALUE);
        }
        return true;
    }

    @Override
    public void close() {
        regionCursor.close();//TODO close or is this shared ?
        LOGGER.info("Closed long appender.");
    }
}
