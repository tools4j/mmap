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
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.HeaderCodec.HEADER_WORD;
import static org.tools4j.mmap.queue.impl.HeaderCodec.appenderId;
import static org.tools4j.mmap.queue.impl.HeaderCodec.payloadPosition;

final class DefaultAppender implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAppender.class);
    private static final long NOT_INITIALISED = -1;
    private static final int LENGTH_SIZE = 4;
    private static final int LENGTH_OFFSET = 0;
    private static final int PAYLOAD_OFFSET = LENGTH_OFFSET + LENGTH_SIZE;

    private final short appenderId;
    private final AppenderIdPool appenderIdPool;

    private final String queueName;
    private final RegionMapper headerMapper;
    private final RegionMapper payloadMapper;
    private final MaxLengthAppendingContext appendingContext;

    private long currentHeaderPosition = NOT_INITIALISED;
    private long currentIndex = NOT_INITIALISED;
    private long currentPayloadPosition = HeaderCodec.initialPayloadPosition();

    DefaultAppender(final String queueName,
                    final QueueRegionMappers regionAccessor,
                    final AppenderIdPool appenderIdPool) {
        this.queueName = requireNonNull(queueName);
        this.appenderIdPool = requireNonNull(appenderIdPool);
        this.appenderId = appenderIdPool.acquire();
        this.headerMapper = regionAccessor.header();
        this.payloadMapper = regionAccessor.payload(appenderId);
        this.appendingContext = new MaxLengthAppendingContext();

        advanceToLastAppendPosition();
    }

    @Override
    public long append(final DirectBuffer buffer, final int offset, final int length) {
        if (!appendingContext.isClosed()) {
            return APPENDING_CONTEXT_IN_USE;
        }

        Region header;
        Region payload;

        boolean onLastAppendPosition = advanceToLastAppendPosition();
        if (!onLastAppendPosition) {
            return MOVE_TO_END_ERROR;
        }

        if (!(payload = payloadMapper.map(currentPayloadPosition)).isReady()) {
            return MAP_PAYLOAD_REGION_ERROR;
        }
        if (!(header = headerMapper.map(currentHeaderPosition)).isReady()) {
            return MAP_HEADER_REGION_ERROR;
        }

        if (payload.bytesAvailable() < length + LENGTH_SIZE) { //message does not fit capacity of payload buffer
            //should re-map the payload buffer to the new memory region
            currentPayloadPosition += payload.bytesAvailable();
            if (!(payload = payloadMapper.map(currentHeaderPosition)).isReady()) {
                return MAP_PAYLOAD_REGION_ERROR;
            }
        }
        final MutableDirectBuffer payloadBuffer = payload.buffer();
        payloadBuffer.putInt(LENGTH_OFFSET, length);
        buffer.getBytes(offset, payloadBuffer, PAYLOAD_OFFSET, length);

        final long headerValue = HeaderCodec.header(appenderId, currentPayloadPosition);

        while (!header.buffer().compareAndSetLong(0, 0, headerValue)) {
            if ((header = moveToLastHeader()) == null) {
                return MAP_HEADER_REGION_ERROR;
            }
        }

        final long appendIndex = currentIndex;
        currentIndex++;
        currentHeaderPosition = HEADER_WORD.position(currentIndex);
        currentPayloadPosition += length + LENGTH_SIZE;

        return appendIndex;
    }

    @Override
    public AppendingContext appending(int maxLength) {
        if (!advanceToLastAppendPosition()) {
            throw new IllegalStateException("Failed to move to last append position");
        }
        return appendingContext.init(maxLength);
    }

    private Region moveToLastHeader() {
        currentIndex++;
        currentHeaderPosition = HEADER_WORD.position(currentIndex);
        Region header;
        while ((header = headerMapper.map(currentHeaderPosition)).isReady()) {
            final long headerValues = header.buffer().getLongVolatile(0);
            if (headerValues == 0) {
                return header;
            }
            currentHeaderPosition = HEADER_WORD.position(++currentIndex);
        }
        return null;
    }

    private boolean advanceToLastAppendPosition() {
        if (currentHeaderPosition == NOT_INITIALISED) {
            currentIndex = 0;
            currentHeaderPosition = HEADER_WORD.position(currentIndex);
            long header;
            do {
                header = this.headerMapper.map(currentHeaderPosition)
                        .buffer()
                        .getLongVolatile(0);
                if (header != 0) {
                    currentHeaderPosition = HEADER_WORD.position(++currentIndex);
                    short headerAppenderId = appenderId(header);
                    if (headerAppenderId == appenderId) {
                        currentPayloadPosition = payloadPosition(header);
                    }
                }
            } while (header != 0);

            if (currentPayloadPosition != HeaderCodec.initialPayloadPosition()) {
                //load payload length and add to currentPayloadPosition
                final int payloadLength = payloadMapper.map(currentPayloadPosition)
                        .buffer()
                        .getInt(0);
                currentPayloadPosition += payloadLength + LENGTH_SIZE;
            }

        }
        return true;
    }

    @Override
    public void close() {
        headerMapper.close();//TODO close or is this shared ?
        payloadMapper.close();//TODO close or is this shared ?
        appenderIdPool.release(appenderId);
        LOGGER.info("Closed appender. id={} for queue={}", appenderId, queueName);
    }

    private class MaxLengthAppendingContext implements AppendingContext {
        final UnsafeBuffer messageBuffer = new UnsafeBuffer();
        Region payload;

        MaxLengthAppendingContext init(final int maxLength) {
            if (payload != null) {
                reset();
            }
            ;
            if (!(payload = payloadMapper.map(currentPayloadPosition)).isReady()) {
                throw new IllegalStateException("Mapping " + queueName + " payload to position " +
                        currentPayloadPosition + " failed: readiness not achieved in time");
            }
            if (payload.bytesAvailable() < maxLength + LENGTH_SIZE) { //maxLength does not fit capacity of payload buffer
                //should re-map the payload buffer to the new memory region
                currentPayloadPosition += payload.bytesAvailable();
                if (!(payload = payloadMapper.map(currentPayloadPosition)).isReady()) {
                    throw new IllegalStateException("Mapping " + queueName + " payload to position " +
                            currentPayloadPosition + " failed: readiness not achieved in time");
                }
            }
            messageBuffer.wrap(payload.buffer(), PAYLOAD_OFFSET, maxLength);
            return this;
        }

        @Override
        public MutableDirectBuffer buffer() {
            if (payload != null) {
                return messageBuffer;
            }
            throw new IllegalStateException("Appending context is closed");
        }

        @Override
        public void abort() {
            reset();
        }

        private void reset() {
            payload = null;
            messageBuffer.wrap(0, 0);
        }

        @Override
        public long commit(int length) {
            if (payload != null) {
                payload.buffer().putInt(LENGTH_OFFSET, length);

                final long headerValue = HeaderCodec.header(appenderId, currentPayloadPosition);

                Region header;
                if (!(header = headerMapper.map(currentHeaderPosition)).isReady()) {
                    throw new IllegalStateException("Mapping " + queueName + " header to position " +
                            currentHeaderPosition + " failed: readiness not achieved in time");
                }
                while (!header.buffer().compareAndSetLong(0, 0, headerValue)) {
                    if ((header = moveToLastHeader()) == null) {
                        throw new IllegalStateException("Moving " + queueName + " header to last position " +
                                currentHeaderPosition + " failed: readiness not achieved in time");
                    }
                }

                final long appendIndex = currentIndex;
                currentIndex++;
                currentHeaderPosition = HEADER_WORD.position(currentIndex);
                currentPayloadPosition += length + LENGTH_SIZE;

                reset();
                return appendIndex;
            }
            return APPENDING_CONTEXT_CLOSED;
        }

        @Override
        public boolean isClosed() {
            return payload == null;
        }
    }
}
