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

import org.tools4j.mmap.region.impl.PowerOfTwoRegionMetrics;
import org.tools4j.mmap.region.impl.RegionMapperFactories;

import static java.util.Objects.requireNonNull;

/**
 * Region mapper factory with constants and static methods delegating to {@link RegionMapperFactories}.
 */
public interface RegionMapperFactory {
    /**
     * Creates and returns a new region mapper.
     *
     * @param fileMapper        the file mapper to use
     * @param regionSize        the region size
     * @param regionCacheSize   the number of regions to cache
     * @param regionsToMapAhead regions to map-ahead if async mapping is used (ignored in sync mode)
     * @return a new region mapper for the provided parameters
     */
    RegionMapper create(FileMapper fileMapper, int regionSize, int regionCacheSize, int regionsToMapAhead);

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
    SyncFactory SYNC = SyncFactory.create("SyncFactory:default", RegionMapperFactories::sync);

    /**
     * Factory constant for async region mapping as described in {@link RegionState}.
     * <p>
     * Alternative ways to create region mappers are available through the static {@code async(..)} factory methods
     * provided by {@link RegionMapperFactories}.
     */
    AsyncFactory ASYNC = AsyncFactory.create("AsyncFactory:default", RegionMapperFactories::async);

    /**
     * Returns a new async region factory for the provided arguments.
     * @param asyncRuntime       the runtime to use for async operations
     * @param stopRuntimeOnClose if true the async runtime will be stopped when then mapper is closed
     * @return a new region mapper factory instance that uses the specified parameters
     */
    static AsyncFactory async(final AsyncRuntime asyncRuntime, final boolean stopRuntimeOnClose) {
        requireNonNull(asyncRuntime);
        return AsyncFactory.create("AsyncFactory:stopRuntimeOnClose=" + stopRuntimeOnClose,
                (fileMapper, regionSize, regionCacheSize, regionsToMapAhead) -> RegionMapperFactories.async(
                        asyncRuntime, fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize,
                        regionsToMapAhead, stopRuntimeOnClose
                )
        );
    }

    /**
     * Returns a new async region factory for the provided arguments.
     * @param asyncRuntime       the runtime to use for async operations
     * @param stopRuntimeOnClose if true the async runtime will be stopped when then mapper is closed
     * @param waitingPolicy      the policy defining how to wait for mappings performed asynchronously
     * @param timeoutHandler     handler invoked if mapping is not achieved in time
     * @return a new region mapper factory instance that uses the specified parameters
     */
    static AsyncFactory async(final AsyncRuntime asyncRuntime,
                              final boolean stopRuntimeOnClose,
                              final WaitingPolicy waitingPolicy,
                              final TimeoutHandler<Region> timeoutHandler) {
        requireNonNull(asyncRuntime);
        requireNonNull(waitingPolicy);
        requireNonNull(timeoutHandler);
        return AsyncFactory.create(
                "AsyncFactory:waitingPolicy=" + waitingPolicy + "|timeoutHandler=" + timeoutHandler,
                (fileMapper, regionSize, regionCacheSize, regionsToMapAhead) -> RegionMapperFactories.async(
                        asyncRuntime, fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize,
                        regionsToMapAhead, stopRuntimeOnClose, waitingPolicy, timeoutHandler
                )
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
        return AsyncFactory.create("AsyncFactory:baseFactory=" + baseFactory + "|waitingPolicy=" +
                waitingPolicy + "|timeoutHandler=" + timeoutHandler,
                (fileMapper, regionSize, regionCacheSize, regionsToMapAhead) -> RegionMapperFactories.async(
                        baseFactory.create(fileMapper, regionSize, regionCacheSize, regionsToMapAhead),
                        waitingPolicy, timeoutHandler
                )
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

        /**
         * Returns a factory with {@code toString()} returning the provided factory name for nicer printing.
         * @param name      the name for the factory
         * @param factory   the actual factory
         * @return a delegate factory that returns the provided name in {@code #toString()}
         */
        static SyncFactory create(final String name, final SyncFactory factory) {
            requireNonNull(name);
            requireNonNull(factory);
            return new SyncFactory() {
                @Override
                public RegionMapper create(final FileMapper fileMapper,
                                           final int regionSize,
                                           final int regionCacheSize,
                                           final int regionsToMapAhead) {
                    return factory.create(fileMapper, regionSize, regionCacheSize, regionsToMapAhead);
                }

                @Override
                public String toString() {
                    return name;
                }
            };
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

        /**
         * Returns a factory with {@code toString()} returning the provided factory name for nicer printing.
         * @param name      the name for the factory
         * @param factory   the actual factory
         * @return a delegate factory that returns the provided name in {@code #toString()}
         */
        static AsyncFactory create(final String name, final AsyncFactory factory) {
            requireNonNull(name);
            requireNonNull(factory);
            return new AsyncFactory() {
                @Override
                public RegionMapper create(final FileMapper fileMapper,
                                           final int regionSize,
                                           final int regionCacheSize,
                                           final int regionsToMapAhead) {
                    return factory.create(fileMapper, regionSize, regionCacheSize, regionsToMapAhead);
                }

                @Override
                public String toString() {
                    return name;
                }
            };
        }
    }
}
