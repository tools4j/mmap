/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;

/**
 * Factory to create a {@link RegionMapper}, used by {@link RegionMapperFactory}.
 */
public enum RegionMapperFactories {
    ;
    public static RegionMapperFactory sync(final String name) {
        return factory(name, false, () -> {},
                (fileMapper, regionMetrics, closeFinalizer) -> new SyncRegionMapper(fileMapper, regionMetrics)
        );
    }

    public static RegionMapperFactory async(final String name, final IdleStrategy idleStrategy) {
        return async(name, AsyncRuntime.create(idleStrategy), true);
    }

    public static RegionMapperFactory async(final String name,
                                              final AsyncRuntime asyncRuntime,
                                              final boolean autoCloseRuntime) {
        requireNonNull(name);
        requireNonNull(asyncRuntime);
        final Runnable autoCloser = autoCloseRuntime ? asyncRuntime::close : () -> {};
        return factory(name, true, autoCloser,
                (fileMapper, regionMetrics, closeFinalizer) ->
                        new AsyncRegionMapper(asyncRuntime, fileMapper, regionMetrics, closeFinalizer)
        );
    }

    public static RegionMapperFactory ahead(final String name, final IdleStrategy idleStrategy) {
        return ahead(name, AsyncRuntime.create(idleStrategy), true);
    }

    public static RegionMapperFactory ahead(final String name,
                                            final AsyncRuntime asyncRuntime,
                                            final boolean autoCloseRuntime) {
        requireNonNull(name);
        requireNonNull(asyncRuntime);
        final Runnable autoCloser = autoCloseRuntime ? asyncRuntime::close : () -> {};
        return factory(name, true, autoCloser,
                (fileMapper, regionMetrics, closeFinalizer) ->
                        new BackgroundMapAheadRegionMapper(asyncRuntime, fileMapper, regionMetrics, closeFinalizer)
        );
    }

    interface SingleMapperFactory {
        RegionMapper create(FileMapper fileMapper, RegionMetrics regionMetrics, Runnable closeFinalizer);
    }

    /**
     * Convenience method to create factories with name, async property and factory lambda.
     *
     * @param name the to-string name for the factory
     * @param async true if the factory creates async region mappers, and false for sync mappers
     * @param factory the factory to create region mappers
     * @return a region mapper factory
     */
    static RegionMapperFactory factory(final String name,
                                       final boolean async,
                                       final Runnable closeFinalizer,
                                       final SingleMapperFactory factory) {
        requireNonNull(name);
        requireNonNull(closeFinalizer);
        requireNonNull(factory);
        return new RegionMapperFactory() {
            @Override
            public RegionMapper create(final FileMapper fileMapper, final RegionMetrics regionMetrics, final int regionCacheSize, final int regionsToMapAhead) {
                return create(fileMapper, regionMetrics, regionCacheSize, regionsToMapAhead, closeFinalizer);
            }

            @Override
            public RegionMapper create(final FileMapper fileMapper, final RegionMetrics regionMetrics, final int regionCacheSize, final int regionsToMapAhead, final Runnable closeFinalizer) {
                if (regionCacheSize <= 1) {
                    return factory.create(fileMapper, regionMetrics, closeFinalizer);
                }
                return new RingCacheRegionMapper(fileMapper, this, regionMetrics, regionCacheSize, regionsToMapAhead, closeFinalizer);
            }

            @Override
            public boolean isAsync() {
                return async;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
