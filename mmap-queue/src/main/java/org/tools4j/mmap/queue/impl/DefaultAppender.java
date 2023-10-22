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
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.HeaderCodec.HEADER_WORD;
import static org.tools4j.mmap.queue.impl.HeaderCodec.appenderId;
import static org.tools4j.mmap.queue.impl.HeaderCodec.payloadPosition;

public class DefaultAppender implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAppender.class);
    private static final long NOT_INITIALISED = -1;
    private static final int LENGTH_LENGTH = 4;
    private static final int LENGTH_OFFSET = 0;
    private static final int PAYLOAD_OFFSET = LENGTH_OFFSET + LENGTH_LENGTH;

    private final UnsafeBuffer headerBuffer;
    private final UnsafeBuffer payloadBuffer;
    private final short appenderId;
    private final AppenderIdPool appenderIdPool;

    private final String queueName;
    private final RegionAccessorSupplier regionAccessor;
    private final RegionAccessor headerAccessor;
    private final RegionAccessor payloadAccessor;
    private final MaxLengthAppendingContext appendingContext;

    private long currentHeaderPosition = NOT_INITIALISED;
    private long currentIndex = NOT_INITIALISED;
    private long currentPayloadPosition = HeaderCodec.initialPayloadPosition();

    public DefaultAppender(final String queueName,
                           final RegionAccessorSupplier regionAccessor,
                           final AppenderIdPool appenderIdPool) {
        this.queueName = requireNonNull(queueName);
        this.appenderIdPool = requireNonNull(appenderIdPool);
        this.appenderId = appenderIdPool.acquire();

        this.headerBuffer = new UnsafeBuffer();
        this.payloadBuffer = new UnsafeBuffer();
        this.headerAccessor = regionAccessor.header();
        this.payloadAccessor = regionAccessor.payload(appenderId);
        this.regionAccessor = regionAccessor;
        this.appendingContext = new MaxLengthAppendingContext();

        advanceToLastAppendPosition();
    }

    @Override
    public long append(final DirectBuffer buffer, final int offset, final int length) {
        if (!appendingContext.isClosed()) {
            return APPENDING_CONTEXT_IN_USE;
        }

        boolean onLastAppendPosition = advanceToLastAppendPosition();
        if (!onLastAppendPosition) {
            return MOVE_TO_END_ERROR;
        }

        if (payloadAccessor.wrap(currentPayloadPosition, payloadBuffer)) {
            if (headerAccessor.wrap(currentHeaderPosition, headerBuffer)) {

                if (payloadBuffer.capacity() < length + LENGTH_LENGTH) { //message does not fit capacity of payload buffer
                    //should re-map the payload buffer to the new memory region
                    currentPayloadPosition += payloadBuffer.capacity();
                    if (!payloadAccessor.wrap(currentPayloadPosition, payloadBuffer)) {
                        LOGGER.error("Failed to wrap payload buffer for position {} while advancing to new region",
                                currentPayloadPosition);
                        return ADVANCE_TO_NEXT_PAYLOAD_REGION_ERROR;
                    }
                }
                payloadBuffer.putInt(LENGTH_OFFSET, length);
                buffer.getBytes(offset, payloadBuffer, PAYLOAD_OFFSET, length);

                long header = HeaderCodec.header(appenderId, currentPayloadPosition);

                while (!headerBuffer.compareAndSetLong(0, 0, header)) {
                    moveToLastHeader();
                }

                final long appendIndex = currentIndex;
                currentIndex++;
                currentHeaderPosition = HEADER_WORD.position(currentIndex);
                currentPayloadPosition += length + LENGTH_LENGTH;

                return appendIndex;
            } else {
                LOGGER.error("Failed to wrap header buffer for position {}", currentHeaderPosition);
                return WRAP_HEADER_REGION_ERROR;
            }
        } else {
            LOGGER.error("Failed to wrap payload buffer for position {}", currentPayloadPosition);
            return WRAP_PAYLOAD_REGION_ERROR;
        }
    }

    @Override
    public AppendingContext appending(int maxLength) {
        boolean onLastAppendPosition = advanceToLastAppendPosition();
        if (!onLastAppendPosition) {
            throw new RuntimeException("Failed to move to last append position");
        }

        return appendingContext.init(maxLength);
    }

    private void moveToLastHeader() {
        currentIndex++;
        currentHeaderPosition = HEADER_WORD.position(currentIndex);
        long header;
        do {
            if (headerAccessor.wrap(currentHeaderPosition, headerBuffer)) {
                header = headerBuffer.getLongVolatile(0);
                if (header != 0) {
                    currentHeaderPosition = HEADER_WORD.position(++currentIndex);
                }
            } else {
                LOGGER.error("Failed to wrap header buffer for position {} when advancing to last append position",
                        currentHeaderPosition);
                return;
            }
        } while (header != 0);
    }

    private boolean advanceToLastAppendPosition() {
        if (currentHeaderPosition == NOT_INITIALISED) {
            currentIndex = 0;
            currentHeaderPosition = HEADER_WORD.position(currentIndex);
            long header;
            do {
                if (headerAccessor.wrap(currentHeaderPosition, headerBuffer)) {
                    header = headerBuffer.getLongVolatile(0);
                    if (header != 0) {
                        currentHeaderPosition = HEADER_WORD.position(++currentIndex);
                        short headerAppenderId = appenderId(header);
                        if (headerAppenderId == appenderId) {
                            currentPayloadPosition = payloadPosition(header);
                        }
                    }
                } else {
                    LOGGER.error("Failed to wrap header buffer for position {} when advancing to last append position",
                            currentHeaderPosition);
                    return false;
                }
            } while (header != 0);

            if (currentPayloadPosition != HeaderCodec.initialPayloadPosition()) {
                //load payload length and add to currentPayloadPosition
                if (payloadAccessor.wrap(currentPayloadPosition, payloadBuffer)) {
                    final int payloadLength = payloadBuffer.getInt(0);
                    currentPayloadPosition += payloadLength + LENGTH_LENGTH;
                } else {
                    LOGGER.error("Failed to wrap payload buffer for position {} while advancing to new region",
                            currentPayloadPosition);
                    return false;
                }
            }

        }
        return true;
    }

    @Override
    public void close() {
        regionAccessor.close();
        appenderIdPool.release(appenderId);
        LOGGER.info("Closed appender. id={} for queue={}", appenderId, queueName);
    }

    private class MaxLengthAppendingContext implements AppendingContext {
        final UnsafeBuffer messageBuffer = new UnsafeBuffer();
        boolean closed = true;

        MaxLengthAppendingContext init(int maxLength) {
            if (payloadAccessor.wrap(currentPayloadPosition, payloadBuffer)) {
                if (payloadBuffer.capacity() < maxLength + LENGTH_LENGTH) { //maxLength does not fit capacity of payload buffer
                    //should re-map the payload buffer to the new memory region
                    currentPayloadPosition += payloadBuffer.capacity();
                    if (!payloadAccessor.wrap(currentPayloadPosition, payloadBuffer)) {
                        LOGGER.error("Failed to wrap payload buffer for position {} while advancing to new region",
                                currentPayloadPosition);
                        throw new RuntimeException("Failed to wrap payload buffer");
                    }
                }
                messageBuffer.wrap(payloadBuffer, PAYLOAD_OFFSET, maxLength);
            } else {
                LOGGER.error("Failed to wrap payload buffer for position {}", currentPayloadPosition);
                throw new RuntimeException("Failed to wrap payload buffer");
            }

            closed = false;
            return this;
        }

        @Override
        public MutableDirectBuffer buffer() {
            if (!closed) {
                return messageBuffer;
            }
            throw new IllegalStateException("Appending context is closed");
        }

        @Override
        public void abort() {
            reset();
        }

        private void reset() {
            closed = true;
            messageBuffer.wrap(0, 0);
        }

        @Override
        public long commit(int length) {
            if (!closed) {
                payloadBuffer.putInt(LENGTH_OFFSET, length);

                long header = HeaderCodec.header(appenderId, currentPayloadPosition);

                if (headerAccessor.wrap(currentHeaderPosition, headerBuffer)) {
                    while (!headerBuffer.compareAndSetLong(0, 0, header)) {
                        moveToLastHeader();
                    }

                    final long appendIndex = currentIndex;
                    currentIndex++;
                    currentHeaderPosition = HEADER_WORD.position(currentIndex);
                    currentPayloadPosition += length + LENGTH_LENGTH;

                    reset();
                    return appendIndex;
                } else {
                    LOGGER.error("Failed to wrap header buffer for position {}", currentHeaderPosition);
                    return WRAP_HEADER_REGION_ERROR;
                }
            }
            return APPENDING_CONTEXT_CLOSED;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
