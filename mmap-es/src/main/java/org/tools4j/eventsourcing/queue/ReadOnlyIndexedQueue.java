package org.tools4j.eventsourcing.queue;

import org.tools4j.eventsourcing.api.IndexedMessageConsumer;
import org.tools4j.eventsourcing.api.IndexedPollerFactory;
import org.tools4j.eventsourcing.api.IndexedQueue;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.mmap.region.api.RegionRingFactory;

import java.io.IOException;

public final class ReadOnlyIndexedQueue implements IndexedQueue {
    private final IndexedMessageConsumer appender;
    private final IndexedPollerFactory pollerFactory;

    public ReadOnlyIndexedQueue(final String directory,
                                final String filePrefix,
                                final RegionRingFactory regionRingFactory,
                                final int regionSize,
                                final int regionRingSize,
                                final int regionsToMapAhead) throws IOException {

        this.appender = (source, sourceId, eventTimeNanos, buffer, offset, length) -> {
            throw new UnsupportedOperationException("append operation is not supported");
        };

        this.pollerFactory = new DefaultIndexedPollerFactory(
                directory,
                filePrefix,
                regionRingFactory,
                regionSize,
                regionRingSize,
                regionsToMapAhead);
    }

    @Override
    public IndexedMessageConsumer appender() {
        return this.appender;
    }

    @Override
    public Poller createPoller(final Poller.IndexPredicate skipPredicate,
                               final Poller.IndexPredicate pausePredicate,
                               final Poller.IndexConsumer beforeIndexHandler,
                               final Poller.IndexConsumer afterIndexHandler) throws IOException {
        return pollerFactory.createPoller(
                skipPredicate,
                pausePredicate,
                beforeIndexHandler,
                afterIndexHandler);
    }

}
