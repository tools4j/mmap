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

import org.agrona.LangUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link IdPool}, {@link IdPool64} and {@link IdPool256}
 */
class IdPoolTest {

    private static final long TIMEOUT_MILLIS = 3_000;

    enum PoolFactory {
        IdPool64(64, IdPool64::new),
        IdPool256(256, IdPool256::new),
        IdPool256Max100(100, file -> new IdPool256(file, 100));
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

    @Test
    void poolsWithDifferentMaxIdsShareFile() throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final File file = new File(tmpDir.toFile(), "test.ids");
        try (final IdPool single = new IdPool256(file, 1);
             final IdPool all = new IdPool256(file)) {
            //then
            assertEquals(IdPool256.FILE_SIZE, file.length());

            //when + then
            assertEquals(0, single.acquire());
            assertThrows(IllegalStateException.class, single::acquire);
            assertThrows(IllegalArgumentException.class, () -> single.release(1));
            assertEquals(1, all.acquire());
            assertEquals(2, single.acquired());

            //when + then
            assertTrue(all.release(0));
            assertEquals(0, single.acquire());
        }
    }

    @ParameterizedTest(name = "maxIds={0}")
    @ValueSource(ints = {1, 2, 63, 64, 65, 100, 127, 128, 129, 191, 192, 193, 200, 255, 256})
    void acquiresExactlyIdsBelowMaxIds(final int maxIds) throws Exception {
        //given
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final File file = new File(tmpDir.toFile(), "test.ids");
        try (final IdPool limited = new IdPool256(file, maxIds);
             final IdPool all = new IdPool256(file)) {
            //when + then
            for (int id = 0; id < maxIds; id++) {
                assertEquals(id, limited.acquire());
            }
            assertThrows(IllegalStateException.class, limited::acquire);
            assertThrows(IllegalArgumentException.class, () -> limited.release(maxIds));

            //then: IDs from maxIds upwards remain available
            for (int id = maxIds; id < IdPool256.MAX_IDS; id++) {
                assertEquals(id, all.acquire());
            }
            assertThrows(IllegalStateException.class, all::acquire);

            //when + then: released ID below maxIds is acquired again
            assertTrue(limited.release(maxIds - 1));
            assertEquals(maxIds - 1, limited.acquire());
        }
    }

    @Test
    void releasingIdNotAcquiredReturnsFalseAndCloseIsIdempotent() throws Exception {
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final File file = new File(tmpDir.toFile(), "test.ids");
        final IdPool pool = new IdPool256(file.getPath(),
                Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool256.FILE_SIZE));
        assertFalse(pool.release(70));
        assertEquals(0, pool.acquire());
        assertTrue(pool.release(0));
        assertFalse(pool.release(0));
        pool.close();
        assertTrue(pool.isClosed());
        pool.close();
        assertTrue(pool.isClosed());
    }

    @Test
    void mappingOfWrongSizeIsRejected() throws Exception {
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final File file = new File(tmpDir.toFile(), "test.ids");
        try (final Mapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool64.FILE_SIZE)) {
            assertThrows(IllegalArgumentException.class, () -> new IdPool256(file.getPath(), mapping));
            assertTrue(mapping.isClosed(), "owned mapping must be closed if construction fails");
        }
        try (final Mapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool256.FILE_SIZE)) {
            assertThrows(IllegalArgumentException.class, () -> new IdPool64(file.getPath(), mapping));
            assertTrue(mapping.isClosed(), "owned mapping must be closed if construction fails");
        }
        final Mapping mapping64 = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool64.FILE_SIZE);
        assertThrows(NullPointerException.class, () -> new IdPool64(null, mapping64));
        assertTrue(mapping64.isClosed(), "owned mapping must be closed if name is null");
        final Mapping mapping256 = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool256.FILE_SIZE);
        assertThrows(NullPointerException.class, () -> new IdPool256(null, mapping256));
        assertTrue(mapping256.isClosed(), "owned mapping must be closed if name is null");
    }

    @Test
    void invalidMaxIds() throws Exception {
        final Path tmpDir = Files.createTempDirectory(getClass().getSimpleName());
        final File file = new File(tmpDir.toFile(), "test.ids");
        assertThrows(IllegalArgumentException.class, () -> new IdPool256(file, 0));
        assertThrows(IllegalArgumentException.class, () -> new IdPool256(file, IdPool256.MAX_IDS + 1));
        final Mapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0L, IdPool256.FILE_SIZE);
        assertThrows(IllegalArgumentException.class, () -> new IdPool256(file.getPath(), mapping, 0));
        assertTrue(mapping.isClosed(), "owned mapping must be closed if construction fails");
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