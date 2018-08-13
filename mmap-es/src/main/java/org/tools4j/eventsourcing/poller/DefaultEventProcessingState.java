package org.tools4j.eventsourcing.poller;

import org.agrona.collections.Long2LongHashMap;
import org.tools4j.eventsourcing.api.EventProcessingState;
import org.tools4j.eventsourcing.api.Poller;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class DefaultEventProcessingState implements EventProcessingState, Poller.IndexConsumer {
    private final Long2LongHashMap sourceMap;
    private final LongSupplier systemNanoClock;

    private long id;
    private int source;
    private long sourceId;
    private long eventTimeNanos;
    private long ingestionTimeNanos;

    public DefaultEventProcessingState(final LongSupplier systemNanoClock) {
        this.systemNanoClock = Objects.requireNonNull(systemNanoClock);
        this.sourceMap = new Long2LongHashMap(-1);
        this.eventTimeNanos = 0;
    }

    @Override
    public long sourceId(final int source) {
        return sourceMap.get(source);
    }

    @Override
    public void accept(final long id, final int source, final long sourceId, final long eventTimeNanos) {
        sourceMap.put(source, sourceId);
        this.id = id;
        this.source = source;
        this.sourceId = sourceId;
        this.eventTimeNanos = eventTimeNanos;
        this.ingestionTimeNanos = systemNanoClock.getAsLong();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public int source() {
        return source;
    }

    @Override
    public long sourceId() {
        return sourceId;
    }

    @Override
    public long eventTimeNanos() {
        return eventTimeNanos;
    }

    @Override
    public long ingestionTimeNanos() {
        return ingestionTimeNanos;
    }
}
