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
 * Waits when a thread's duty cycle found no work. An idler may keep state between calls, such as how long it waited
 * already, so every thread needs its own instance, for instance from {@link IdleMode#newIdler()} or a
 * {@link java.util.function.Supplier Supplier} such as {@code Idler::backoff}.
 * <p>
 * A thread calls {@link #idle()} after each duty cycle without work and {@link #reset()} after one with work:
 * <pre>{@code
 * if (workCount > 0) {
 *     idler.reset();
 * } else {
 *     idler.idle();
 * }
 * }</pre>
 * Stateless idlers only implement {@link #idle()}, and can be lambdas, e.g. {@code Thread::onSpinWait}.
 */
@FunctionalInterface
public interface Idler {

    /**
     * Waits once, after a duty cycle found no work.
     */
    void idle();

    /**
     * Resets this idler after a duty cycle did some work, so that the next {@link #idle()} starts with the shortest
     * wait again. Does nothing by default, for stateless idlers.
     */
    default void reset() {}

    /**
     * Returns the idler returning immediately, so that the calling loop spins without even the spin-wait hint of
     * {@link #busySpin()}: for loops that wait by other means, and for tests.
     *
     * @return the shared no-op idler, which is stateless
     * @see IdleMode#NO_OP
     */
    static Idler noOp() {
        return StatelessIdler.NO_OP;
    }

    /**
     * Returns the idler busy-spinning with {@link Thread#onSpinWait()}: lowest latency, keeping one CPU busy all the
     * time.
     *
     * @return the shared busy-spinning idler, which is stateless
     * @see IdleMode#BUSY_SPIN
     */
    static Idler busySpin() {
        return StatelessIdler.BUSY_SPIN;
    }

    /**
     * Returns the idler yielding with {@link Thread#yield()}: low latency, giving the CPU to other threads ready to
     * run.
     *
     * @return the shared yielding idler, which is stateless
     * @see IdleMode#YIELDING
     */
    static Idler yielding() {
        return StatelessIdler.YIELDING;
    }

    /**
     * Returns a new backing-off idler with default limits: it spins 10 times, then yields 5 times, then parks for 1
     * microsecond, doubling the period up to 1 millisecond, see {@link #backoff(int, int, long, long)}.
     *
     * @return a new backing-off idler, for one thread
     * @see IdleMode#BACKOFF
     */
    static Idler backoff() {
        return backoff(BackoffIdler.DEFAULT_MAX_SPINS, BackoffIdler.DEFAULT_MAX_YIELDS,
                BackoffIdler.DEFAULT_MIN_PARK_NANOS, BackoffIdler.DEFAULT_MAX_PARK_NANOS);
    }

    /**
     * Returns a new backing-off idler: it spins with {@link Thread#onSpinWait()} up to {@code maxSpins} times, then
     * yields with {@link Thread#yield()} up to {@code maxYields} times, then parks with
     * {@link java.util.concurrent.locks.LockSupport#parkNanos(long) LockSupport.parkNanos(..)} for
     * {@code minParkNanos}, doubling the period with every further idle up to {@code maxParkNanos}, until it is
     * {@linkplain #reset() reset}.
     *
     * @param maxSpins     the number of spins before yielding, zero or more
     * @param maxYields    the number of yields before parking, zero or more
     * @param minParkNanos the first park period in nanoseconds, positive
     * @param maxParkNanos the longest park period in nanoseconds, at least {@code minParkNanos}
     * @return a new backing-off idler, for one thread
     * @throws IllegalArgumentException if a parameter is out of range
     */
    static Idler backoff(final int maxSpins, final int maxYields, final long minParkNanos, final long maxParkNanos) {
        return new BackoffIdler(maxSpins, maxYields, minParkNanos, maxParkNanos);
    }
}
