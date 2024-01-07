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

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.concurrent.TimeUnit;

/**
 * Implementors are conscious of the {@linkplain RegionState state} of a {@link Region} for instance indicating whether
 * the region is ready for data access.
 */
public interface RegionStateAware {
    /**
     * Returns the state of the region.
     * @return the region state
     */
    RegionState regionState();

    /**
     * Returns true if the region is ready for data access.
     * @return true if {@link #regionState()} == {@link RegionState#MAPPED}
     */
    default boolean isReady() {
        return regionState().isReady();
    }

    /**
     * Returns true if the region is closed.
     * @return true if {@link #regionState()} == {@link RegionState#CLOSED}
     */
    default boolean isClosed() {
        return regionState().isClosed();
    }

    /**
     * Returns true if the region is not yet ready for data access due to an async mapping operation.
     * @return true if {@link #regionState()} == {@link RegionState#REQUESTED}
     */
    default boolean isPending() {
        return regionState().isPending();
    }

    /**
     * Returns true if this state is associated with asynchronous mapping or unmapping operation.
     * @return true if {@link #regionState()} is {@link RegionState#REQUESTED} or {@link RegionState#CLOSING}
     */
    default boolean isAsync() {
        return regionState().isAsync();
    }

    /**
     * Await {@linkplain #isReady() readiness} for data access, but no longer than what is specified in the waiting
     * policy. The waiting strategy provided by the policy is while waiting if necessary.
     *
     * @param waitingPolicy policy definign maximum time to wait and idle strategy to use while waiting
     * @return true if readiness has been achieved, and false otherwise
     */
    default boolean awaitReadiness(final WaitingPolicy waitingPolicy) {
        return waitingPolicy.await(this, state -> !state.isPending()) && isReady();
    }

    /**
     * Await {@linkplain #isReady() readiness} for data access, but no longer than the given time. A
     * {@link BusySpinIdleStrategy} is used while waiting if necessary. The method returns immediately if the region is
     * not in a {@linkplain #isPending() pending} state.
     *
     * @param time the maximum time to wait
     * @param unit the unit for time
     * @return true if readiness has been achieved, and false otherwise
     */
    default boolean awaitReadiness(final long time, final TimeUnit unit) {
        return awaitReadiness(time, unit, BusySpinIdleStrategy.INSTANCE);
    }

    /**
     * Await {@linkplain #isReady() readiness} for data access, but no longer than the given time. The provided idle
     * strategy is used while waiting if necessary. The method returns immediately if the region is not in a
     * {@linkplain #isPending() pending} state.
     *
     * @param time the maximum time to wait
     * @param unit the unit for time
     * @param idleStrategy the strategy to use while waiting
     * @return true if readiness has been achieved, and false otherwise
     */
    default boolean awaitReadiness(final long time, final TimeUnit unit, final IdleStrategy idleStrategy) {
        return WaitingPolicies.await(this, state -> !state.isPending(), time, unit, idleStrategy) && isReady();
    }
}
