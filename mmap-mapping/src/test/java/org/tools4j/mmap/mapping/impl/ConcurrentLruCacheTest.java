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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.impl.ConcurrentLruCache.Borrower;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link ConcurrentLruCache}: single-threaded acquire/release, borrower lifecycle, eviction and removal
 * semantics, and concurrent tests with one borrower per thread and no more threads than capacity, checking that acquire
 * always succeeds, a single value is created per index and values are never closed while held.
 */
class ConcurrentLruCacheTest {

    private static final long MAX_WAIT_MILLIS = 10_000;
    /** Shared by all workers of a concurrent test, generous for slow CI machines with fewer cores than threads */
    private static final long WORKERS_MAX_WAIT_MILLIS = 30_000;
    /** Rounds of barrier tests stop early after this, as each round parks and wakes all threads (slow on CI) */
    private static final long ROUNDS_MAX_MILLIS = 5_000;
    /** Wait for something expected not to happen */
    private static final long SHORT_WAIT_MILLIS = 100;

    /** Cached test value recording how often it was closed and whether it is currently in use */
    static final class Value implements AutoCloseable {
        final int index;
        final AtomicInteger closeCount = new AtomicInteger();
        final AtomicInteger users = new AtomicInteger();
        final AtomicInteger closedWhileInUse = new AtomicInteger();
        AtomicInteger open;

        Value(final int index) {
            this.index = index;
        }

        boolean isClosed() {
            return closeCount.get() > 0;
        }

        @Override
        public void close() {
            //NOTE: exceptions are swallowed by the cache, so we record misuse instead of throwing
            if (users.get() != 0) {
                closedWhileInUse.incrementAndGet();
            }
            closeCount.incrementAndGet();
            if (open != null) {
                open.decrementAndGet();
            }
        }

        @Override
        public String toString() {
            return "Value:index=" + index + "|closeCount=" + closeCount + "|users=" + users +
                    "|closedWhileInUse=" + closedWhileInUse;
        }
    }

    /**
     * Factory creating {@link Value}s, keeping track of all of them, and counting values created while another value
     * for the same index was still open
     */
    static final class Factory implements IntFunction<Value> {
        final Queue<Value> created = new ConcurrentLinkedQueue<>();
        final Map<Integer, AtomicInteger> open = new ConcurrentHashMap<>();
        final AtomicInteger concurrentDuplicates = new AtomicInteger();

        @Override
        public Value apply(final int index) {
            if (open.computeIfAbsent(index, k -> new AtomicInteger()).getAndIncrement() > 0) {
                concurrentDuplicates.incrementAndGet();
            }
            final Value value = new Value(index);
            value.open = open.get(index);
            created.add(value);
            return value;
        }

        int count(final int index) {
            return (int) created.stream().filter(v -> v.index == index).count();
        }
    }

    private final Factory factory = new Factory();
    /** Set when workers time out, so that they stop instead of running into subsequent tests */
    private volatile boolean stopped;
    /** Snapshot of stopped taken by the barrier action, so that all workers of a round agree on stopping */
    private volatile boolean stopRound;

    private static Value use(final Borrower<Value> borrower, final int index, final IntFunction<Value> factory) {
        final Value value = borrower.acquire(index, factory);
        assertNotNull(value);
        borrower.release();
        return value;
    }

