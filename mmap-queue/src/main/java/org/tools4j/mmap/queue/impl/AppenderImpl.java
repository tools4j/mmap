package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.region.api.Region;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.NULL_HEADER;

final class AppenderImpl implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppenderImpl.class);

    private final String queueName;
    private final int appenderId;
    private final Region header;
    private final Region payload;
    private final AppendingContextImpl context = new AppendingContextImpl();
    private long currentIndex;
    private long currentHeader;
    private boolean closed;

    public AppenderImpl(final String queueName, final QueueRegions regions, final AppenderIdPool appenderIdPool) {
        this.queueName = requireNonNull(queueName);
        this.appenderId = appenderIdPool.acquire();
        this.header = requireNonNull(regions.header());
        this.payload = requireNonNull(regions.payload(appenderId));
        this.currentIndex = Index.NULL;
        this.currentHeader = NULL_HEADER;
        moveToEnd();
    }

    private void moveToEnd() {

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
        return context.init;
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
