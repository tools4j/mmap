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
package org.tools4j.mmap.mapping.perf;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.impl.ConcurrentLruCache;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Throughput of {@link ConcurrentLruCache} for acquire/release pairs, run via
 * {@code ./gradlew :mmap-mapping:perfTest --tests "org.tools4j.mmap.mapping.perf.LruCachePerf"}.
 * <p>
 * Scenarios, each with 1..CAPACITY threads:
 * <ul>
 *     <li>hot-key: all threads acquire the same cached key</li>
 *     <li>own-key: every thread acquires its own cached key</li>
 *     <li>misses: random keys from 2 * CAPACITY, roughly every other acquire misses and evicts</li>
 * </ul>
 * Hits write no shared cache line, so throughput per thread should stay roughly constant with more threads in the
 * hot-key and own-key scenarios. Values are cached Integers, so the factory never allocates and close is a no-op.
 */
@Tag("perf")
class LruCachePerf {

    private static final int CAPACITY = 8;
    private static final int[] THREADS = {1, 2, 4, 8};
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURE_ROUNDS = 5;
    private static final long ROUND_MILLIS = 300;
    private static final IntFunction<Integer> FACTORY = Integer::valueOf;

    private enum Scenario {
        HOT_KEY, OWN_KEY, MISSES;

        int keyRange() {
            return this == MISSES ? 2 * CAPACITY : CAPACITY;
        }

        int key(final int thread, final long random) {
            switch (this) {
                case HOT_KEY: return 0;
                case OWN_KEY: return thread;
                default: return (int) ((random >>> 33) % keyRange());
            }
        }
    }

    private volatile boolean running;
    private volatile long sink;

    @Test
    void measureThroughput() throws Exception {
        System.out.printf(Locale.ROOT, "%n%-8s %7s %14s %16s%n", "scenario", "threads", "total Mops/s",
                "per-thread Mops/s");
        for (final Scenario scenario : Scenario.values()) {
            for (final int threads : THREADS) {
                final double total = measure(scenario, threads);
                System.out.printf(Locale.ROOT, "%-8s %7d %14.1f %16.1f%n", scenario, threads, total,
                        total / threads);
            }
        }
        System.out.println();
    }

    /** @return median total throughput over measure rounds in million acquire/release pairs per second */
    private double measure(final Scenario scenario, final int threads) throws Exception {
        final ConcurrentLruCache<Integer> cache = new ConcurrentLruCache<>(CAPACITY);
        final double[] results = new double[MEASURE_ROUNDS];
        for (int round = 0; round < WARMUP_ROUNDS + MEASURE_ROUNDS; round++) {
            final long[] ops = new long[threads];
            final CyclicBarrier start = new CyclicBarrier(threads + 1);
            final Thread[] workers = new Thread[threads];
            for (int t = 0; t < threads; t++) {
                final int id = t;
                workers[t] = new Thread(() -> {
                    try {
                        ops[id] = run(cache, scenario, id, start);
                    } catch (final Exception e) {
                        throw new IllegalStateException(e);
                    }
                }, "perf-" + t);
                workers[t].start();
            }
            running = true;
            start.await(10, TimeUnit.SECONDS);
            final long t0 = System.nanoTime();
            Thread.sleep(ROUND_MILLIS);
            running = false;
            for (final Thread worker : workers) {
                worker.join(10_000);
            }
            final long nanos = System.nanoTime() - t0;
            if (round >= WARMUP_ROUNDS) {
                results[round - WARMUP_ROUNDS] = Arrays.stream(ops).sum() * 1e3 / nanos;
            }
        }
        Arrays.sort(results);
        return results[MEASURE_ROUNDS / 2];
    }

    private long run(final ConcurrentLruCache<Integer> cache, final Scenario scenario, final int thread,
                     final CyclicBarrier start) throws Exception {
        long random = 0x9E3779B97F4A7C15L * (thread + 1);
        long ops = 0;
        long sum = 0;
        try (ConcurrentLruCache.Borrower<Integer> borrower = cache.newBorrower()) {
            start.await(10, TimeUnit.SECONDS);
            while (running) {
                random = xorShift(random);
                final int key = scenario.key(thread, random);
                sum += borrower.acquire(key, FACTORY);
                borrower.release();
                ops++;
            }
        }
        sink += sum;
        return ops;
    }

    private static long xorShift(long x) {
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        return x;
    }
}
