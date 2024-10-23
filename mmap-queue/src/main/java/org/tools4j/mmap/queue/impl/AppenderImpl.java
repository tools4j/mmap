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
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class AppenderImpl implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppenderImpl.class);

    private final String queueName;
    private final AppenderMappings mappings;
    private final int appenderId;
    private final OffsetMapping header;
    private final OffsetMapping payload;
    private final AppendingContextImpl context;
    private long endIndex;
    private long lastOwnHeader;
    private boolean closed;

    public AppenderImpl(final String queueName, final AppenderMappings mappings) {
        this.queueName = requireNonNull(queueName);
        this.mappings = requireNonNull(mappings);
        this.appenderId = mappings.appenderId();
        this.header = requireNonNull(mappings.header());
        this.payload = requireNonNull(mappings.payload());
        this.context = new AppendingContextImpl(this);
        this.endIndex = Index.NULL;
        this.lastOwnHeader = NULL_HEADER;
        initialMoveToEnd();
    }

    private void checkIndexNotExceedingMax(final long index) {
        if (index > Index.MAX) {
            throw new IllegalStateException("Max index " + Index.MAX + " exceeded for queue " + queueName);
        }
    }

    /** Linear move to end while also remembering the last of our own headers */
    private void initialMoveToEnd() {
        final OffsetMapping hdr = header;
        //noinspection UnnecessaryLocalVariable
        final int ownAppenderId = appenderId;
        long endIndex = Index.FIRST;
        long lastOwnHeader = NULL_HEADER;
        long header;
        while ((header = Headers.header(hdr, endIndex)) != NULL_HEADER) {
            endIndex++;
            if (Headers.appenderId(header) == ownAppenderId) {
                lastOwnHeader = header;
            }
        }
        checkIndexNotExceedingMax(endIndex);
        this.endIndex = endIndex;
        this.lastOwnHeader = lastOwnHeader;
    }

    @Override
    public long append(final DirectBuffer buffer, final int offset, final int length) {
        try (final AppendingContext context = appending(length)) {
            context.buffer().putBytes(0, buffer, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public AppendingContext appending(final int maxLength) {
        ensureNotClosed();
        return context.init(maxLength);
    }

    private long appendEntry(final long payloadPosition) {
        ensureNotClosed();
        final OffsetMapping hdr = header;
        final AtomicBuffer buf = hdr.buffer();
        final long headerValue = Headers.header(appenderId, payloadPosition);
        long index = endIndex;
        while (!buf.compareAndSetLong(0, NULL_HEADER, headerValue)) {
            do {
                index++;
                checkIndexNotExceedingMax(index);
                if (!Headers.moveToHeaderIndex(hdr, index)) {
                    throw headerMoveException(this, Headers.headerPositionForIndex(index));
                }
            } while (buf.getLongVolatile(0) != NULL_HEADER);
        }
        endIndex = index + 1;
        return index;
    }

    private void ensureNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Appender " + appenderName() + " is closed");
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!isClosed()) {
            closed = true;
            endIndex = Index.NULL;
            lastOwnHeader = NULL_HEADER;
            mappings.close();
            LOGGER.info("Appender closed: {}", appenderName());
        }
    }

    private static final class AppendingContextImpl implements AppendingContext {
        final AppenderImpl appender;
        final OffsetMapping payload;
        final int maxEntrySize;
        final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
        int maxLength = -1;

        AppendingContextImpl(final AppenderImpl appender) {
            this.appender = requireNonNull(appender);
            this.payload = requireNonNull(appender.payload);
            this.maxEntrySize = payload.regionSize() - Integer.SIZE;
        }

        AppendingContext init(final int maxLength) {
            if (!isClosed()) {
                abort();
                throw new IllegalStateException("Appending context has not been closed");
            }
            if (maxLength > maxEntrySize) {
                throw new IllegalArgumentException("Max length " + maxLength + " exceeds maximum allowed entry size " +
                        maxEntrySize);
            }
            this.maxLength = initPayloadBuffer(appender.lastOwnHeader, Math.max(0, maxLength));
            return this;
        }

        int initPayloadBuffer(final long lastOwnHeader, final int maxLength) {
            assert maxLength >= 0 && maxLength <= maxEntrySize;
            final OffsetMapping pld = payload;
            final int minCapacity = maxLength + Integer.SIZE;
            long payloadPosition = lastOwnHeader == NULL_HEADER ? 0L :
                    Headers.nextPayloadPosition(Headers.payloadPosition(lastOwnHeader), minCapacity);
            if (!pld.moveTo(payloadPosition)) {
                throw payloadMoveException(appender, payloadPosition);
            }
            if (pld.bytesAvailable() < minCapacity && !pld.moveToNextRegion()) {
                final long regionStartPosition = pld.regionMetrics().regionPosition(payloadPosition + pld.regionSize());
                throw payloadMoveException(appender, regionStartPosition);
            }
            buffer.wrap(pld.buffer(), Integer.SIZE, maxLength);
            return maxLength;
        }

        @Override
        public MutableDirectBuffer buffer() {
            return buffer;
        }

        @Override
        public void abort() {
            this.maxLength = -1;
        }

        @Override
        public long commit(final int length) {
            final int max = maxLength;
            maxLength = -1;
            buffer.wrap(0, 0);
            validateLength(length, max);
            final OffsetMapping pld = payload;
            pld.buffer().putInt(0, length);
            return appender.appendEntry(pld.position());
        }

        static void validateLength(final int length, final int maxLength) {
            if (length < 0) {
                throw new IllegalArgumentException("Length " + length + " cannot be negative");
            } else if (length > maxLength) {
                if (maxLength >= 0) {
                    throw new IllegalArgumentException("Length " + length + " exceeds max length " + maxLength);
                }
                throw new IllegalStateException("Appending context is closed");
            }
        }

        @Override
        public boolean isClosed() {
            return maxLength < 0;
        }

        @Override
        public String toString() {
            return "AppendingContextImpl" +
                    ":queue=" + appender.queueName +
                    "|appenderId=" + appender.appenderId +
                    "|closed=" + isClosed();
        }
    }

    private String appenderName() {
        return queueName + ".appender-" + appenderId;
    }

    @Override
    public String toString() {
        return "AppenderImpl:queue=" + queueName + "|appenderId=" + appenderId + "|closed=" + closed;
    }

    private static IllegalStateException headerMoveException(final AppenderImpl appender, final long position) {
        return mappingMoveException(appender.appenderName() + ".header", position);
    }

    private static IllegalStateException payloadMoveException(final AppenderImpl appender, final long position) {
        return mappingMoveException(appender.appenderName() + ".payload", position);
    }

    private static IllegalStateException mappingMoveException(final String name, final long position) {
        throw new IllegalStateException("Moving " + name + " mapping to position " + position + " failed");
    }

}
