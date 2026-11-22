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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.config.AsyncMappingConfig;
import org.tools4j.mmap.mapping.config.AsyncUnmappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.impl.RegionMetricsImpl;

import java.util.function.UnaryOperator;

/**
 * Contains factory methods for {@link RegionMapper} instances.
 */
@Unsafe
public enum RegionMappers {
    ;
    /**
     * Creates a region mapper for the given file mapper, region metrics and mapping strategy.
     *
     * @param fileMapper the file mapper to map and unmap regions of
     * @param regionMetrics the region metrics with region size and overlap size
     * @param config     the mapping strategy defining caching and async mapping and unmapping
     * @return a new region mapper
     */
    public static RegionMapper create(final FileMapper fileMapper,
                                      final RegionMetrics regionMetrics,
                                      final MappingStrategyConfig config) {
        return create(fileMapper, regionMetrics, config, MappingConfigurations.defaultLifecycleListener());
    }

    /**
     * Creates a region mapper for the given file mapper with region size, overlap size, mapping strategy and
     * lifecycle listener from the given mapping configuration.
     *
     * @param fileMapper the file mapper to map and unmap regions of
     * @param config     the mapping configuration with region size, overlap size, mapping strategy and lifecycle
     *                   listener
     * @return a new region mapper
     */
    public static RegionMapper create(final FileMapper fileMapper, final MappingConfig config) {
        final RegionMetrics regionMetrics = new RegionMetricsImpl(config.regionSize(), config.overlapSize());
        return create(fileMapper, regionMetrics, config.mappingStrategy(), config.lifecycleListener());
    }

    /**
     * Creates a region mapper for the given file mapper, region metrics and mapping strategy.
     *
     * @param fileMapper        the file mapper to map and unmap regions of
     * @param regionMetrics     the region metrics with region size and overlap size
     * @param config            the mapping strategy defining caching and async mapping and unmapping
     * @param lifecycleListener the listener notified when an async run-ahead region mapper is opened and closed
     * @return a new region mapper
     */
    public static RegionMapper create(final FileMapper fileMapper,
                                      final RegionMetrics regionMetrics,
                                      final MappingStrategyConfig config,
                                      final LifecycleListener lifecycleListener) {
        final AsyncMappingConfig asyncMappingConfig = config.asyncMapping().orElse(null);
        final AsyncUnmappingConfig asyncUnmappingConfig = config.asyncUnmapping().orElse(null);
        if (asyncMappingConfig == null) {
            return asyncUnmappingConfig == null
                    ? createSyncRegionMapper(fileMapper, regionMetrics, config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping())
                    : createSyncRegionMapper(fileMapper, regionMetrics, config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncUnmappingConfig);
        } else {
            return asyncUnmappingConfig == null
                    ? createAsyncRunAheadRegionMapper(fileMapper, regionMetrics, config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncMappingConfig, lifecycleListener)
                    : createAsyncRunAheadRegionMapper(fileMapper, regionMetrics, config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncMappingConfig, asyncUnmappingConfig,
                    lifecycleListener);
        }
    }

    /**
     * Creates a region mapper with an unbounded cache and reference counting for mapped regions, delegating actual
     * mapping and unmapping operations to the given base mapper. A region is unmapped when its reference count
     * reaches zero.
     *
     * @param baseMapper       the region mapper performing the actual mapping and unmapping operations
     * @param initialCacheSize the initial capacity of the region cache
     * @return a new reference counting region mapper
     */
    public static RegionMapper createRefCountRegionMapper(final RegionMapper baseMapper, final int initialCacheSize) {
        return new RefCountRegionMapper(baseMapper, initialCacheSize);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
        return new SyncRegionMapper(fileMapper, regionMetrics);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final RegionMetrics regionMetrics,
                                                      final int cacheSize) {
        return createSyncRegionMapper(fileMapper, regionMetrics, cacheSize, 0, false);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final RegionMetrics regionMetrics,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping) {
        return createSyncRegionMapper(new SyncRegionMapper(fileMapper, regionMetrics), cacheSize, lruCacheSize,
                deferUnmapping);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final RegionMetrics regionMetrics,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping,
                                                      final AsyncUnmappingConfig asyncUnmappingConfig) {
        return createSyncRegionMapper(fileMapper, regionMetrics, cacheSize, lruCacheSize, deferUnmapping,
                asyncUnmappingConfig.unmappingRuntimeSupplier().get(), asyncUnmappingConfig.unmappingCacheSize());
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final RegionMetrics regionMetrics,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping,
                                                      final AsyncRuntime unmappingRuntime,
                                                      final int unmappingCacheSize) {
        final DirectRegionMapper regionMapper = new SyncRegionMapperAsyncUnmapper(unmappingRuntime, fileMapper,
                regionMetrics, unmappingCacheSize);
        return createSyncRegionMapper(regionMapper, cacheSize, lruCacheSize, deferUnmapping);
    }

