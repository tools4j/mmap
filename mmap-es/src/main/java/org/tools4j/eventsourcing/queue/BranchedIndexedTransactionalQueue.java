/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
