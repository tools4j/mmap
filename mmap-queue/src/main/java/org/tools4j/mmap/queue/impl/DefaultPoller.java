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

import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.MessageHandler;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.agrona.collections.ArrayUtil.EMPTY_BYTE_ARRAY;
import static org.tools4j.mmap.queue.api.Poller.Result.ADVANCED;
import static org.tools4j.mmap.queue.api.Poller.Result.ERROR;
import static org.tools4j.mmap.queue.api.Poller.Result.NOT_AVAILABLE;
import static org.tools4j.mmap.queue.api.Poller.Result.RETAINED;
import static org.tools4j.mmap.queue.api.Poller.Result.RETREATED;
import static org.tools4j.mmap.queue.impl.HeaderCodec.HEADER_WORD;

public class DefaultPoller implements Poller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPoller.class);

    private static final long NULL_HEADER = 0;
    private final String queueName;
    private final RegionAccessorSupplier regionAccessor;
    private final RegionAccessor headerAccessor;
    private final UnsafeBuffer headerBuffer;
    private final UnsafeBuffer payloadBuffer;
    private final UnsafeBuffer message;

    private long currentIndex = 0;
    private short currentAppenderId;
    private long currentPayloadPosition;

    public DefaultPoller(final String queueName, final RegionAccessorSupplier regionAccessor) {
        this.queueName = requireNonNull(queueName);
        this.regionAccessor = requireNonNull(regionAccessor);
        this.headerAccessor = regionAccessor.header();

        this.headerBuffer = new UnsafeBuffer();
        this.payloadBuffer = new UnsafeBuffer();
        this.message = new UnsafeBuffer();
    }

    @Override
    public Result poll(final MessageHandler messageHandler) {
        final long workingIndex = currentIndex;
        if (initHeader(workingIndex)) {
            if (!regionAccessor.payload(currentAppenderId).wrap(currentPayloadPosition, payloadBuffer)) {
                LOGGER.error("Failed to wrap payload buffer to position {} of appender {}", currentPayloadPosition, currentAppenderId);
                return ERROR;
            }
            final int length = payloadBuffer.getInt(0);
            message.wrap(payloadBuffer, 4, length);

            final MessageHandler.NextMove nextMove = messageHandler.onMessage(currentIndex, message);
            message.wrap(EMPTY_BYTE_ARRAY);

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

        if (initHeader(index)) {
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
        while (readHeader(currentIndex) != NULL_HEADER) {
            currentIndex++;
        }
        return currentIndex;
    }

    @Override
    public long moveToLastExisting() {
        long index = currentIndex;
        while (initHeader(index)) {
            index++;
        }
        return currentIndex == index ? NULL_INDEX : currentIndex;
    }

    @Override
    public void moveToStart() {
        currentIndex = 0;
    }

    @Override
    public long currentIndex() {
        return currentIndex;
    }

    @Override
    public boolean hasEntry(final long index) {
        return readHeader(index) != NULL_HEADER;
    }

    private boolean initHeader(final long index) {
        final long header = readHeader(index);
        if (header != NULL_HEADER) {
            currentIndex = index;
            currentAppenderId = HeaderCodec.appenderId(header);
            currentPayloadPosition = HeaderCodec.payloadPosition(header);
            return true;
        }
        return false;
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
        regionAccessor.close();
        LOGGER.info("Closed poller. queue={}", queueName);
    }
}
