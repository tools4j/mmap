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
package org.tools4j.mmap.region.impl;

import org.agrona.LangUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link IdPool}, {@link IdPool64} and {@link IdPool256}
 */
class IdPoolTest {

    private static final long TIMEOUT_MILLIS = 3_000;

    enum PoolFactory {
        IdPool64(64, IdPool64::new),
        IdPool256(256, IdPool256::new);
        final int maxIds;
        private final Function<File, IdPool> factoryMethod;

        PoolFactory(final int maxIds, final Function<File, IdPool> factoryMethod) {
            this.maxIds = maxIds;
            this.factoryMethod = requireNonNull(factoryMethod);
        }

        IdPool createPool(final File idIdFile) {
            return factoryMethod.apply(idIdFile);
        }
    }

    @ParameterizedTest(name = "poolFactory={0}")
    @EnumSource(PoolFactory.class)
    void acquireAndReleaseAll(final PoolFactory poolFactory) throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final IdPool pool = poolFactory.createPool(new File(tmpDir.toFile(), "test.ids"));
        final int min = 0;
        final int cnt = poolFactory.maxIds;

        //when + then: acquired
        assertEquals(0, pool.acquired());

        //when + then: acquire
        for (int i = 0; i < cnt; i++) {
            assertEquals(i + min, pool.acquire());
        }
        assertThrows(IllegalStateException.class, pool::acquire);

        //when + then: acquired
        assertEquals(cnt, pool.acquired());

        for (int i = 0; i < cnt; i++) {
            //when
            pool.release(i + min);

            //then
            assertEquals(cnt - i - 1, pool.acquired());
        }

        //when + then: acquired
        assertEquals(0, pool.acquired());

        //when + then: illegal releases
        assertThrows(IllegalArgumentException.class, () -> pool.release(-1));
        assertThrows(IllegalArgumentException.class, () -> pool.release(poolFactory.maxIds));
        assertThrows(IllegalArgumentException.class, () -> pool.release(min - 1));

        //when
        pool.close();

        //then
        assertThrows(IllegalStateException.class, pool::acquire);
        assertThrows(IllegalStateException.class, () -> pool.release(min));
        assertEquals(0, pool.acquired());
    }

    @ParameterizedTest(name = "poolFactory={0}")
    @EnumSource(PoolFactory.class)
    void concurrentlyAcquireAndReleaseSome(final PoolFactory poolFactory) throws Exception {
        final int threads = 10;
        final int repeat = 5;
        final long maxWaitMillis = 100;
        runConcurrentTest(poolFactory, threads, repeat, maxWaitMillis);
    }

    @ParameterizedTest(name = "poolFactory={0}")
    @EnumSource(PoolFactory.class)
    void concurrentlyAcquireAndReleaseMany(final PoolFactory poolFactory) throws Exception {
        final int threads = poolFactory.maxIds;
        final int repeat = 3;
        final long maxWaitMillis = 20;
        runConcurrentTest(poolFactory, threads, repeat, maxWaitMillis);
    }

    private void runConcurrentTest(final PoolFactory poolFactory,
                                   final int threadCount,
                                   final int repeat,
                                   final long maxWaitMillis) throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final IdPool pool = poolFactory.createPool(new File(tmpDir.toFile(), "test.ids"));
        final BitSet acquired = new BitSet();
        final BitSet released = new BitSet();
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final Thread[] threads = initThreads(threadCount, repeat, maxWaitMillis, pool, acquired, released, latch);

        //when
        for (final Thread thread : threads) {
            thread.start();
        }
        for (final Thread thread : threads) {
            thread.join(10 * repeat * maxWaitMillis);
        }

        //then
        assertEquals(threadCount, acquired.cardinality());
        assertEquals(threadCount, released.cardinality());
        assertEquals(threadCount, acquired.length());
        assertEquals(threadCount, released.length());
        
        //when
        final int id = pool.acquire();
        
        //then
        assertEquals(0, id);
    }

    private Thread[] initThreads(final int count,
                                 final int repeat,
                                 final long maxWaitMillis,
                                 final IdPool pool,
                                 final BitSet acquired,
                                 final BitSet released,
                                 final CountDownLatch latch) {
        requireNonNull(pool);
        requireNonNull(acquired);
        requireNonNull(released);
        requireNonNull(latch);
        final Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            final String name = "id-" + i;
            threads[i] = new Thread(null, () -> {
                for (int j = 0; j < repeat; j++) {
                    sleepRandom(maxWaitMillis);
                    final int id = pool.acquire();
                    if (j == 0) {
                        sync(() -> acquired.set(id));
                        latch.countDown();
                        await(latch);
                    }
                    sleepRandom(maxWaitMillis);
                    pool.release(id);
                    if (j == 0) {
                        sync(() -> released.set(id));
                    }
                }
            }, name);
        }
        return threads;
    }

    private static void sleepRandom(final long maxMillis) {
        sleep((long)(maxMillis * Math.random()));
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            //ignore
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (final Exception e) {
            LangUtil.rethrowUnchecked(e);
        }
    }

    private synchronized void sync(final Runnable runnable) {
        runnable.run();
    }
}