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
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.config.MappingStrategy.AsyncOptions;

/**
 * Contains factory methods for {@link RegionMapper} instances.
 */
@Unsafe
public enum RegionMappers {
    ;
    public static RegionMapper create(final FileMapper fileMapper, final MappingStrategy strategy) {
        final AsyncOptions asyncOptions = strategy.asyncOptions().orElse(null);
        if (asyncOptions == null) {
            return createSyncRegionMapper(fileMapper, strategy.regionSize(), strategy.cacheSize());
        }
        return createAsyncRunAheadRegionMapper(asyncOptions.mappingRuntime(), asyncOptions.unmappingRuntime(),
                fileMapper, strategy.regionSize(), strategy.cacheSize(), asyncOptions.regionsToMapAhead(),
                asyncOptions.unmapCacheSize(), asyncOptions.deferUnmap());
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper, final int regionSize) {
        return new SyncRegionMapper(fileMapper, regionSize);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize) {
        final RegionMapper regionMapper = createSyncRegionMapper(fileMapper, regionSize);
        return cacheSize <= 1 ? regionMapper : new RingCacheRegionMapper(regionMapper, cacheSize, true);
    }

    public static RegionMapper createSyncRegionMapper(final FileMapper fileMapper,
                                                      final int regionSize,
                                                      final int cacheSize,
                                                      final AsyncRuntime unmappingRuntime,
                                                      final int unmapCacheSize) {
        final RegionMapper regionMapper = new AsyncUnmappingRegionMapper(unmappingRuntime, fileMapper, regionSize, unmapCacheSize);
        return cacheSize <= 1 ? regionMapper : new RingCacheRegionMapper(regionMapper, cacheSize, true);
    }

    public static RegionMapper createAsyncRunAheadRegionMapper(final AsyncRuntime mappingRuntime,
                                                               final AsyncRuntime unmappingRuntime,
                                                               final FileMapper fileMapper,
                                                               final int regionSize,
                                                               final int cacheSize,
                                                               final int regionsToMapAhead,
                                                               final int unmapCacheSize,
                                                               final boolean deferUnmap) {
        final DirectRegionMapper baseMapper = new AsyncUnmappingRegionMapper(unmappingRuntime, fileMapper, regionSize, unmapCacheSize);
        return new AsyncRunAheadRegionMapper(mappingRuntime, baseMapper, cacheSize, regionsToMapAhead, deferUnmap);
    }
}
