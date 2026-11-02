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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for {@link PendingIdler}.
 */
class PendingIdlerTest {

    private final CountingIdleStrategy pendingQueue = new CountingIdleStrategy();
    private final CountingIdleStrategy pendingData = new CountingIdleStrategy();
    private final PendingIdler idler = new PendingIdler(pendingQueue, pendingData);

    private void assertCounts(final int queueIdles, final int queueResets, final int dataIdles, final int dataResets) {
        assertEquals(queueIdles, pendingQueue.idleCount, "pending queue idle count");
        assertEquals(queueResets, pendingQueue.resetCount, "pending queue reset count");
        assertEquals(dataIdles, pendingData.idleCount, "pending data idle count");
        assertEquals(dataResets, pendingData.resetCount, "pending data reset count");
    }

    @Test
    void idlesWhileQueueIsPendingThenUntilFirstEntryIsRead() {
        idler.onQueuePending();
        idler.onQueuePending();
        assertCounts(2, 0, 0, 0);

        idler.onNoEntry(0);
        idler.onNoEntry(0);
        assertCounts(2, 1, 2, 0);

        idler.onEntry();
        assertCounts(2, 1, 2, 1);

        idler.onNoEntry(0);
        idler.onNoEntry(1);
        idler.onEntry();
        assertCounts(2, 1, 2, 1);
    }

    @Test
    void neverIdlesForEntriesOtherThanTheFirst() {
        idler.onNoEntry(1);
        idler.onNoEntry(42);
        assertCounts(0, 0, 0, 0);
    }

    @Test
    void entryReadWhilePendingQueueResetsPendingQueueStrategy() {
        idler.onQueuePending();
        idler.onEntry();
        idler.onNoEntry(0);
        assertCounts(1, 1, 0, 0);
    }

    @Test
    void noFirstEntryWhileQueueIsPendingResetsPendingQueueStrategy() {
        idler.onNoEntry(0);
        assertCounts(0, 1, 1, 0);

        idler.onEntry();
        idler.onNoEntry(0);
        assertCounts(0, 1, 1, 1);
    }
}
