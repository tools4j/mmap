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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class PollerImpl implements Poller {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerImpl.class);

    private final String queueName;
    private final ReaderMappings mappings;
    private final OffsetMapping header;
    private long nextIndex;
    private long currentIndex;
    private long currentHeader;
    private int errorState = PENDING_OPEN;

    PollerImpl(final String queueName, final ReaderMappings mappings) {
        this.queueName = requireNonNull(queueName);
        this.mappings = requireNonNull(mappings);
        this.header = requireNonNull(mappings.header());
        this.nextIndex = Index.FIRST;
        this.currentIndex = Index.NULL;
        this.currentHeader = NULL_HEADER;
    }

    @Override
    public long currentIndex() {
        return currentIndex;
    }

    @Override
    public long nextIndex() {
        return nextIndex;
    }

    @Override
    public void seekStart() {
        checkNotClosed();
        nextIndex = Index.FIRST;
    }

    @Override
    public void seekLast() {
        checkNotClosed();
        nextIndex = Index.LAST;
    }

    @Override
    public void seekEnd() {
        checkNotClosed();
        nextIndex = Index.END;
    }

    @Override
    public void seekNext(final long index) {
        checkNotClosed();
        nextIndex = nextIndex(Index.FIRST, index);
    }

    @Override
    public int poll(final EntryHandler entryHandler) {
        final int result = moveHeaderToNext();
        if (result == ENTRY_POLLED) {
            final long curIndex = currentIndex;
            final long moveNext = handleCurrentEntry(entryHandler, curIndex);
            nextIndex = nextIndex(curIndex, moveNext);
            return ENTRY_POLLED;
        }
        return result;
    }

    private long handleCurrentEntry(final EntryHandler entryHandler, final long index) {
        final DirectBuffer buffer = payloadBuffer();
        final int length = buffer.getInt(0);
        try {
            return entryHandler.onEntry(index, buffer, Integer.BYTES, length);
        } catch (final Exception e) {
            LOGGER.error("Unexpected exception thrown by entry handler for message entry {} of queue {}", index,
                    queueName, e);
            return Move.NONE;//FIXME make sure error does not repeat forever, or at least throttle logging
        }
    }

    private DirectBuffer payloadBuffer() {
        final long header = currentHeader;
        final int appenderId = Headers.appenderId(header);
        final long payloadPosition = Headers.payloadPosition(header);
        final OffsetMapping mapping = mappings.payload(appenderId);
        final boolean success = mapping.moveTo(payloadPosition);
        assert success : "moving to payload position failed";
        return mapping.buffer();
    }

    private int moveHeaderToNext() {
        final long nex = nextIndex;
        final long cur = currentIndex;
        if (cur == nex) {
            return cur >= 0 ? ENTRY_POLLED : BEFORE_FIRST;
        }
        final int upd;
        if (cur < nex) {
            upd = cur < Index.MAX ? moveTo(cur, 1) : AFTER_MAX;
        } else {
            upd = cur > Index.FIRST ? moveTo(cur, -1) : BEFORE_FIRST;
        }
        return (upd == CURSOR_MOVED && currentIndex == nex) ? ENTRY_POLLED : upd;
    }

    private int moveTo(final long curIndex, final int inc) {
        final int error = errorState;
        if (error == CLOSED) {
            return CLOSED;
        }
        final long index = curIndex + inc;
        final long position = Headers.headerPositionForIndex(index);
        final OffsetMapping headerMapping = this.header;
        if (!headerMapping.moveTo(position)) {
            return error;
        }
        if (error == PENDING_OPEN) {
            errorState = PENDING_NEXT;
        }
        final long header = headerMapping.buffer().getLongVolatile(0);
        if (header == NULL_HEADER) {
            final long next = nextIndex;
            if (next > Index.MAX && inc > 0) {
                assert next == Index.LAST || next == Index.END : "invalid special index";
                if (index > 0 && next == Index.LAST) {
                    nextIndex = index - 1;
                    return CURSOR_MOVED;
                }
                nextIndex = index;
            }
            return PENDING_NEXT;
        }
        currentIndex = index;
        currentHeader = header;
        return CURSOR_MOVED;
    }

    @Override
    public boolean isClosed() {
        return errorState == CLOSED;
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Poller " + pollerName() + " is closed");
        }
    }

    private String pollerName() {
        return queueName + ".poller-" + System.identityHashCode(this);
    }

    @Override
    public void close() {
        if (!isClosed()) {
            errorState = CLOSED;
            nextIndex = Index.FIRST;
            currentIndex = Index.NULL;
            currentHeader = NULL_HEADER;
            mappings.close();
            LOGGER.info("Poller closed: {}", pollerName());
        }
    }

    private static long nextIndex(final long currentIndex, final long move) {
        if (move > Index.MAX) {
            return Math.max(move, Index.LAST);
        }
        if (move < -Index.MAX) {
            return Index.NULL;
        }
        final long nextIndex = currentIndex + move;
        return nextIndex >= 0 ? nextIndex : (move > 0 ? Index.END : Index.NULL);
    }

    @Override
    public String toString() {
        return "PollerImpl:queue=" + queueName + "|currentIndex=" + currentIndex + "|nextIndex=" + nextIndex +
                "|closed=" + isClosed();
    }
}
