/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link AppenderIdPool} and {@link MultiAppenderIdPool}
 */
class AppenderIdPoolTest {

    private static final long TIMEOUT_MILLIS = 3_000;

    @ParameterizedTest(name = "allowZero={0}")
    @ValueSource(booleans = {true, false})
    void acquireAndReleaseAll(final boolean allowZero) throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final MultiAppenderIdPool pool = new MultiAppenderIdPool(new File(tmpDir.toFile(), "appender-ids"), allowZero);
        final int min = allowZero ? 0 : 1;
        final int cnt = allowZero ? MultiAppenderIdPool.MAX_APPENDERS : MultiAppenderIdPool.MAX_APPENDERS - 1;

        //when + then: open appenders
        assertEquals(0, pool.openAppenders());

        //when + then: acquire
        for (int i = 0; i < cnt; i++) {
            assertEquals(i + min, pool.acquire());
        }
        assertThrows(IllegalStateException.class, pool::acquire);

        //when + then: open appenders
        assertEquals(cnt, pool.openAppenders());

        for (int i = 0; i < cnt; i++) {
            //when
            pool.release(i + min);

            //then
            assertEquals(cnt - i - 1, pool.openAppenders());
        }

        //when + then: open appenders
        assertEquals(0, pool.openAppenders());

        //when + then: illegal releases
        assertThrows(IllegalArgumentException.class, () -> pool.release(-1));
        assertThrows(IllegalArgumentException.class, () -> pool.release(MultiAppenderIdPool.MAX_APPENDERS));
        assertThrows(IllegalArgumentException.class, () -> pool.release(min - 1));

        //when
        pool.close();

        //then
        assertThrows(IllegalStateException.class, pool::acquire);
        assertThrows(IllegalStateException.class, () -> pool.release(min));
        assertEquals(0, pool.openAppenders());
    }

    @Test
    void acquireAndReleaseSome() throws Exception {
        final int threads = 10;
        final int repeat = 5;
        final long maxWaitMillis = 100;
        runTest(threads, repeat, maxWaitMillis);
    }

    @Test
    void acquireAndReleaseMany() throws Exception {
        final int threads = MultiAppenderIdPool.MAX_APPENDERS;
        final int repeat = 3;
        final long maxWaitMillis = 20;
        runTest(threads, repeat, maxWaitMillis);
    }

    private void runTest(final int threadCount,
                         final int repeat,
                         final long maxWaitMillis) throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final AppenderIdPool pool = new MultiAppenderIdPool(new File(tmpDir.toFile(), "appender-ids"));
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
        final int appendedId = pool.acquire();
        
        //then
        assertEquals(0, appendedId);
    }

    private Thread[] initThreads(final int count,
                                 final int repeat,
                                 final long maxWaitMillis,
                                 final AppenderIdPool pool,
                                 final BitSet acquired,
                                 final BitSet released,
                                 final CountDownLatch latch) {
        requireNonNull(pool);
        requireNonNull(acquired);
        requireNonNull(released);
        requireNonNull(latch);
        final Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
            final String name = "appender-" + i;
            threads[i] = new Thread(null, () -> {
                for (int j = 0; j < repeat; j++) {
                    sleepRandom(maxWaitMillis);
                    final int appenderId = pool.acquire();
                    if (j == 0) {
                        sync(() -> acquired.set(appenderId));
                        latch.countDown();
                        await(latch);
                    }
                    sleepRandom(maxWaitMillis);
                    pool.release(appenderId);
                    if (j == 0) {
                        sync(() -> released.set(appenderId));
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