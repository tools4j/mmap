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
package org.tools4j.mmap.mapping.api;

import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.config.SharingPolicy;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link AsyncRuntimeInstances}: shared, per-thread and individual runtimes.
 * <p>
 * Every runtime created here other than the shared ones is stopped again, since each runs its own thread; per-thread
 * runtimes are created on a separate thread so that the stopped runtime is not seen by other tests.
 */
class AsyncRuntimeInstancesTest {

    private static final long STOP_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

    @Test
    void sharedRuntimesAreRunningSingletons() {
        assertSame(AsyncRuntimeInstances.sharedMappingRuntime(), AsyncRuntimeInstances.sharedMappingRuntime());
        assertSame(AsyncRuntimeInstances.sharedUnmappingRuntime(), AsyncRuntimeInstances.sharedUnmappingRuntime());
        assertNotSame(AsyncRuntimeInstances.sharedMappingRuntime(), AsyncRuntimeInstances.sharedUnmappingRuntime());
        assertTrue(AsyncRuntimeInstances.sharedMappingRuntime().isRunning());
        assertTrue(AsyncRuntimeInstances.sharedUnmappingRuntime().isRunning());
    }

    @Test
    void sharedPolicySuppliesSharedRuntime() {
        assertSame(AsyncRuntimeInstances.sharedMappingRuntime(),
                AsyncRuntimeInstances.mappingRuntimeSupplier(SharingPolicy.SHARED).get());
        assertSame(AsyncRuntimeInstances.sharedUnmappingRuntime(),
                AsyncRuntimeInstances.unmappingRuntimeSupplier(SharingPolicy.SHARED).get());
    }

    @Test
    void newRuntimesAreIndividualAndCanBeStopped() {
        assertIndividualRuntimes(AsyncRuntimeInstances::newMappingRuntime);
        assertIndividualRuntimes(AsyncRuntimeInstances::newUnmappingRuntime);
        assertIndividualRuntimes(() -> AsyncRuntimeInstances.newMappingRuntime(new SleepingMillisIdleStrategy(1)));
        assertIndividualRuntimes(() -> AsyncRuntimeInstances.newUnmappingRuntime(new SleepingMillisIdleStrategy(1)));
    }

    @Test
    void individualPolicySuppliesNewRuntimes() {
        assertIndividualRuntimes(AsyncRuntimeInstances.mappingRuntimeSupplier(SharingPolicy.INDIVIDUAL));
        assertIndividualRuntimes(AsyncRuntimeInstances.unmappingRuntimeSupplier(SharingPolicy.INDIVIDUAL));
    }

    @Test
    void perThreadPolicySuppliesOneRuntimePerThread() throws InterruptedException {
        assertPerThreadRuntimes(AsyncRuntimeInstances.mappingRuntimeSupplier(SharingPolicy.PER_THREAD));
        assertPerThreadRuntimes(AsyncRuntimeInstances.unmappingRuntimeSupplier(SharingPolicy.PER_THREAD));
    }

    @Test
    void autoStopOnLastDeregister() {
        final AsyncRuntime.Recurring task = () -> 0;
        final AsyncRuntime autoStop = AsyncRuntimeInstances.newMappingRuntime(new SleepingMillisIdleStrategy(1), true);
        autoStop.register(task);
        autoStop.deregister(task);
        awaitStopped(autoStop);

        final AsyncRuntime noAutoStop = AsyncRuntimeInstances.newUnmappingRuntime(new SleepingMillisIdleStrategy(1),
                false);
        try {
            noAutoStop.register(task);
            noAutoStop.deregister(task);
            assertTrue(noAutoStop.isRunning());
        } finally {
            noAutoStop.close();
        }
        awaitStopped(noAutoStop);
    }

    private static void assertIndividualRuntimes(final Supplier<AsyncRuntime> supplier) {
        final AsyncRuntime runtime1 = supplier.get();
        final AsyncRuntime runtime2 = supplier.get();
        try {
            assertNotSame(runtime1, runtime2);
            assertTrue(runtime1.isRunning());
            assertTrue(runtime2.isRunning());
        } finally {
            runtime1.close();
            runtime2.close();
        }
        awaitStopped(runtime1);
        awaitStopped(runtime2);
    }

    private static void assertPerThreadRuntimes(final Supplier<AsyncRuntime> supplier) throws InterruptedException {
        final AsyncRuntime[] thread1 = getTwiceOnNewThread(supplier);
        final AsyncRuntime[] thread2 = getTwiceOnNewThread(supplier);
        try {
            assertSame(thread1[0], thread1[1]);
            assertSame(thread2[0], thread2[1]);
            assertNotSame(thread1[0], thread2[0]);
        } finally {
            thread1[0].close();
            thread2[0].close();
        }
        awaitStopped(thread1[0]);
        awaitStopped(thread2[0]);
    }

    private static AsyncRuntime[] getTwiceOnNewThread(final Supplier<AsyncRuntime> supplier)
            throws InterruptedException {
        final AsyncRuntime[] runtimes = new AsyncRuntime[2];
        final Thread thread = new Thread(() -> {
            runtimes[0] = supplier.get();
            runtimes[1] = supplier.get();
        });
        thread.start();
        thread.join();
        return runtimes;
    }

    private static void awaitStopped(final AsyncRuntime runtime) {
        final long deadline = System.nanoTime() + STOP_TIMEOUT_NANOS;
        while (runtime.isRunning() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(runtime.isRunning(), runtime.toString());
        assertTrue(runtime.isClosed());
    }
}