    private static RegionMapper createSyncRegionMapper(final DirectRegionMapper regionMapper,
                                                       final int cacheSize,
                                                       final int lruCacheSize,
                                                       final boolean deferUnmapping) {
        if (cacheSize <= 1 && lruCacheSize <= 0 && !deferUnmapping) {
            //direct, no caches
            return regionMapper;
        } else if (lruCacheSize <= 0) {
            //ring cache only
            return new RingCacheRegionMapper(regionMapper, Math.max(1, cacheSize), deferUnmapping);
        } else if (cacheSize <= 1) {
            //LRU cache only
            return new LruCacheRegionMapper(regionMapper, lruCacheSize, deferUnmapping);
        } else {
            //ring cache, then LRU cache
            return new RingCacheRegionMapper(new LruCacheRegionMapper(regionMapper, lruCacheSize, deferUnmapping),
                    cacheSize, deferUnmapping);
        }
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final RegionMetrics regionMetrics,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncMappingConfig asyncMappingConfig,
                                                               final LifecycleListener lifecycleListener) {
        return createAsyncRunAheadRegionMapper(fileMapper, regionMetrics, cacheSize, lruCacheSize, deferUnmapping,
                asyncMappingConfig.mappingRuntimeSupplier().get(), asyncMappingConfig.regionsToMapAhead(),
                asyncMappingConfig.aheadMappingCacheSize(), lifecycleListener);
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final RegionMetrics regionMetrics,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncMappingConfig asyncMappingConfig,
                                                               final AsyncUnmappingConfig asyncUnmappingConfig,
                                                               final LifecycleListener lifecycleListener) {
        return createAsyncRunAheadRegionMapper(fileMapper, regionMetrics, cacheSize, lruCacheSize, deferUnmapping,
                asyncMappingConfig.mappingRuntimeSupplier().get(), asyncMappingConfig.regionsToMapAhead(),
                asyncMappingConfig.aheadMappingCacheSize(), asyncUnmappingConfig.unmappingRuntimeSupplier().get(),
                asyncUnmappingConfig.unmappingCacheSize(), lifecycleListener);
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final RegionMetrics regionMetrics,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncRuntime mappingRuntime,
                                                               final int regionsToMapAhead,
                                                               final int mapAheadCacheSize,
                                                               final LifecycleListener lifecycleListener) {
        final DirectRegionMapper baseMapper = new SyncRegionMapper(fileMapper, regionMetrics);
        return createAsyncRunAheadRegionMapper(baseMapper, mappingRuntime, cacheSize, lruCacheSize, deferUnmapping,
                regionsToMapAhead, mapAheadCacheSize, lifecycleListener);
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final RegionMetrics regionMetrics,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncRuntime mappingRuntime,
                                                               final int regionsToMapAhead,
                                                               final int mapAheadCacheSize,
                                                               final AsyncRuntime unmappingRuntime,
                                                               final int unmapCacheSize,
                                                               final LifecycleListener lifecycleListener) {
        final DirectRegionMapper baseMapper = new SyncRegionMapperAsyncUnmapper(unmappingRuntime, fileMapper,
                regionMetrics, unmapCacheSize);
        return createAsyncRunAheadRegionMapper(baseMapper, mappingRuntime, cacheSize, lruCacheSize, deferUnmapping,
                regionsToMapAhead, mapAheadCacheSize, lifecycleListener);
    }

    private static RegionMapper createAsyncRunAheadRegionMapper(final DirectRegionMapper baseMapper,
                                                                final AsyncRuntime mappingRuntime,
                                                                final int cacheSize,
                                                                final int lruCacheSize,
                                                                final boolean deferUnmapping,
                                                                final int regionsToMapAhead,
                                                                final int mapAheadCacheSize,
                                                                final LifecycleListener lifecycleListener) {
        final UnaryOperator<RegionMapper> cacheFactory;
        if (cacheSize <= 0 && lruCacheSize <= 0 && !deferUnmapping) {
            //direct, no caches
            cacheFactory = UnaryOperator.identity();
        } else if (lruCacheSize <= 0) {
            //ring cache only
            final int actualCacheSize = Math.max(1, cacheSize);//1 for deferUnmapping case
            cacheFactory = mapper -> new RingCacheRegionMapper(mapper, actualCacheSize, deferUnmapping);
        } else if (cacheSize <= 1) {
            //LRU cache only
            cacheFactory = mapper -> new LruCacheRegionMapper(mapper, lruCacheSize, deferUnmapping);
        } else {
            //ring cache, then LRU cache
            cacheFactory = mapper -> new RingCacheRegionMapper(
                    new LruCacheRegionMapper(mapper, lruCacheSize, deferUnmapping), cacheSize, deferUnmapping
            );
        }
        return new AsyncRunAheadRegionMapper(mappingRuntime, baseMapper, cacheFactory, regionsToMapAhead, mapAheadCacheSize,
                lifecycleListener);
    }
}
