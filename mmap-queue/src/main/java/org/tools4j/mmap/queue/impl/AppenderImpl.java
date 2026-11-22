/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.internal.Closeables;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.config.QueueLayoutConfig;

import java.nio.ByteBuffer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Exceptions.headerMoveException;
import static org.tools4j.mmap.queue.impl.Exceptions.payloadMoveException;
import static org.tools4j.mmap.queue.impl.Exceptions.payloadPositionExceedsMaxException;
import static org.tools4j.mmap.queue.impl.Headers.MAX_PAYLOAD_POSITION;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class AppenderImpl implements Appender, Describable {

    private final String queueName;
    private final AppenderMappings mappings;
    private final int appenderId;
    private final DynamicMutableMapping header;
    private final DynamicMutableMapping payload;
    private final AppendingContextImpl context;
    private final int safeEntryLength;
    private final int maxEntryLength;
    private final Runnable onClose;
    private final LifecycleListener lifecycleListener;
    private long endIndex;
    private long lastOwnHeader;
    private int lastOwnPayloadLength;
    private boolean closed;

    public AppenderImpl(final String queueName, final AppenderMappings mappings, final QueueLayoutConfig layout,
                        final Runnable onClose, final LifecycleListener lifecycleListener) {
        try {
            this.queueName = requireNonNull(queueName);
            this.mappings = requireNonNull(mappings);
            this.appenderId = mappings.appenderId();
            this.header = requireNonNull(mappings.header());
            this.payload = requireNonNull(mappings.payload());
            this.safeEntryLength = layout.safeEntryLength();
            this.maxEntryLength = layout.maxEntryLength();
            this.context = new AppendingContextImpl(this);
            this.onClose = requireNonNull(onClose);
            this.lifecycleListener = requireNonNull(lifecycleListener);
            this.endIndex = Index.NULL;
            this.lastOwnHeader = NULL_HEADER;
            this.lastOwnPayloadLength = -1;
            initialMoveToEnd();
        } catch (final Throwable t) {
            Closeables.closeOnFailure(mappings, t);
            throw t;
        }
        lifecycleListener.onOpened(this);
    }

    private void checkIndexNotExceedingMax(final long index) {
        if (index > Index.MAX) {
            throw new IllegalStateException("Max index " + Index.MAX + " exceeded for queue " + queueName);
        }
    }

    /**
     * Move to end while also finding the last of our own headers: binary search of the last entry, backward scan to
     * our last own entry unless our ID has none, then forward scan to the end, which may have moved since the binary
     * search if other appenders appended concurrently
     */
    private void initialMoveToEnd() {
        final DynamicMapping hdr = header;
        //noinspection UnnecessaryLocalVariable
        final int ownAppenderId = appenderId;
        final long lastIndex = Headers.binarySearchLastIndex(hdr, Index.FIRST);
        long lastOwnHeader = mappings.payloadFileExisted() ? lastOwnHeaderBackwardFrom(lastIndex) : NULL_HEADER;
        long endIndex = lastIndex + 1;
        long header;
        while ((header = Headers.moveAndGetHeader(hdr, endIndex)) != NULL_HEADER) {
            endIndex++;
            if (Headers.appenderId(header) == ownAppenderId) {
                lastOwnHeader = header;
            }
        }
        checkIndexNotExceedingMax(endIndex);
        this.endIndex = endIndex;
        this.lastOwnHeader = lastOwnHeader;
        this.lastOwnPayloadLength = lastOwnHeader == NULL_HEADER ? -1 : payloadLength(lastOwnHeader);
    }

    private long lastOwnHeaderBackwardFrom(final long lastIndex) {
        for (long index = lastIndex; index >= Index.FIRST; index--) {
            final long header = Headers.moveAndGetHeader(this.header, index);
            if (header != NULL_HEADER && Headers.appenderId(header) == appenderId) {
                return header;
            }
        }
        return NULL_HEADER;
    }

    /**
     * Returns the payload length including length prefix of an entry previously appended with this appender's ID, for
     * instance by a previous owner of the ID, so that new payload is appended after it.
     */
    private int payloadLength(final long ownHeader) {
        final long payloadPosition = Headers.payloadPosition(ownHeader);
        if (!payload.moveTo(payloadPosition, DynamicMapping.STRETCH_LENGTH)) {
            throw payloadMoveException(this, payloadPosition);
        }
        //NOTE: plain read is safe, the length was written before the header, which we read with acquire semantics
        return payload.buffer().getInt(0) + Integer.BYTES;
    }

    @Override
    public long append(final byte[] bytes) {
        return append(bytes, 0, bytes.length);
    }

    @Override
    public long append(final byte[] bytes, final int offset, final int length) {
        try (final AppendingContext context = appending(length)) {
            context.buffer().putBytes(0, bytes, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public long append(final ByteBuffer buffer) {
        return append(buffer, buffer.remaining());
    }

    @Override
    public long append(final ByteBuffer buffer, final int length) {
        try (final AppendingContext context = appending(length)) {
            context.buffer().putBytes(0, buffer, length);
            return context.commit(length);
        }
    }

    @Override
    public long append(final ByteBuffer buffer, final int offset, final int length) {
        try (final AppendingContext context = appending(length)) {
            context.buffer().putBytes(0, buffer, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public long append(final MemoryBuffer buffer, final int offset, final int length) {
        try (final AppendingContext context = appending(length)) {
            context.buffer().putBytes(0, buffer, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public AppendingContext appending() {
        return appending(safeEntryLength);
    }

    @Override
    public AppendingContext appending(final int capacity) {
        checkNotClosed();
        return context.init(capacity);
    }

    private long appendEntry(final long payloadPosition, final int payloadLength) {
        assert !isClosed() : "Appender is closed";//on commit, i.e. open context, which the appender aborts when closed
        final DynamicMutableMapping hdr = header;
        final MutableMemoryBuffer buf = hdr.buffer();
        final long headerValue = Headers.header(appenderId, payloadPosition);
        long index = endIndex;
        assert index <= Index.MAX : "endIndex never exceeds max index, only the retry loop advances beyond it";
        while (!buf.compareAndSetLong(0, NULL_HEADER, headerValue)) {
            do {
                index++;
                checkIndexNotExceedingMax(index);
                if (!Headers.moveToHeaderIndex(hdr, index)) {
                    throw headerMoveException(this, Headers.headerPositionForIndex(index));
                }
                //NOTE: only after a successful move, endIndex must always be where hdr is mapped
                endIndex = index;
            } while (buf.getLongAcquire(0) != NULL_HEADER);
        }
        final long nextIndex = index + 1;
        if (nextIndex <= Index.MAX && tryMoveToHeaderIndexAfterCommit(hdr, nextIndex)) {
            endIndex = nextIndex;
        }
        //else: leave endIndex at `index` (already durably claimed) and hdr mapped there too - the next append's own
        //retry loop will find the slot taken and correctly advance from there, without losing this append's result
        //or skipping an index if the pre-position move fails

        lastOwnHeader = headerValue;
        lastOwnPayloadLength = payloadLength;
        return index;
    }

    /**
     * Pre-positions the header after the entry has been committed, which must not throw: a failed move leaves the
     * header where it is, and the next append repeats the move and gets the error.
     */
    private static boolean tryMoveToHeaderIndexAfterCommit(final DynamicMapping hdr, final long index) {
        try {
            return Headers.moveToHeaderIndex(hdr, index);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private void checkNotClosed() {
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
            context.abort();
            endIndex = Index.NULL;
            lastOwnHeader = NULL_HEADER;
            lastOwnPayloadLength = -1;
            mappings.close();
            onClose.run();
            lifecycleListener.onClosed(this);
        }
    }

    private static final class AppendingContextImpl implements AppendingContext {
        final AppenderImpl appender;
        final DynamicMutableMapping payload;
        final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
        int maxLength = -1;

        AppendingContextImpl(final AppenderImpl appender) {
            this.appender = requireNonNull(appender);
            this.payload = requireNonNull(appender.payload);
        }

        AppendingContext init(final int capacity) {
            if (!isClosed()) {
                abort();
                throw new IllegalStateException("Appending context has not been closed");
            }
            if (capacity > appender.maxEntryLength) {
                throw new IllegalArgumentException("Capacity " + capacity + " exceeds max entry length " +
                        appender.maxEntryLength);
            }
            this.maxLength = initPayloadBuffer(appender.lastOwnHeader, appender.lastOwnPayloadLength, Math.max(0, capacity));
            return this;
        }

        private int initPayloadBuffer(final long lastOwnHeader, final int lastOwnPayloadLen, final int capacity) {
            assert capacity >= 0 && capacity <= appender.maxEntryLength;
            final DynamicMutableMapping pld = payload;
            final long nextPosition = lastOwnHeader == NULL_HEADER ? 0L :
                    Headers.nextPayloadPosition(Headers.payloadPosition(lastOwnHeader), lastOwnPayloadLen);
            final long payloadPosition = entryPosition(pld.regionMetrics(), nextPosition, capacity);
            if (payloadPosition > MAX_PAYLOAD_POSITION) {
                throw payloadPositionExceedsMaxException(appender, payloadPosition);
            }
            if (!pld.moveTo(payloadPosition, DynamicMapping.STRETCH_LENGTH)) {
                throw payloadMoveException(appender, payloadPosition);
            }
            buffer.wrap(pld.buffer(), Integer.BYTES, capacity);
            return capacity;
        }

        /**
         * Returns the next payload position if the entry fits there, which an entry up to the safe entry length always
         * does, and otherwise the start of the next payload region
         */
        private static long entryPosition(final RegionMetrics metrics, final long nextPosition, final int capacity) {
            if (Integer.BYTES + capacity <= metrics.maxLengthAtPosition(nextPosition)) {
                return nextPosition;
            }
            return metrics.regionPosition(nextPosition) + metrics.regionSize();
        }

        @Override
        public MutableMemoryBuffer buffer() {
            return buffer;
        }

        @Override
        public void abort() {
            maxLength = -1;
            buffer.unwrap();
        }

        @Override
        public long commit(final int length) {
            validateLength(length, maxLength);
            abort();
            final DynamicMutableMapping pld = payload;
            pld.buffer().putInt(0, length);
            return appender.appendEntry(pld.position(), length + Integer.BYTES);
        }

        private static void validateLength(final int length, final int maxLength) {
            if (maxLength < 0) {
                throw new IllegalStateException("Appending context is closed");
            }
            if (length < 0) {
                throw new IllegalArgumentException("Length cannot be negative: " + length);
            }
            if (length > maxLength) {
                throw new IllegalArgumentException("Length " + length + " exceeds max length " + maxLength);
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

    String appenderName() {
        return queueName + ".appender-" + appenderId;
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("AppenderImpl")
                .append(":queue=").append(queueName)
                .append("|appenderId=").append(appenderId)
                .append("|closed=").append(closed);
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }

}
