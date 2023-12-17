/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
import static org.tools4j.mmap.region.api.WaitingPolicies.awaitCondition;

/**
 * Defines how long and how to wait for an operation to complete.
 */
public interface WaitingPolicy {
    long maxWaitTime();
    TimeUnit timeUnit();
    IdleStrategy waitingStrategy();

    default boolean await(final BooleanSupplier condition) {
        return await(condition, BooleanSupplier::getAsBoolean);
    }

    default <T> boolean await(final T state, final Predicate<? super T> condition) {
        if (condition.test(state)) {
            return true;
        }
        final long maxWaitTime = maxWaitTime();
        if (maxWaitTime == 0) {
            return false;
        }
        return awaitCondition(state, condition, maxWaitTime, timeUnit(), waitingStrategy());
    }

    static WaitingPolicy noWait() {
        return WaitingPolicies.NO_WAIT;
    }
    static WaitingPolicy busySpinWaiting(final long maxWaitTime, final TimeUnit timeUnit) {
        return create(maxWaitTime, timeUnit, BusySpinIdleStrategy.INSTANCE);
    }

    static WaitingPolicy backoffWaiting(final long maxWaitTime, final TimeUnit timeUnit) {
        return threadLocal(maxWaitTime, timeUnit, BackoffIdleStrategy::new);
    }

    static WaitingPolicy create(final long maxWaitTime, final TimeUnit timeUnit, final IdleStrategy waitingStrategy) {
        requireNonNull(waitingStrategy);
        return WaitingPolicies.create(maxWaitTime, timeUnit, () -> waitingStrategy);
    }

    static WaitingPolicy threadLocal(final long maxWaitTime,
                                     final TimeUnit timeUnit,
                                     final Supplier<? extends IdleStrategy> waitingStrategySupplier) {
        return WaitingPolicies.create(maxWaitTime, timeUnit, ThreadLocal.withInitial(waitingStrategySupplier)::get);
    }

}
