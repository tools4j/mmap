package org.tools4j.eventsourcing.queue;

import org.agrona.collections.MutableReference;
import org.tools4j.eventsourcing.api.IndexedTransactionalQueue;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.eventsourcing.api.Transaction;

import java.io.IOException;
import java.util.Objects;

public final class BranchedIndexedTransactionalQueue implements IndexedTransactionalQueue {
    private final DefaultIndexedPollerFactory basePollerFactory;
    private final IndexedTransactionalQueue branchQueue;
    private final Poller.IndexPredicate branchPredicate;

    public BranchedIndexedTransactionalQueue(final DefaultIndexedPollerFactory basePollerFactory,
                                             final IndexedTransactionalQueue branchQueue,
                                             final Poller.IndexPredicate branchPredicate) {
        this.basePollerFactory = Objects.requireNonNull(basePollerFactory);
        this.branchQueue = Objects.requireNonNull(branchQueue);
        this.branchPredicate = Objects.requireNonNull(branchPredicate);
    }

    @Override
    public Transaction appender() {
        return branchQueue.appender();
    }

    @Override
    public Poller createPoller(final Poller.IndexPredicate skipPredicate,
                               final Poller.IndexPredicate pausePredicate,
                               final Poller.IndexConsumer beforeIndexHandler,
                               final Poller.IndexConsumer afterIndexHandler) throws IOException {
        final Poller branchQueuePoller = branchQueue.createPoller(
                skipPredicate,
                pausePredicate,
                beforeIndexHandler,
                afterIndexHandler);
        final MutableReference<Poller> currentPollerRef = new MutableReference<>();

        final Poller.IndexPredicate switchToBranch = (index, source, sourceId, eventTimeNanos) -> {
            if (this.branchPredicate.test(index, source, sourceId, eventTimeNanos)) {
                currentPollerRef.set(branchQueuePoller);
                return true;
            } else {
                return false;
            }
        };

        final Poller firstLegPoller = basePollerFactory.createPoller(
                switchToBranch.or(skipPredicate),
                pausePredicate,
                beforeIndexHandler,
                afterIndexHandler);

        currentPollerRef.set(firstLegPoller);

        return consumer -> currentPollerRef.get().poll(consumer);
    }
}