    @Test
    void invalidCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentLruCache<Value>(-1));
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentLruCache<Value>(0));
    }

    @Test
    void acquireCreatesOnceAndTryAcquireFindsCachedValue() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final Borrower<Value> borrower = cache.newBorrower();
        assertNull(borrower.tryAcquire(1));
        final Value value = borrower.acquire(1, factory);
        assertEquals(1, value.index);
        borrower.release();
        assertSame(value, borrower.tryAcquire(1));
        borrower.release();
        assertSame(value, use(borrower, 1, factory));
        assertEquals(1, factory.count(1));
        assertEquals(1, cache.size());
        assertTrue(cache.toString().startsWith("ConcurrentLruCache:capacity=4|size=1"), cache.toString());
    }

    @Test
    void releaseWithoutAcquireThrows() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final Borrower<Value> borrower = cache.newBorrower();
        assertThrows(IllegalStateException.class, borrower::release);
        use(borrower, 1, factory);
        assertThrows(IllegalStateException.class, borrower::release);
    }

    @Test
    void nestedAcquireFailsAssertion() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final Borrower<Value> borrower = cache.newBorrower();
        borrower.acquire(1, factory);
        assertThrows(AssertionError.class, () -> borrower.acquire(2, factory));
        assertThrows(AssertionError.class, () -> borrower.tryAcquire(1));
        borrower.release();
        use(borrower, 2, factory);
    }

    @Test
    void atMostCapacityBorrowersAndClosingFreesOne() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(2);
        final Borrower<Value> borrower0 = cache.newBorrower();
        final Borrower<Value> borrower1 = cache.newBorrower();
        assertThrows(IllegalStateException.class, cache::newBorrower);
        final Value value0 = borrower0.acquire(0, factory);
        assertThrows(IllegalStateException.class, borrower0::close, "close while holding");
        borrower0.release();
        assertFalse(borrower0.isClosed());
        borrower0.close();
        assertTrue(borrower0.isClosed());
        borrower0.close();//idempotent
        assertThrows(AssertionError.class, () -> borrower0.acquire(0, factory));
        final Borrower<Value> borrower2 = cache.newBorrower();
        assertSame(value0, use(borrower2, 0, factory));
        use(borrower1, 1, factory);
    }

    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {1, 2, 3, 4, 8, 65})
    void evictsLeastRecentlyUsedAndClosesIt(final int capacity) {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(capacity);
        final Borrower<Value> borrower = cache.newBorrower();
        final List<Value> values = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            values.add(use(borrower, index, factory));
        }
        //touch index 0 so that index 1 (if any) becomes least recently used
        use(borrower, 0, factory);
        use(borrower, capacity, factory);
        final int evicted = capacity == 1 ? 0 : 1;
        assertTrue(values.get(evicted).isClosed(), values.toString());
        assertNull(borrower.tryAcquire(evicted));
        for (int index = 0; index < capacity; index++) {
            if (index != evicted) {
                assertFalse(values.get(index).isClosed(), values.get(index).toString());
            }
        }
        assertEquals(capacity, cache.size());
    }

    @Test
    void heldValueIsNeverEvictedEvenIfLeastRecentlyUsed() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(2);
        final Borrower<Value> holder = cache.newBorrower();
        final Borrower<Value> user = cache.newBorrower();
        final Value held = holder.acquire(0, factory);
        Value previous = null;
        for (int index = 1; index <= 10; index++) {
            final Value value = use(user, index, factory);
            if (previous != null) {
                assertTrue(previous.isClosed(), previous.toString());
            }
            previous = value;
        }
        assertFalse(held.isClosed());
        holder.release();
        assertSame(held, use(holder, 0, factory));
        assertEquals(1, factory.count(0));
    }

    @Test
    void staleHintFallsBackToScan() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(2);
        final Borrower<Value> borrower = cache.newBorrower();
        final Borrower<Value> other = cache.newBorrower();
        final Value first = use(borrower, 1, factory);
        use(other, 2, factory);
        use(other, 3, factory);//evicts 1, the slot borrower's hint points to
        assertTrue(first.isClosed());
        assertNull(borrower.tryAcquire(1));
        final Value second = use(borrower, 1, factory);
        assertNotSame(first, second);
        assertSame(second, use(borrower, 1, factory));
    }

    @Test
    void removeClosesUnheldValueButSkipsHeldOne() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final Borrower<Value> borrower = cache.newBorrower();
        final Borrower<Value> holder = cache.newBorrower();
        final Value unheld = use(borrower, 1, factory);
        final Value held = holder.acquire(2, factory);
        assertTrue(cache.remove(1));
        assertTrue(unheld.isClosed());
        assertFalse(cache.remove(2));
        assertFalse(cache.removeAll());
        assertFalse(held.isClosed());
        assertSame(held, borrower.tryAcquire(2));
        borrower.release();
        holder.release();
        assertTrue(cache.remove(2));
        assertTrue(held.isClosed());
        assertFalse(cache.remove(3));
        assertEquals(0, cache.size());
    }

    @Test
    void removeAllClosesAllValues() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final Borrower<Value> borrower = cache.newBorrower();
        for (int index = 0; index < 4; index++) {
            use(borrower, index, factory);
        }
        assertTrue(cache.removeAll());
        assertEquals(0, cache.size());
        factory.created.forEach(value -> assertEquals(1, value.closeCount.get(), value.toString()));
    }

    @Test
    void createIfAbsentCreatesUnheldEntryOnlyOnce() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(2);
        final Borrower<Value> borrower = cache.newBorrower();
        borrower.createIfAbsent(1, factory);
        borrower.createIfAbsent(1, factory);
        assertEquals(1, factory.count(1));
        final Value value = use(borrower, 1, factory);
        assertEquals(1, factory.count(1));
        //unheld, hence evictable
        use(borrower, 2, factory);
        use(borrower, 3, factory);
        assertTrue(value.isClosed());
    }

    @Test
    void factoryExceptionDoesNotLeakClaimedSlot() {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(1);
        final Borrower<Value> borrower = cache.newBorrower();
        final IllegalStateException failure = new IllegalStateException("factory failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> borrower.acquire(1, index -> {
            throw failure;
        })));
        assertEquals(0, cache.size());
        assertNull(borrower.tryAcquire(1));
        use(borrower, 1, factory);
        use(borrower, 2, factory);
        assertEquals(1, cache.size());
    }

    @Test
    void concurrentAcquireOfSameNewIndexWaitsForPendingCreation() throws Exception {
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(4);
        final CountDownLatch firstInFactory = new CountDownLatch(1);
        final CountDownLatch releaseFactory = new CountDownLatch(1);
        final CountDownLatch secondDone = new CountDownLatch(1);
        final AtomicReference<Value> firstValue = new AtomicReference<>();
        final AtomicReference<Value> secondValue = new AtomicReference<>();
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        final Thread first = new Thread(() -> {
            try (Borrower<Value> borrower = cache.newBorrower()) {
                firstValue.set(borrower.acquire(1, index -> {
                    firstInFactory.countDown();
                    await(releaseFactory);
                    return factory.apply(index);
                }));
                borrower.release();
            } catch (final Throwable t) {
                failures.add(t);
            }
        });
        final Thread second = new Thread(() -> {
            try (Borrower<Value> borrower = cache.newBorrower()) {
                secondValue.set(use(borrower, 1, factory));
            } catch (final Throwable t) {
                failures.add(t);
            } finally {
                secondDone.countDown();
            }
        });
        first.start();
        try {
            assertTrue(firstInFactory.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
            second.start();
            assertFalse(secondDone.await(SHORT_WAIT_MILLIS, TimeUnit.MILLISECONDS),
                    "second acquire waits while first is creating the value");
        } finally {
            releaseFactory.countDown();
        }
        assertTrue(secondDone.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
        first.join(MAX_WAIT_MILLIS);
        second.join(MAX_WAIT_MILLIS);
        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(1, factory.count(1), "values created for index 1: " + factory.created);
        assertSame(firstValue.get(), secondValue.get());
        assertEquals(1, cache.size());
    }

    /**
     * Each round, every thread acquires the same index that was never used before, all threads released at the same
     * time. A value must never be created while another one for the same index is still open.
     * <p>
     * NOTE: a value can still be created more than once per index: a claimer may find the newly published (and already
     * released) value to be the only unheld entry and evict it again within the same round.
     */
    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {2, 3, 4, 8})
    void concurrentAcquireOfSameNewIndexCreatesSingleValue(final int capacity) throws Exception {
        final int rounds = 500;
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(capacity);
        final CyclicBarrier barrier = new CyclicBarrier(capacity, () -> stopRound = stopped);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runWorkers(capacity, failures, "", (worker, borrower) -> {
            for (int round = 0; round < rounds; round++) {
                try {
                    barrier.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                    if (stopRound) {
                        return;
                    }
                    final Value value = borrower.acquire(round, factory);
                    try {
                        assertEquals(round, value.index);
                        assertFalse(value.isClosed(), value.toString());
                    } finally {
                        borrower.release();
                    }
                } catch (final Throwable e) {
                    failures.add(new AssertionError("round " + round, e));
                    barrier.reset();
                    return;
                }
            }
        }, cache);
        assertEquals(0, factory.concurrentDuplicates.get(), "values created while another was open for same index");
        for (int round = 0; round < rounds; round++) {
            assertTrue(factory.count(round) >= 1, "value created for index " + round);
        }
        assertTrue(cache.removeAll());
        assertEquals(0, cache.size());
    }

    /**
     * Each round, every thread performs exactly one acquire/use/release sequence, all threads released at the same
     * time. With no more threads than capacity, acquire must always succeed.
     */
    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {2, 3, 4, 8})
    void concurrentSingleSequencePerThreadAlwaysAcquiresAndNeverClosesHeldValues(final int capacity)
            throws Exception {
        final int indices = capacity + 1;//forces eviction on almost every create
        final int rounds = 100_000;
        final long seed = System.nanoTime();
        final long roundsDeadline = seed + TimeUnit.MILLISECONDS.toNanos(ROUNDS_MAX_MILLIS);
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(capacity);
        final CyclicBarrier barrier = new CyclicBarrier(capacity,
                () -> stopRound = stopped || System.nanoTime() - roundsDeadline > 0);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runWorkers(capacity, failures, ", seed=" + seed, (worker, borrower) -> {
            final Random random = new Random(seed + worker);
            for (int round = 0; round < rounds; round++) {
                try {
                    barrier.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (final Exception e) {
                    failures.add(e);
                    return;
                }
                if (stopRound) {
                    return;
                }
                if (!acquireUseRelease(borrower, random.nextInt(indices), random.nextInt(4) == 0, failures,
                        round)) {
                    barrier.reset();
                    return;
                }
            }
        }, cache);
        assertQuiescentAndNeverClosedWhileHeld(cache, capacity, seed);
    }

    /**
     * Like {@link #concurrentSingleSequencePerThreadAlwaysAcquiresAndNeverClosesHeldValues(int)} but free-running
     * without a barrier, so that hits, evictions and creations overlap at arbitrary points.
     */
    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {2, 3, 4, 8})
    void concurrentFreeRunningAlwaysAcquiresAndNeverClosesHeldValues(final int capacity) throws Exception {
        final int indices = capacity + 1;
        final int iterations = 200_000;
        final long seed = System.nanoTime();
        final ConcurrentLruCache<Value> cache = new ConcurrentLruCache<>(capacity);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        runWorkers(capacity, failures, ", seed=" + seed, (worker, borrower) -> {
            final Random random = new Random(seed + worker);
            for (int i = 0; i < iterations && !stopped; i++) {
                if (!acquireUseRelease(borrower, random.nextInt(indices), random.nextInt(4) == 0, failures,
                        i)) {
                    return;
                }
            }
        }, cache);
        assertQuiescentAndNeverClosedWhileHeld(cache, capacity, seed);
    }

    private boolean acquireUseRelease(final Borrower<Value> borrower,
                                      final int index,
                                      final boolean tryOnly,
                                      final Queue<Throwable> failures,
                                      final int iteration) {
        try {
            final Value value = tryOnly ? borrower.tryAcquire(index) : borrower.acquire(index, factory);
            if (value == null) {
                return true;
            }
            value.users.incrementAndGet();
            try {
                assertEquals(index, value.index);
                assertFalse(value.isClosed(), value.toString());
            } finally {
                value.users.decrementAndGet();
                borrower.release();
            }
            return true;
        } catch (final Throwable e) {
            failures.add(new AssertionError("iteration " + iteration, e));
            return false;
        }
    }

    private void assertQuiescentAndNeverClosedWhileHeld(final ConcurrentLruCache<Value> cache,
                                                        final int capacity,
                                                        final long seed) {
        final long open = factory.created.stream().filter(value -> !value.isClosed()).count();
        assertEquals(open, cache.size(), "size matches open values when quiescent, seed=" + seed);
        assertTrue(cache.size() <= capacity, cache.toString());
        assertEquals(0, factory.concurrentDuplicates.get(), "values created while another was open, seed=" + seed);
        assertTrue(cache.removeAll());
        assertEquals(0, cache.size());
        for (final Value value : factory.created) {
            assertEquals(1, value.closeCount.get(), "closed exactly once: " + value + ", seed=" + seed);
            assertEquals(0, value.closedWhileInUse.get(), "never closed while in use: " + value + ", seed=" + seed);
        }
    }

    @FunctionalInterface
    private interface Worker {
        void run(int worker, Borrower<Value> borrower);
    }

    private void runWorkers(final int threads,
                            final Queue<Throwable> failures,
                            final String context,
                            final Worker worker,
                            final ConcurrentLruCache<Value> cache) throws InterruptedException {
        final Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int id = t;
            workers[t] = new Thread(() -> {
                try (Borrower<Value> borrower = cache.newBorrower()) {
                    worker.run(id, borrower);
                } catch (final Throwable e) {
                    failures.add(e);
                }
            }, "worker-" + t);
            workers[t].start();
        }
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WORKERS_MAX_WAIT_MILLIS);
        for (final Thread thread : workers) {
            thread.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
            if (thread.isAlive()) {
                stopped = true;
                final AssertionError error = new AssertionError(thread.getName() + " still running after " +
                        WORKERS_MAX_WAIT_MILLIS + "ms with " + threads + " threads" + context);
                error.setStackTrace(thread.getStackTrace());
                throw error;
            }
        }
        if (!failures.isEmpty()) {
            final AssertionError error = new AssertionError(failures.size() + " failures with " + threads +
                    " threads" + context, failures.peek());
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timed out waiting for latch");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
