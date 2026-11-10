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
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.mapping.api.CpuPinning;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit test for {@link PinnedThreadFactory}; pinning is only tested where {@linkplain CpuPinning#isSupported()
 * supported}.
 */
class PinnedThreadFactoryTest {

    private final Queue<String> warnings = new ConcurrentLinkedQueue<>();
    private final ErrorListener errorListener = new ErrorListener() {
        @Override
        public void onWarning(final Describable source, final CharSequence message) {
            assertTrue(source instanceof PinnedThreadFactory);
            warnings.add(message.toString());
        }
    };

    @Test
    void createsNamedThreads() {
        final PinnedThreadFactory factory = new PinnedThreadFactory("pinned", true, 2, 3, errorListener);
        final Thread first = factory.newThread(() -> {});
        final Thread second = factory.newThread(() -> {});
        assertEquals("pinned-1", first.getName());
        assertEquals("pinned-2", second.getName());
        assertTrue(first.isDaemon());
        assertFalse(new PinnedThreadFactory("user", false, 0, 0, errorListener).newThread(() -> {}).isDaemon());
        assertArrayEquals(new int[]{2, 3}, factory.cpus());
        assertEquals("PinnedThreadFactory:namePrefix=pinned|cpus=2-3|cpusInUse=0", factory.toString());
        final int[] toMax = new PinnedThreadFactory("max", true, 1).cpus();
        assertEquals(1, toMax[0]);
        assertEquals(SystemCpus.maxOnlineCpu(), toMax[toMax.length - 1]);
        final int[] even = {6, 0, 2, 4};
        final PinnedThreadFactory evenFactory = new PinnedThreadFactory("even", true, even, errorListener);
        even[0] = 7;//copied
        assertArrayEquals(new int[]{6, 0, 2, 4}, evenFactory.cpus());
        assertEquals("PinnedThreadFactory:namePrefix=even|cpus=6,0,2,4|cpusInUse=0", evenFactory.toString());
    }

    @Test
    void rejectsInvalidCpus() {
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, 0,
                CpuPinning.MAX_CPU + 1));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true,
                SystemCpus.maxOnlineCpu() + 1));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, new int[0]));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, new int[]{2, 4, 2}));
        assertThrows(IllegalArgumentException.class, () -> new PinnedThreadFactory("x", true, new int[]{-1}));
    }

    @Test
    void unsupportedPlatformWarnsAndRunsUnpinned() throws InterruptedException {
        assumeFalse(CpuPinning.isSupported(), "CPU affinity is supported");
        final PinnedThreadFactory factory = new PinnedThreadFactory("unsupported", true, 0, 0, errorListener);
        final Queue<String> events = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 2; i++) {
            final Thread thread = factory.newThread(() -> events.add("run"));
            thread.start();
            thread.join(10_000);
        }
        assertEquals("[run, run]", events.toString());
        assertEquals(2, warnings.size());
        assertTrue(warnings.peek().contains("not supported"), warnings::toString);
        assertTrue(factory.toString().endsWith("cpusInUse=0"), factory::toString);
    }

    @Test
    void pinsThreadsToFreeCpusOfRange() throws InterruptedException {
        assumeTrue(CpuPinning.isSupported(), "CPU affinity is not supported");
        final int cpu = ThreadAffinities.currentThreadAffinity()[0];
        final PinnedThreadFactory factory = new PinnedThreadFactory("pinned", true, cpu, cpu, errorListener);
        final Queue<String> events = new ConcurrentLinkedQueue<>();
        final CountDownLatch pinned = new CountDownLatch(1);
        final CountDownLatch finish = new CountDownLatch(1);
        final Thread first = factory.newThread(() -> {
            events.add("first:" + ThreadAffinities.currentThreadAffinity()[0]);
            pinned.countDown();
            await(finish);
        });

        //when: first holds the only CPU
        first.start();
        assertTrue(pinned.await(10, TimeUnit.SECONDS));
        final Thread second = factory.newThread(() -> events.add("second:" +
                ThreadAffinities.currentThreadAffinity().length));
        second.start();
        second.join(10_000);

        //then: second runs unpinned
        assertEquals(1, warnings.size());
        assertTrue(warnings.peek().contains("all CPUs are in use"), warnings::toString);
        assertTrue(factory.toString().endsWith("cpusInUse=1"), factory::toString);

        //when: first completes, freeing its CPU
        finish.countDown();
        first.join(10_000);
        final Thread third = factory.newThread(() -> assertArrayEquals(new int[]{cpu},
                ThreadAffinities.currentThreadAffinity()));
        third.setUncaughtExceptionHandler((t, e) -> events.add("failed:" + e));
        third.start();
        third.join(10_000);

        //then
        assertEquals("first:" + cpu, events.poll());
        assertTrue(events.poll().startsWith("second:"));
        assertTrue(events.isEmpty(), events::toString);
        assertEquals(1, warnings.size());
        assertTrue(factory.toString().endsWith("cpusInUse=0"), factory::toString);
    }

    @Test
    void cpuIsFreedIfRunnableThrows() throws InterruptedException {
        final PinnedThreadFactory factory = new PinnedThreadFactory("failing", true, 0, 0, ErrorListener.NO_OP);
        final Queue<String> events = new ConcurrentLinkedQueue<>();
        final Thread thread = factory.newThread(() -> {
            throw new IllegalStateException("test");
        });
        thread.setUncaughtExceptionHandler((t, e) -> events.add("uncaught:" + e.getMessage()));
        thread.start();
        thread.join(10_000);
        assertEquals("[uncaught:test]", events.toString());
        assertTrue(factory.toString().endsWith("cpusInUse=0"), factory::toString);
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
