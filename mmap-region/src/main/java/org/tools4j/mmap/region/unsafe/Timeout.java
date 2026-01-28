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
package org.tools4j.mmap.region.unsafe;

import java.util.concurrent.atomic.AtomicReference;

final class Timeout {
    public static final long GRACEFUL_TIMEOUT_MILLIS = 4000;
    public static final long TOTAL_TIMEOUT_MILLIS = 5000;
    private static final long MIN_WAIT_MILLIS = 20;

    enum Termination {
        STOP_GRACEFULLY,
        STOP_IMMEDIATELY,
        STOPPED
    }

    private final long gracefulTimeoutMillis;
    private final long totalTimeoutMillis;
    private final AtomicReference<Termination> termination = new AtomicReference<>();

    public Timeout() {
        this(GRACEFUL_TIMEOUT_MILLIS, TOTAL_TIMEOUT_MILLIS);
    }

    public Timeout(final long gracefulTimeoutMillis, final long totalTimeoutMillis) {
        this.gracefulTimeoutMillis = gracefulTimeoutMillis;
        this.totalTimeoutMillis = totalTimeoutMillis;
    }

    public boolean isStoppedOrStopping() {
        return termination.get() != null;
    }

    public boolean isStopGracefully() {
        return termination.get() == Termination.STOP_GRACEFULLY;
    }

    public boolean isStopImmediately() {
        return termination.get() == Termination.STOP_IMMEDIATELY;
    }

    public boolean isStopped() {
        return termination.get() == Termination.STOPPED;
    }

    public void stopAndWait() {
        stopGracefully();
        final long end = System.currentTimeMillis() + totalTimeoutMillis;
        if (!awaitStopped(gracefulTimeoutMillis)) {
            stopImmediately();
            final long remainingMillis = end - System.currentTimeMillis();
            awaitStopped(remainingMillis);
        }
    }

    public void stopGracefully() {
        termination.compareAndSet(null, Termination.STOP_GRACEFULLY);
    }

    public void stopImmediately() {
        if (!termination.compareAndSet(null, Termination.STOP_IMMEDIATELY)) {
            termination.compareAndSet(Termination.STOP_GRACEFULLY, Termination.STOP_IMMEDIATELY);
        }
    }

    void stopped() {
        termination.set(Termination.STOPPED);
    }

    public boolean awaitStopped(final long timeoutMillis) {
        if (termination.get() == null) {
            throw new IllegalStateException("Must stop before awaiting");
        }
        long end = System.currentTimeMillis() + timeoutMillis;
        while (termination.get() != Termination.STOPPED) {
            if (System.currentTimeMillis() > end || !sleep()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sleep() {
        try {
            Thread.sleep(Timeout.MIN_WAIT_MILLIS);
            return true;
        } catch (final InterruptedException e) {
            return false;
        }
    }
}
