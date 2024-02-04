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
import org.tools4j.mmap.region.impl.AsyncRegionMapper;
import org.tools4j.mmap.region.impl.SyncBackgroundMapAheadRegionMapper;
import org.tools4j.mmap.region.impl.PowerOfTwoRegionMetrics;
import org.tools4j.mmap.region.impl.RingCacheRegionMapper;
import org.tools4j.mmap.region.impl.SyncRegionMapper;

import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Factory to create a {@link RegionMapper}.
 */
public interface RegionMapperFactory {
    /**
     * Factory constant for sync region mapping.
     */
    RegionMapperFactory SYNC = sync("RegionMapperFactory:SYNC");

    /**
     * Factory constant for async region mapping.
     */
    RegionMapperFactory ASYNC = async("RegionMapperFactory::ASYNC", BusySpinIdleStrategy.INSTANCE);

    /**
     * Factory constant for sync region mapping with async ahead mapping in the background
     */
    RegionMapperFactory AHEAD = async("RegionMapperFactory::AHEAD", BusySpinIdleStrategy.INSTANCE);

    /**
     * Creates and returns a new region mapper without cache.
     *
     * @param fileMapper        the file mapper to use
     * @param regionMetrics     the region metrics defined by the region size
     * @return a new region mapper for the provided parameters
     */
    RegionMapper create(FileMapper fileMapper, RegionMetrics regionMetrics);

    /**
     * Creates and returns a new region mapper without cache.
     *
     * @param fileMapper        the file mapper to use
     * @param regionSize        the region size
     * @return a new region mapper for the provided parameters
     */
    default RegionMapper create(final FileMapper fileMapper, final int regionSize) {
        return create(fileMapper, new PowerOfTwoRegionMetrics(regionSize));
    }

    /**
     * Creates and returns a new region mapper with cache if {@code cacheSize > 1}.
     *
     * @param fileMapper        the file mapper to use
     * @param regionSize        the region size
     * @param regionCacheSize   the number of regions to cache
     * @param regionsToMapAhead regions to map-ahead if async mapping is used (ignored in sync mode)
     * @return a new region mapper for the provided parameters
     */
    default RegionMapper create(final FileMapper fileMapper,
                                final int regionSize,
                                final int regionCacheSize,
                                final int regionsToMapAhead) {
        return create(fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize, regionsToMapAhead);
    }

    /**
     * Creates and returns a new region mapper with cache if {@code cacheSize > 1}.
     *
     * @param fileMapper        the file mapper to use
     * @param regionMetrics     the region metrics defined by the region size
     * @param regionCacheSize   the number of regions to cache
     * @param regionsToMapAhead regions to map-ahead if async mapping is used (ignored in sync mode)
     * @return a new region mapper for the provided parameters
     */
    default RegionMapper create(final FileMapper fileMapper,
                                final RegionMetrics regionMetrics,
                                final int regionCacheSize,
                                final int regionsToMapAhead) {
        if (regionCacheSize <= 1) {
            return create(fileMapper, regionMetrics);
        }
        return new RingCacheRegionMapper(fileMapper, this, regionMetrics, regionCacheSize, regionsToMapAhead);
    }

    /**
     * Returns true if region mappers created by this factory perform asynchronous mapping in the background, and false
     * if mappings are performed synchronously.
     * @return true if this factory creates mappers with asynchronous mapping, and false if with synchronous mapping
     */
    boolean isAsync();

    static RegionMapperFactory sync(final String name) {
        return factory(name, false, SyncRegionMapper::new);
    }

    static RegionMapperFactory async(final String name, final IdleStrategy idleStrategy) {
        return async(name, AsyncRuntime.create(idleStrategy));
    }

    static RegionMapperFactory async(final String name, final AsyncRuntime asyncRuntime) {
        requireNonNull(name);
        requireNonNull(asyncRuntime);
        return factory(name, true, (fileMapper, regionMetrics) -> new AsyncRegionMapper(asyncRuntime, fileMapper, regionMetrics));
    }

    static RegionMapperFactory ahead(final String name, final IdleStrategy idleStrategy) {
        return ahead(name, AsyncRuntime.create(idleStrategy));
    }

    static RegionMapperFactory ahead(final String name, final AsyncRuntime asyncRuntime) {
        requireNonNull(name);
        requireNonNull(asyncRuntime);
        return factory(name, true, (fileMapper, regionMetrics) -> new SyncBackgroundMapAheadRegionMapper(asyncRuntime, fileMapper, regionMetrics));
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
                                       final BiFunction<? super FileMapper, ? super RegionMetrics, ? extends RegionMapper> factory) {
        requireNonNull(name);
        requireNonNull(factory);
        return new RegionMapperFactory() {
            @Override
            public RegionMapper create(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
                return factory.apply(fileMapper, regionMetrics);
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
