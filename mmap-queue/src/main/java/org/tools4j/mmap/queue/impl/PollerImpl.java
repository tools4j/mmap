package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.region.api.DynamicRegion;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.HEADER_WORD;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class PollerImpl implements Poller {
    private static final Logger LOGGER = LoggerFactory.getLogger(PollerImpl.class);

    private final String queueName;
    private final DynamicRegion header;
    private final QueueRegions regions;
    private long nextIndex;
    private long currentIndex;
    private long currentHeader;
    private int errorState = PENDING_OPEN;

    PollerImpl(final String queueName, final QueueRegions regions) {
        this.queueName = requireNonNull(queueName);
        this.header = requireNonNull(regions.header());
        this.regions = requireNonNull(regions);
        this.nextIndex = Index.FIRST;
        this.currentIndex = Index.NULL;
        this.currentHeader = NULL_HEADER;
    }

    @Override
    public long lastIndex() {
        checkNotClosed();
        return Headers.binarySearchLastIndex(header, Index.FIRST);
    }

    @Override
    public boolean hasEntry(final long index) {
        checkNotClosed();
        return Headers.isValidHeaderAt(header, index);
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
        nextIndex = Index.FIRST;
    }

    @Override
    public void seekLast() {
        nextIndex = Index.LAST;
    }

    @Override
    public void seekEnd() {
        nextIndex = Index.END;
    }

    @Override
    public void seekNext(final long index) {
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
        final DynamicRegion region = regions.payload(appenderId);
        final boolean success = region.moveTo(payloadPosition);
        assert success : "moving to payload position failed";
        return region.buffer();
    }

    private int moveHeaderToNext() {
        final long nex = nextIndex;
        final long cur = currentIndex;
        if (cur == nex) {
            return cur >= 0 ? ENTRY_POLLED : BEFORE_FIRST;
        }
        final int upd;
        if (cur < nex) {
            upd = cur < Index.MAX ? moveTo(cur + 1) : AFTER_MAX;
        } else {
            upd = cur > Index.FIRST ? moveTo(cur - 1) : BEFORE_FIRST;
        }
        return (upd == CURSOR_MOVED && currentIndex == nex) ? ENTRY_POLLED : upd;
    }

    private int moveTo(final long index) {
        final int error = errorState;
        if (error == CLOSED) {
            return CLOSED;
        }
        final long position = HEADER_WORD.position(index);
        final DynamicRegion headerRegion = this.header;
        if (!headerRegion.moveTo(position)) {
            return error;
        }
        if (error == PENDING_OPEN) {
            errorState = PENDING_NEXT;
        }
        final long header = headerRegion.buffer().getLongVolatile(0);
        if (header == NULL_HEADER) {
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
            throw new IllegalStateException("Poller is closed");
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {
            errorState = CLOSED;
            nextIndex = Index.FIRST;
            currentIndex = Index.NULL;
            currentHeader = NULL_HEADER;
            LOGGER.info("Poller closed, queue={}", queueName);
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
