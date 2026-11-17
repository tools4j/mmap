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

/**
 * Waits when a thread's duty cycle found no work. Idlers keep no state: the calling thread counts how often it idled
 * in a row and passes the count to {@link #idle(int)}, which returns the count to pass next time, so that one idler
 * instance can be shared by all threads.
 * <p>
 * A thread starts with an idle count of zero, idles after each duty cycle without work and resets the count to zero
 * after one with work:
 * <pre>{@code
 * int idleCount = 0;
 * while (running) {
 *     idleCount = doWork() ? 0 : idler.idle(idleCount);
 * }
 * }</pre>
 * Unlike the work count passed to Agrona's {@code IdleStrategy.idle(int)}, the idle count is kept by the caller from
 * the return value. A caller dropping it keeps idling with count zero, which for a backing-off idler means spinning
 * without ever parking. A custom idler returns the count incremented, saturating, as
 * {@code Math.max(idleCount, idleCount + 1)} does.
 */
@FunctionalInterface
public interface Idler {

    /**
     * Waits once, after a duty cycle found no work.
     *
     * @param idleCount the number of idles since the last duty cycle with work, as returned by the previous
     *                  invocation, zero for the first idle
     * @return the idle count to pass to the next invocation, {@code idleCount + 1}, saturating at
     *         {@link Integer#MAX_VALUE} so that a long idle phase never overflows it
     */
    int idle(int idleCount);

    /**
     * Returns the idler returning immediately, so that the calling loop spins without even the spin-wait hint of
     * {@link #busySpin()}: for loops that wait by other means, and for tests.
     *
     * @return the no-op idler
     * @see IdleMode#NO_OP
     */
    static Idler noOp() {
        return StatelessIdler.NO_OP;
    }

    /**
     * Returns the idler busy-spinning with {@link Thread#onSpinWait()}: lowest latency, keeping one CPU busy all the
     * time.
     *
     * @return the busy-spinning idler
     * @see IdleMode#BUSY_SPIN
     */
    static Idler busySpin() {
        return StatelessIdler.BUSY_SPIN;
    }

    /**
     * Returns the idler yielding with {@link Thread#yield()}: low latency, giving the CPU to other threads ready to
     * run.
     *
     * @return the yielding idler
     * @see IdleMode#YIELDING
     */
    static Idler yielding() {
        return StatelessIdler.YIELDING;
    }

    /**
     * Returns the backing-off idler with default limits: it spins 10 times, then yields 5 times, then parks for 1
     * microsecond, doubling the period up to 1 millisecond, see {@link #backoff(int, int, long, long)}.
     *
     * @return the backing-off idler with default limits
     * @see IdleMode#BACKOFF
     */
    static Idler backoff() {
        return BackoffIdler.DEFAULT;
    }

    /**
     * Returns a new backing-off idler: it spins with {@link Thread#onSpinWait()} for the first {@code maxSpins} idles,
     * then yields with {@link Thread#yield()} for the next {@code maxYields}, then parks with
     * {@link java.util.concurrent.locks.LockSupport#parkNanos(long) LockSupport.parkNanos(..)} for
     * {@code minParkNanos}, doubling the period with every further idle up to {@code maxParkNanos}.
     *
     * @param maxSpins     the number of spins before yielding, zero or more
     * @param maxYields    the number of yields before parking, zero or more
     * @param minParkNanos the first park period in nanoseconds, positive
     * @param maxParkNanos the longest park period in nanoseconds, at least {@code minParkNanos}
     * @return a new backing-off idler
     * @throws IllegalArgumentException if a parameter is out of range
     */
    static Idler backoff(final int maxSpins, final int maxYields, final long minParkNanos, final long maxParkNanos) {
        return new BackoffIdler(maxSpins, maxYields, minParkNanos, maxParkNanos);
    }
}
