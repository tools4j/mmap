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

import org.tools4j.mmap.region.impl.PowerOfTwoRegionMetrics;
import org.tools4j.mmap.region.impl.RegionMapperFactories;

import static java.util.Objects.requireNonNull;

/**
 * Region mapper factory with static methods delegating to {@link RegionMapperFactories}.
 */
public interface RegionMapperFactory {
    RegionMapper create(FileMapper fileMapper, int regionSize, final int regionCacheSize);

    /**
     * Returns true if region mappers created by this factory perform asynchronous mapping in the background, and false
     * if mappings are performed synchronously.
     * @return true if this factory creates mappers with asynchronous mapping, and false if with synchronous mapping
     */
    boolean isAsync();

    /**
     * Factory constant for sync region mapping as described in {@link RegionState}.
     * <p>
     * Alternative ways to create region mappers are available through the static {@code sync(..)} factory methods
     * provided by {@link RegionMapperFactories}.
     */
    SyncFactory SYNC = RegionMapperFactories::sync;

    /**
     * Factory constant for async region mapping as described in {@link RegionState}.
     * <p>
     * Alternative ways to create region mappers are available through the static {@code async(..)} factory methods
     * provided by {@link RegionMapperFactories}.
     */
    AsyncFactory ASYNC = RegionMapperFactories::async;

    /**
     * Returns a new async region factory for the provided arguments.
     * @param asyncRuntime       the runtime to use for async operations
     * @param regionsToMapAhead  the number of regions to map-ahead -- must be less than the region cache size
     * @param stopRuntimeOnClose if true the async runtime will be stopped when then mapper is closed
     * @return a new region mapper factory instance that uses the specified parameters
     */
    static AsyncFactory async(final AsyncRuntime asyncRuntime,
                              final int regionsToMapAhead,
                              final boolean stopRuntimeOnClose) {
        requireNonNull(asyncRuntime);
        return (fileMapper, regionSize, regionCacheSize) -> RegionMapperFactories.async(
                asyncRuntime, fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize, regionsToMapAhead,
                stopRuntimeOnClose
        );
    }

    /**
     * Returns a new async region factory for the provided arguments.
     * @param asyncRuntime       the runtime to use for async operations
     * @param regionsToMapAhead  the number of regions to map-ahead -- must be less than the region cache size
     * @param stopRuntimeOnClose if true the async runtime will be stopped when then mapper is closed
     * @param waitingPolicy      the policy defining how to wait for mappings performed asynchronously
     * @param timeoutHandler     handler invoked if mapping is not achieved in time
     * @return a new region mapper factory instance that uses the specified parameters
     */
    static AsyncFactory async(final AsyncRuntime asyncRuntime,
                              final int regionsToMapAhead,
                              final boolean stopRuntimeOnClose,
                              final WaitingPolicy waitingPolicy,
                              final TimeoutHandler<Region> timeoutHandler) {
        requireNonNull(asyncRuntime);
        requireNonNull(waitingPolicy);
        requireNonNull(timeoutHandler);
        return (fileMapper, regionSize, regionCacheSize) -> RegionMapperFactories.async(
                asyncRuntime, fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize, regionsToMapAhead,
                stopRuntimeOnClose, waitingPolicy, timeoutHandler
        );
    }

    /**
     * Returns a new async region factory for the provided arguments.
     * @param baseFactory       the base factory to use
     * @param waitingPolicy     the policy defining how to wait for mappings performed asynchronously
     * @param timeoutHandler    handler invoked if mapping is not achieved in time
     * @return a new region mapper factory instance that uses the specified parameters
     */
    static AsyncFactory async(final RegionMapperFactory baseFactory,
                              final WaitingPolicy waitingPolicy,
                              final TimeoutHandler<Region> timeoutHandler) {
        requireNonNull(baseFactory);
        requireNonNull(waitingPolicy);
        requireNonNull(timeoutHandler);
        return (fileMapper, regionSize, regionCacheSize) -> RegionMapperFactories.async(
                baseFactory.create(fileMapper, regionSize, regionCacheSize),
                waitingPolicy, timeoutHandler
        );
    }

    /**
     * Functional interface for sync region mapper factories.
     */
    @FunctionalInterface
    interface SyncFactory extends RegionMapperFactory {
        @Override
        default boolean isAsync() {
            return false;
        }
    }

    /**
     * Functional interface for async region mapper factories.
     */
    @FunctionalInterface
    interface AsyncFactory extends RegionMapperFactory {
        @Override
        default boolean isAsync() {
            return true;
        }
    }
}
