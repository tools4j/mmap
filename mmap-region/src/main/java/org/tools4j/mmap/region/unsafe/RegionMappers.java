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
package org.tools4j.mmap.region.unsafe;

import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.config.AsyncMappingConfig;
import org.tools4j.mmap.region.config.AsyncUnmappingConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfig;

import java.util.function.UnaryOperator;

/**
 * Contains factory methods for {@link RegionMapper} instances.
 */
@Unsafe
public enum RegionMappers {
    ;
    public static RegionMapper create(final FileMapper fileMapper, final MappingStrategyConfig config) {
        final AsyncMappingConfig asyncMappingConfig = config.asyncMapping().orElse(null);
        final AsyncUnmappingConfig asyncUnmappingConfig = config.asyncUnmapping().orElse(null);
        if (asyncMappingConfig == null) {
            return asyncUnmappingConfig == null
                    ? createSyncRegionMapper(fileMapper, config.regionSize(), config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping())
                    : createSyncRegionMapper(fileMapper, config.regionSize(), config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncUnmappingConfig);
        } else {
            return asyncUnmappingConfig == null
                    ? createAsyncRunAheadRegionMapper(fileMapper, config.regionSize(), config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncMappingConfig)
                    : createAsyncRunAheadRegionMapper(fileMapper, config.regionSize(), config.cacheSize(),
                    config.lruCacheSize(), config.deferUnmapping(), asyncMappingConfig, asyncUnmappingConfig);
        }
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper, final int regionSize) {
        return new SyncRegionMapper(fileMapper, regionSize);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize) {
        return createSyncRegionMapper(fileMapper, regionSize, cacheSize, 0, false);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping) {
        return createSyncRegionMapper(new SyncRegionMapper(fileMapper, regionSize), cacheSize, lruCacheSize, deferUnmapping);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping,
                                                      final AsyncUnmappingConfig asyncUnmappingConfig) {
        return createSyncRegionMapper(fileMapper, regionSize, cacheSize, lruCacheSize, deferUnmapping,
                asyncUnmappingConfig.unmappingRuntimeSupplier().get(), asyncUnmappingConfig.unmappingCacheSize());
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize,
                                                      final int lruCacheSize,
                                                      final boolean deferUnmapping,
                                                      final AsyncRuntime unmappingRuntime,
                                                      final int unmappingCacheSize) {
        final DirectRegionMapper regionMapper = new SyncRegionMapperAsyncUnmapper(unmappingRuntime, fileMapper, regionSize,
                unmappingCacheSize);
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
            return new RingCacheRegionMapper(new LruCacheRegionMapper(regionMapper, cacheSize, deferUnmapping),
                    lruCacheSize, deferUnmapping);
        }
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final int regionSize,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncMappingConfig asyncMappingConfig) {
        return createAsyncRunAheadRegionMapper(fileMapper, regionSize, cacheSize, lruCacheSize, deferUnmapping,
                asyncMappingConfig.mappingRuntimeSupplier().get(), asyncMappingConfig.regionsToMapAhead(),
                asyncMappingConfig.aheadMappingCacheSize());
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final int regionSize,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncMappingConfig asyncMappingConfig,
                                                               final AsyncUnmappingConfig asyncUnmappingConfig) {
        return createAsyncRunAheadRegionMapper(fileMapper, regionSize, cacheSize, lruCacheSize, deferUnmapping,
                asyncMappingConfig.mappingRuntimeSupplier().get(), asyncMappingConfig.regionsToMapAhead(),
                asyncMappingConfig.aheadMappingCacheSize(), asyncUnmappingConfig.unmappingRuntimeSupplier().get(),
                asyncUnmappingConfig.unmappingCacheSize());
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final int regionSize,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncRuntime mappingRuntime,
                                                               final int regionsToMapAhead,
                                                               final int mapAheadCacheSize) {
        final DirectRegionMapper baseMapper = new SyncRegionMapper(fileMapper, regionSize);
        return createAsyncRunAheadRegionMapper(baseMapper, mappingRuntime, cacheSize, lruCacheSize, deferUnmapping,
                regionsToMapAhead, mapAheadCacheSize);
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final FileMapper fileMapper,
                                                               final int regionSize,
                                                               final int cacheSize,
                                                               final int lruCacheSize,
                                                               final boolean deferUnmapping,
                                                               final AsyncRuntime mappingRuntime,
                                                               final int regionsToMapAhead,
                                                               final int mapAheadCacheSize,
                                                               final AsyncRuntime unmappingRuntime,
                                                               final int unmapCacheSize) {
        final DirectRegionMapper baseMapper = new SyncRegionMapperAsyncUnmapper(unmappingRuntime, fileMapper, regionSize,
                unmapCacheSize);
        return createAsyncRunAheadRegionMapper(baseMapper, mappingRuntime, cacheSize, lruCacheSize, deferUnmapping,
                regionsToMapAhead, mapAheadCacheSize);
    }

    private static RegionMapper createAsyncRunAheadRegionMapper(final DirectRegionMapper baseMapper,
                                                                final AsyncRuntime mappingRuntime,
                                                                final int cacheSize,
                                                                final int lruCacheSize,
                                                                final boolean deferUnmapping,
                                                                final int regionsToMapAhead,
                                                                final int mapAheadCacheSize) {
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
        return new AsyncRunAheadRegionMapper(mappingRuntime, baseMapper, cacheFactory, regionsToMapAhead, mapAheadCacheSize);
    }
}
