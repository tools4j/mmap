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

import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.tools4j.mmap.region.impl.Constraints.nonNegative;

/**
 * Defines static methods to create {@link WaitingPolicy} instances and to await a condition. The implementations are
 * package private and are used by {@link WaitingPolicy} and {@link RegionStateAware}.
 */
enum WaitingPolicies {
    ;
    static final WaitingPolicy NO_WAIT = create(0, NANOSECONDS, () -> NoOpIdleStrategy.INSTANCE);

    static WaitingPolicy create(final long maxWaitTime,
                                final TimeUnit timeUnit,
                                final Supplier<? extends IdleStrategy> waitingStrategySupplier) {
        nonNegative(maxWaitTime, "maxWaitTime");
        requireNonNull(timeUnit);
        requireNonNull(waitingStrategySupplier);
        return new WaitingPolicy() {
            @Override
            public long maxWaitTime() {
                return maxWaitTime;
            }

            @Override
            public TimeUnit timeUnit() {
                return timeUnit;
            }

            @Override
            public IdleStrategy waitingStrategy() {
                return waitingStrategySupplier.get();
            }

            @Override
            public String toString() {
                return "WaitingPolicy:maxWaitTime=" + maxWaitTime + "|timeUnit=" + timeUnit +
                        "|waitingStrategy=" + waitingStrategySupplier.get();
            }
        };
    }

    static <T> boolean await(final T state,
                             final Predicate<? super T> condition,
                             final WaitingPolicy waitingPolicy) {
        //state is nullable
        if (condition.test(state)) {
            return true;
        }
        final long maxWaitTime = waitingPolicy.maxWaitTime();
        if (maxWaitTime == 0) {
            return false;
        }
        return awaitCondition(state, condition, maxWaitTime, waitingPolicy.timeUnit(), waitingPolicy.waitingStrategy());
    }

    static <T> boolean await(final T state,
                             final Predicate<? super T> condition,
                             final long maxWaitTime,
                             final TimeUnit timeUnit,
                             final IdleStrategy waitingStrategy) {
        //state is nullable
        if (condition.test(state)) {
            return true;
        }
        if (maxWaitTime == 0) {
            return false;
        }
        return awaitCondition(state, condition, maxWaitTime, timeUnit, waitingStrategy);
    }

    static <T> boolean awaitCondition(final T state,
                                      final Predicate<? super T> condition,
                                      final long maxWaitTime,
                                      final TimeUnit timeUnit,
                                      final IdleStrategy waitingStrategy) {
        final long start = System.nanoTime();
        final long nanos = timeUnit.toNanos(maxWaitTime);
        waitingStrategy.reset();
        boolean success;
        do {
            waitingStrategy.idle();
        } while (!(success = condition.test(state)) && (System.nanoTime() - start) < nanos);
        return success || condition.test(state);
    }
}
