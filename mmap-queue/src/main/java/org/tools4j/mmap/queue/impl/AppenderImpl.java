package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.region.api.DynamicMapping;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class AppenderImpl implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppenderImpl.class);

    private final String queueName;
    private final int appenderId;
    private final DynamicMapping header;
    private final DynamicMapping payload;
    private final AppendingContextImpl context = new AppendingContextImpl();
    private long currentIndex;
    private boolean closed;

    public AppenderImpl(final String queueName, final QueueRegions regions, final AppenderIdPool appenderIdPool) {
        this.queueName = requireNonNull(queueName);
        this.appenderId = appenderIdPool.acquire();
        this.header = requireNonNull(regions.header());
        this.payload = requireNonNull(regions.payload(appenderId));
        this.currentIndex = Index.NULL;
        initialMoveToEnd();
    }

    private static void checkIndexNotExceedingMax(final long index) {
        if (index > Index.MAX) {
            throw new IllegalStateException("Max index reached: " + Index.MAX);
        }
    }

    /** Binary search to move to the end starting from first entry */
    private void initialMoveToEnd() {
        final DynamicMapping hdr = header;
        final long lastIndex = Headers.binarySearchLastIndex(hdr, Index.FIRST);
        final long endIndex = lastIndex + 1;
        checkIndexNotExceedingMax(endIndex);
        if (lastIndex >= Index.FIRST) {
            currentIndex = lastIndex;
        }
    }

    /** Linear move to the end starting from current index */
    private void moveToEnd() {
        final DynamicMapping hdr = header;
        long endIndex = currentIndex;
        do {
            endIndex++;
        } while (Headers.isValidHeaderAt(hdr, endIndex));
        checkIndexNotExceedingMax(endIndex);
        if (endIndex != currentIndex) {
            currentIndex = endIndex;
        }
    }

    @Override
    public long append(final DirectBuffer buffer, final int offset, final int length) {
        try (final AppendingContext context = appending()) {
            context.buffer().putBytes(0, buffer, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public AppendingContext appending() {
        return context.init();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!isClosed()) {
            closed = true;
            currentIndex = Index.NULL;
            currentHeader = NULL_HEADER;
            LOGGER.info("Appender closed, queue={}", queueName);
        }
    }

    @Override
    public String toString() {
        return "AppenderImpl:queue=" + queueName + "|closed=" + closed;
    }

    private final class AppendingContextImpl implements AppendingContext {

        MutableDirectBuffer buffer;

        AppendingContext init() {
            if (buffer != null) {
                abort();
                throw new IllegalStateException("Appending context not closed");
            }

        }

        @Override
        public MutableDirectBuffer buffer() {
            return null;
        }

        @Override
        public void abort() {

        }

        @Override
        public long commit(final int length) {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
