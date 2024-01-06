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
package org.tools4j.mmap.region.api;

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Defines how long and how to wait for an operation to complete.
 */
public interface WaitingPolicy {
    /** @return the maximum time to wait as per {@link #timeUnit()}, or zero to not wait at all */
    long maxWaitTime();
    /** @return the time unit for {@link #maxWaitTime()} */
    TimeUnit timeUnit();
    /** @return the idle strategy to use while waiting */
    IdleStrategy waitingStrategy();

    /**
     * Waits for the given condition to become true.
     * @param condition the condition to await
     * @return true if condition was met, and false if a timeout occurred
     */
    default boolean await(final BooleanSupplier condition) {
        return await(condition, BooleanSupplier::getAsBoolean);
    }

    /**
     * Waits for the given condition to become true.
     * @param state the state parameter to pass to the condition checker
     * @param condition the condition to await, invoked with the provided state parameter
     * @param <T> the state type parameter
     * @return true if condition was met, and false if a timeout occurred
     */
    default <T> boolean await(final T state, final Predicate<? super T> condition) {
        return WaitingPolicies.await(state, condition, this);
    }

    /** @return a no-wait policy, for instance useful in sync operation modes */
    static WaitingPolicy noWait() {
        return WaitingPolicies.NO_WAIT;
    }

    /**
     * Returns a busy-spin waiting policy; the idle strategy has no state and can be shared between threads.
     *
     * @param maxWaitTime the maximum time to wait as per {@link #timeUnit()}, or zero to not wait at all
     * @param timeUnit the time unit for {@code maxWaitTime}
     * @return a busy-spin waiting policy with given timeout conditions
     */
    static WaitingPolicy busySpinWaiting(final long maxWaitTime, final TimeUnit timeUnit) {
        return create(maxWaitTime, timeUnit, BusySpinIdleStrategy.INSTANCE);
    }

    /**
     * Returns a backoff waiting policy; the idle strategy has state, hence thread-local instances are used.
     *
     * @param maxWaitTime the maximum time to wait as per {@link #timeUnit()}, or zero to not wait at all
     * @param timeUnit the time unit for {@code maxWaitTime}
     * @return a backoff waiting policy that uses thread-local idle strategy instances
     */
    static WaitingPolicy backoffWaiting(final long maxWaitTime, final TimeUnit timeUnit) {
        return threadLocal(maxWaitTime, timeUnit, BackoffIdleStrategy::new);
    }

    /**
     * Returns a waiting policy for the given parameters. Suitable for idle strategies that have no state and can be
     * shared between multiple threads, or for single-thread usage.
     *
     * @param maxWaitTime the maximum time to wait as per {@link #timeUnit()}, or zero to not wait at all
     * @param timeUnit the time unit for {@code maxWaitTime}
     * @param waitingStrategy the idle strategy to use while waiting
     * @return a waiting policy for the given parameters
     */
    static WaitingPolicy create(final long maxWaitTime, final TimeUnit timeUnit, final IdleStrategy waitingStrategy) {
        requireNonNull(waitingStrategy);
        return WaitingPolicies.create(maxWaitTime, timeUnit, () -> waitingStrategy);
    }

    /**
     * Returns a waiting policy for the given parameters. Suitable for idle strategies that have state and are to be
     * exposed as thread-local instances.
     *
     * @param maxWaitTime the maximum time to wait as per {@link #timeUnit()}, or zero to not wait at all
     * @param timeUnit the time unit for {@code maxWaitTime}
     * @param waitingStrategySupplier the idle strategy supplier to create a strategy per thread to use while waiting
     * @return a waiting policy for the given parameters
     */
    static WaitingPolicy threadLocal(final long maxWaitTime,
                                     final TimeUnit timeUnit,
                                     final Supplier<? extends IdleStrategy> waitingStrategySupplier) {
        return WaitingPolicies.create(maxWaitTime, timeUnit, ThreadLocal.withInitial(waitingStrategySupplier)::get);
    }

}
