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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link AtomicLruCache}: single-threaded acquire/release, eviction and removal semantics, and a
 * round-based concurrent test with no more threads than capacity, checking that acquire always succeeds and values are
 * never closed while pinned.
 */
class AtomicLruCacheTest {

    private static final long MAX_WAIT_MILLIS = 10_000;

    /** Cached test value recording how often it was closed and whether it is currently in use */
    static final class Value implements AutoCloseable {
        final int index;
        final AtomicInteger closeCount = new AtomicInteger();
        final AtomicInteger users = new AtomicInteger();
        final AtomicInteger closedWhileInUse = new AtomicInteger();

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
        }

        @Override
        public String toString() {
            return "Value:index=" + index + "|closeCount=" + closeCount + "|users=" + users +
                    "|closedWhileInUse=" + closedWhileInUse;
        }
    }

    /** Factory creating {@link Value}s and keeping track of all of them */
    static final class Factory implements IntFunction<Value> {
        final Queue<Value> created = new ConcurrentLinkedQueue<>();

        @Override
        public Value apply(final int index) {
            final Value value = new Value(index);
            created.add(value);
            return value;
        }

        int count(final int index) {
            return (int) created.stream().filter(v -> v.index == index).count();
        }
    }

    private final Factory factory = new Factory();

    private static Value use(final AtomicLruCache<Value> cache, final int index, final IntFunction<Value> factory) {
        final Value value = cache.acquire(index, factory);
        assertNotNull(value);
        cache.release(index);
        return value;
    }

    @Test
    void invalidSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AtomicLruCache<Value>(-1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicLruCache<Value>(4, 2));
        assertThrows(IllegalArgumentException.class, () -> new AtomicLruCache<Value>(4, 6));
    }

    @Test
    void acquireCreatesOnceAndTryAcquireFindsCachedValue() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        assertNull(cache.tryAcquire(1));
        final Value value = cache.acquire(1, factory);
        assertEquals(1, value.index);
        assertSame(value, cache.tryAcquire(1));
        cache.release(1);
        cache.release(1);
        assertSame(value, cache.acquire(1, factory));
        cache.release(1);
        assertEquals(1, factory.count(1));
        assertEquals(1, cache.size());
        assertTrue(cache.toString().startsWith("AtomicLruCache:capacity=4|cacheSize=4|size=1"), cache.toString());
    }

    @Test
    void releaseWithoutAcquireThrows() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        assertThrows(IllegalStateException.class, () -> cache.release(1));
        use(cache, 1, factory);
        assertThrows(IllegalStateException.class, () -> cache.release(2));
    }

    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {1, 2, 3, 4, 8})
    void evictsLeastRecentlyUsedAndClosesIt(final int capacity) {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(capacity);
        final List<Value> values = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            values.add(use(cache, index, factory));
        }
        //touch index 0 so that index 1 (if any) becomes least recently used
        use(cache, 0, factory);
        use(cache, capacity, factory);
        final int evicted = capacity == 1 ? 0 : 1;
        assertTrue(values.get(evicted).isClosed(), values.toString());
        assertNull(cache.tryAcquire(evicted));
        for (int index = 0; index < capacity; index++) {
            if (index != evicted) {
                assertFalse(values.get(index).isClosed(), values.get(index).toString());
            }
        }
        assertEquals(capacity, cache.size());
    }

    @Test
    void pinnedValueIsNotEvictedWhileUnpinnedOneIs() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(2);
        final Value pinned = cache.acquire(0, factory);
        final Value unpinned = use(cache, 1, factory);
        use(cache, 2, factory);
        assertTrue(unpinned.isClosed());
        assertFalse(pinned.isClosed());
        assertSame(pinned, cache.tryAcquire(0));
        cache.release(0);
        cache.release(0);
    }

    @Test
    void acquireFailsIfAllEntriesArePinnedAndPinnedValuesAreClosedOnlyOnRelease() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(2);
        final Value value0 = cache.acquire(0, factory);
        final Value value1 = cache.acquire(1, factory);
        assertThrows(IllegalStateException.class, () -> cache.acquire(2, factory));
        assertFalse(value0.isClosed());
        assertFalse(value1.isClosed());
        cache.release(0);
        cache.release(1);
        //NOTE: the failed acquire attempted to evict both pinned entries, they are closed once released
        assertTrue(value0.isClosed());
        assertTrue(value1.isClosed());
        assertEquals(0, cache.size());
        use(cache, 2, factory);
    }

    @Test
    void indicesSharingHintSlotCanBeAcquiredInterleaved() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        final int other = 1 + 4;//same home slot and thread-local hint as index 1
        final Value value1 = cache.acquire(1, factory);
        final Value valueOther = cache.acquire(other, factory);
        cache.release(1);
        cache.release(other);
        assertSame(value1, cache.tryAcquire(1));
        assertSame(valueOther, cache.tryAcquire(other));
        cache.release(other);
        cache.release(1);
        assertEquals(1, factory.count(1));
        assertEquals(1, factory.count(other));
    }

    @Test
    void removeClosesUnpinnedImmediatelyAndPinnedOnRelease() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        final Value unpinned = use(cache, 1, factory);
        final Value pinned = cache.acquire(2, factory);
        assertTrue(cache.remove(1));
        assertTrue(unpinned.isClosed());
        assertTrue(cache.remove(2));
        assertFalse(pinned.isClosed());
        assertNull(cache.tryAcquire(2));
        cache.release(2);
        assertTrue(pinned.isClosed());
        assertFalse(cache.remove(3));
        assertEquals(0, cache.size());
    }

    @Test
    void removeAllClosesAllValues() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        for (int index = 0; index < 4; index++) {
            use(cache, index, factory);
        }
        assertTrue(cache.removeAll());
        assertEquals(0, cache.size());
        factory.created.forEach(value -> assertEquals(1, value.closeCount.get(), value.toString()));
    }

    @Test
    void createIfAbsentCreatesUnpinnedEntryOnlyOnce() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(2);
        cache.createIfAbsent(1, factory);
        cache.createIfAbsent(1, factory);
        assertEquals(1, factory.count(1));
        final Value value = cache.acquire(1, factory);
        assertEquals(1, factory.count(1));
        cache.release(1);
        //unpinned, hence evictable
        use(cache, 2, factory);
        use(cache, 3, factory);
        assertTrue(value.isClosed());
    }

    @Test
    void zeroCapacityCacheCannotHoldValues() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(0);
        assertThrows(IllegalStateException.class, () -> cache.acquire(1, factory));
        assertEquals(0, cache.size());
    }

    @Test
    @Disabled("known issue: createAndAcquire has no duplicate-claim check, see class javadoc")
    void concurrentAcquireOfSameNewIndexPublishesSingleEntry() throws Exception {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(4);
        final CountDownLatch firstInFactory = new CountDownLatch(1);
        final CountDownLatch secondDone = new CountDownLatch(1);
        final IntFunction<Value> blockingFactory = index -> {
            firstInFactory.countDown();
            await(secondDone);
            return factory.apply(index);
        };
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread first = new Thread(() -> {
            try {
                cache.acquire(1, blockingFactory);
                cache.release(1);
            } catch (final Throwable t) {
                failure.set(t);
            }
        });
        first.start();
        try {
            assertTrue(firstInFactory.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
            //first thread has claimed a slot for index 1 and is creating its value
            use(cache, 1, factory);
        } finally {
            secondDone.countDown();
            first.join(MAX_WAIT_MILLIS);
        }
        assertFalse(first.isAlive());
        assertNull(failure.get());
        assertEquals(1, factory.count(1), "values created for index 1: " + factory.created);
        assertEquals(1, cache.size());
    }

    @Test
    void factoryExceptionDoesNotLeakClaimedSlot() {
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(1);
        final IllegalStateException failure = new IllegalStateException("factory failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> cache.acquire(1, index -> {
            throw failure;
        })));
        assertEquals(0, cache.size());
        assertNull(cache.tryAcquire(1));
        use(cache, 1, factory);
        use(cache, 2, factory);
        assertEquals(1, cache.size());
    }

    /**
     * Each round, every thread performs exactly one acquire/use/release sequence, all threads released at the same
     * time. With no more threads than capacity, acquire must always succeed.
     */
    @ParameterizedTest(name = "capacity={0}")
    @ValueSource(ints = {2, 3, 4, 8})
    void concurrentSingleSequencePerThreadAlwaysAcquiresAndNeverClosesPinnedValues(final int capacity)
            throws Exception {
        final int threads = capacity;
        final int indices = capacity + 1;//forces eviction on almost every create
        final int rounds = 100_000;
        final long seed = System.nanoTime();
        final AtomicLruCache<Value> cache = new AtomicLruCache<>(capacity);
        final CyclicBarrier barrier = new CyclicBarrier(threads);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        final Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final Random random = new Random(seed + t);
            workers[t] = new Thread(() -> {
                for (int round = 0; round < rounds; round++) {
                    try {
                        barrier.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (final Exception e) {
                        failures.add(e);
                        return;
                    }
                    try {
                        final int index = random.nextInt(indices);
                        final Value value = random.nextInt(4) == 0
                                ? cache.tryAcquire(index)
                                : cache.acquire(index, factory);
                        if (value == null) {
                            continue;
                        }
                        value.users.incrementAndGet();
                        try {
                            assertEquals(index, value.index);
                            assertFalse(value.isClosed(), value.toString());
                        } finally {
                            value.users.decrementAndGet();
                            cache.release(index);
                        }
                    } catch (final Throwable e) {
                        failures.add(new AssertionError("round " + round, e));
                        barrier.reset();
                        return;
                    }
                }
            }, "worker-" + t);
            workers[t].start();
        }
        for (final Thread worker : workers) {
            worker.join(MAX_WAIT_MILLIS);
            assertFalse(worker.isAlive(), worker.getName() + " still running after " + MAX_WAIT_MILLIS + "ms");
        }
        if (!failures.isEmpty()) {
            final AssertionError error = new AssertionError(failures.size() + " failures with " + threads +
                    " threads, seed=" + seed, failures.peek());
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
        final long open = factory.created.stream().filter(value -> !value.isClosed()).count();
        assertEquals(open, cache.size(), "size matches open values when quiescent, seed=" + seed);
        assertTrue(cache.size() <= capacity, cache.toString());
        assertTrue(cache.removeAll());
        assertEquals(0, cache.size());
        for (final Value value : factory.created) {
            assertEquals(1, value.closeCount.get(), "closed exactly once: " + value + ", seed=" + seed);
            assertEquals(0, value.closedWhileInUse.get(), "never closed while in use: " + value + ", seed=" + seed);
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            if (!latch.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Timeout waiting for latch");
            }
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
