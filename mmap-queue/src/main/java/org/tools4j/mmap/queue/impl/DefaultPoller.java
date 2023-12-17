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

import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Direction;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.Poller.Result.ERROR;
import static org.tools4j.mmap.queue.api.Poller.Result.IDLE;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_MOVED_BACKWARD;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_MOVED_FORWARD;
import static org.tools4j.mmap.queue.api.Poller.Result.POLLED_AND_NOT_MOVED;
import static org.tools4j.mmap.queue.impl.HeaderCodec.HEADER_WORD;

final class DefaultPoller implements Poller {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPoller.class);

    private static final long NULL_HEADER = 0;
    private final String queueName;
    private final QueueRegionMappers regionMappers;
    private final RegionMapper headerMapper;
    private final UnsafeBuffer message = new UnsafeBuffer(0, 0);

    private long currentIndex = 0;
    private short currentAppenderId;
    private long currentPayloadPosition;

    DefaultPoller(final String queueName, final QueueRegionMappers regionMappers) {
        this.queueName = requireNonNull(queueName);
        this.regionMappers = requireNonNull(regionMappers);
        this.headerMapper = requireNonNull(regionMappers.header());
    }

    @Override
    public Result poll(final EntryHandler entryHandler) {
        final long workingIndex = currentIndex;
        if (initHeader(workingIndex)) {
            final Region payload = regionMappers.payload(currentAppenderId).map(currentPayloadPosition);
            if (!payload.isReady()) {
                return ERROR;
            }
            final int length = payload.buffer().getInt(0);
            message.wrap(payload.buffer(), 4, length);
            try {
                final Direction nextMove = entryHandler.onEntry(currentIndex, message);
                switch (nextMove != null ? nextMove : Direction.NONE) {
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
                }
            } finally {
                message.wrap(0, 0);
            }
        }
        return IDLE;
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
    public boolean moveToStart() {
        return moveToIndex(0);
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
        final Region header;
        final long headerPosition = HEADER_WORD.position(index);
        if (!(header = headerMapper.map(headerPosition)).isReady()) {
            return NULL_HEADER;
        }
        return header.buffer().getLongVolatile(0);
    }

    @Override
    public void close() {
        headerMapper.close();//TODO close or is this shared?
        LOGGER.info("Closed poller. queue={}", queueName);
    }
}
