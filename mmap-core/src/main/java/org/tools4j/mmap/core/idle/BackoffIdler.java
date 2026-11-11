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
package org.tools4j.mmap.core.idle;

import java.util.concurrent.locks.LockSupport;

/**
 * Idler spinning, then yielding, then parking for doubling periods, created by {@link Idler#backoff()}.
 */
final class BackoffIdler implements Idler {

    static final int DEFAULT_MAX_SPINS = 10;
    static final int DEFAULT_MAX_YIELDS = 5;
    static final long DEFAULT_MIN_PARK_NANOS = 1_000;
    static final long DEFAULT_MAX_PARK_NANOS = 1_000_000;

    private final int maxSpins;
    private final int maxYields;
    private final long minParkNanos;
    private final long maxParkNanos;

    private int spins;
    private int yields;
    private long parkNanos;

    BackoffIdler(final int maxSpins, final int maxYields, final long minParkNanos, final long maxParkNanos) {
        if (maxSpins < 0 || maxYields < 0 || minParkNanos <= 0 || maxParkNanos < minParkNanos) {
            throw new IllegalArgumentException("Invalid backoff with maxSpins=" + maxSpins + ", maxYields=" +
                    maxYields + ", minParkNanos=" + minParkNanos + " and maxParkNanos=" + maxParkNanos +
                    ", expected non-negative maxSpins and maxYields and 0 < minParkNanos <= maxParkNanos");
        }
        this.maxSpins = maxSpins;
        this.maxYields = maxYields;
        this.minParkNanos = minParkNanos;
        this.maxParkNanos = maxParkNanos;
        reset();
    }

    @Override
    public void idle() {
        if (spins < maxSpins) {
            spins++;
            Thread.onSpinWait();
        } else if (yields < maxYields) {
            yields++;
            Thread.yield();
        } else {
            LockSupport.parkNanos(parkNanos);
            parkNanos = parkNanos > maxParkNanos / 2 ? maxParkNanos : 2 * parkNanos;
        }
    }

    @Override
    public void reset() {
        spins = 0;
        yields = 0;
        parkNanos = minParkNanos;
    }

    /** The period of the next park in nanoseconds, for tests */
    long parkNanos() {
        return parkNanos;
    }

    @Override
    public String toString() {
        return "BACKOFF:maxSpins=" + maxSpins + "|maxYields=" + maxYields + "|minParkNanos=" + minParkNanos +
                "|maxParkNanos=" + maxParkNanos;
    }
}
