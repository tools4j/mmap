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
package org.tools4j.mmap.mapping.unsafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Timeout} termination state transitions and waiting.
 */
class TimeoutTest {

    private static final long JOIN_MILLIS = 10_000;

    private final Timeout timeout = new Timeout();

    @Test
    void initiallyNotStopping() {
        assertFalse(timeout.isStoppedOrStopping());
        assertFalse(timeout.isStopGracefully());
        assertFalse(timeout.isStopImmediately());
        assertFalse(timeout.isStopped());
    }

    @Test
    void stopGracefully() {
        timeout.stopGracefully();
        assertTrue(timeout.isStoppedOrStopping());
        assertTrue(timeout.isStopGracefully());
        assertFalse(timeout.isStopImmediately());
        assertFalse(timeout.isStopped());
    }

    @Test
    void stopImmediately() {
        timeout.stopImmediately();
        assertTrue(timeout.isStoppedOrStopping());
        assertTrue(timeout.isStopImmediately());
        assertFalse(timeout.isStopGracefully());
    }

    @Test
    void stopImmediatelyEscalatesGracefulStop() {
        timeout.stopGracefully();
        timeout.stopImmediately();
        assertTrue(timeout.isStopImmediately());
        assertFalse(timeout.isStopGracefully());
    }

    @Test
    void stopRequestsDoNotRevertStopped() {
        timeout.stopGracefully();
        timeout.stopped();
        timeout.stopGracefully();
        timeout.stopImmediately();
        assertTrue(timeout.isStopped());
        assertTrue(timeout.isStoppedOrStopping());
    }

    @Test
    void awaitStoppedBeforeStopThrows() {
        assertThrows(IllegalStateException.class, () -> timeout.awaitStopped(0));
    }

    @Test
    void awaitStoppedReturnsTrueIfStopped() {
        timeout.stopGracefully();
        timeout.stopped();
        assertTrue(timeout.awaitStopped(0));
    }

    @Test
    void awaitStoppedReturnsFalseOnTimeout() {
        timeout.stopGracefully();
        assertFalse(timeout.awaitStopped(0));
    }

    @Test
    void awaitStoppedReturnsFalseIfInterrupted() {
        timeout.stopGracefully();
        Thread.currentThread().interrupt();
        try {
            assertFalse(timeout.awaitStopped(JOIN_MILLIS));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void stopAndWaitReturnsOnceStoppedGracefully() throws InterruptedException {
        final Timeout longGracePeriod = new Timeout(JOIN_MILLIS, 2 * JOIN_MILLIS);
        final Thread worker = startWorkerStoppingWhen(longGracePeriod, true);
        longGracePeriod.stopAndWait();
        worker.join(JOIN_MILLIS);
        assertTrue(longGracePeriod.isStopped());
    }

    @Test
    void stopAndWaitEscalatesToImmediateStopAfterGracefulTimeout() throws InterruptedException {
        final Timeout shortGracePeriod = new Timeout(50, JOIN_MILLIS);
        final Thread worker = startWorkerStoppingWhen(shortGracePeriod, false);
        shortGracePeriod.stopAndWait();
        worker.join(JOIN_MILLIS);
        assertTrue(shortGracePeriod.isStopped());
    }

    private static Thread startWorkerStoppingWhen(final Timeout timeout, final boolean graceful) {
        final Thread worker = new Thread(() -> {
            while (graceful ? !timeout.isStoppedOrStopping() : !timeout.isStopImmediately()) {
                Thread.onSpinWait();
            }
            timeout.stopped();
        });
        worker.setDaemon(true);
        worker.start();
        return worker;
    }
}
