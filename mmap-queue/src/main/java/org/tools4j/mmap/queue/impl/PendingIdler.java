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

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.config.ReaderConfig;

import static java.util.Objects.requireNonNull;

/**
 * Idles the thread of a poller or entry iterator while there is nothing to read yet: with the pending queue idle
 * strategy while the queue does not exist, and with the pending data idle strategy after failing to read the first
 * entry, until an entry has been read. Each idle strategy is reset when its phase ends, and nothing is idled once an
 * entry has been read, so that reading latency is not affected.
 * <p>
 * Not thread-safe, owned by a single poller or entry iterator.
 */
final class PendingIdler {
    private static final int PENDING_QUEUE = 0;
    private static final int PENDING_DATA = 1;
    private static final int ENTRY_READ = 2;
    private final IdleStrategy pendingQueueIdleStrategy;
    private final IdleStrategy pendingDataIdleStrategy;
    private int phase = PENDING_QUEUE;

    /**
     * Constructor with idle strategies from the given reader configuration.
     *
     * @param config the reader configuration supplying the idle strategies
     */
    PendingIdler(final ReaderConfig config) {
        this(config.pendingQueueIdleStrategy().get(), config.pendingDataIdleStrategy().get());
    }

    /**
     * Constructor with idle strategies.
     *
     * @param pendingQueueIdleStrategy idle strategy while the queue does not exist
     * @param pendingDataIdleStrategy  idle strategy until the first entry has been read
     */
    PendingIdler(final IdleStrategy pendingQueueIdleStrategy, final IdleStrategy pendingDataIdleStrategy) {
        this.pendingQueueIdleStrategy = requireNonNull(pendingQueueIdleStrategy);
        this.pendingDataIdleStrategy = requireNonNull(pendingDataIdleStrategy);
    }

    /**
     * Invoked after failing to read because the queue does not exist yet.
     */
    void onQueuePending() {
        assert phase == PENDING_QUEUE : "queue exists";
        pendingQueueIdleStrategy.idle();
    }

    /**
     * Invoked after failing to read the entry at the given index of an existing queue; idles only for the first entry
     * and only until an entry has been read.
     *
     * @param index the index of the entry that could not be read
     */
    void onNoEntry(final long index) {
        if (phase == ENTRY_READ || index != Index.FIRST) {
            return;
        }
        if (phase == PENDING_QUEUE) {
            pendingQueueIdleStrategy.reset();
            phase = PENDING_DATA;
        }
        pendingDataIdleStrategy.idle();
    }

    /**
     * Invoked after reading an entry.
     */
    void onEntry() {
        if (phase != ENTRY_READ) {
            entryRead();
        }
    }

    private void entryRead() {
        (phase == PENDING_QUEUE ? pendingQueueIdleStrategy : pendingDataIdleStrategy).reset();
        phase = ENTRY_READ;
    }

    @Override
    public String toString() {
        return "PendingIdler:phase=" + switch (phase) {
            case PENDING_QUEUE -> "PENDING_QUEUE";
            case PENDING_DATA -> "PENDING_DATA";
            default -> "ENTRY_READ";
        };
    }
}
