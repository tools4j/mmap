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
package org.tools4j.mmap.region.impl;

import org.agrona.BitUtil;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

final class RingRegionCache implements RegionCache {

    private final RegionMetrics regionMetrics;
    private final MutableRegion[] cache;
    private final int cacheSizeMask;

    public RingRegionCache(final RegionMetrics regionMetrics,
                           final FileMapper fileMapper,
                           final MappingStateProvider.Factory mappingStateProviderFactory,
                           final RegionManager regionManager,
                           final int cacheSize) {
        this(regionMetrics, defaultRegionFactory(fileMapper, mappingStateProviderFactory, regionManager), cacheSize);
    }

    public RingRegionCache(final RegionMetrics regionMetrics,
                           final Function<? super RegionMetrics, ? extends MutableRegion> regionFactory,
                           final int cacheSize) {
        this.regionMetrics = requireNonNull(regionMetrics);
        this.cache = initCache(regionMetrics, regionFactory, cacheSize);
        this.cacheSizeMask = cacheSize - 1;
    }

    private static Function<RegionMetrics, DefaultRegion> defaultRegionFactory(final FileMapper fileMapper,
                                                                               final MappingStateProvider.Factory mappingStateProviderFactory,
                                                                               final RegionManager regionManager) {
        requireNonNull(fileMapper);
        requireNonNull(mappingStateProviderFactory);
        requireNonNull(regionManager);
        return metrics -> new DefaultRegion(regionManager, mappingStateProviderFactory.create(fileMapper, metrics));
    }

    private static MutableRegion[] initCache(final RegionMetrics regionMetrics,
                                             final Function<? super RegionMetrics, ? extends MutableRegion> regionFactory,
                                             final int cacheSize) {
        if (!BitUtil.isPowerOfTwo(cacheSize)) {
            throw new IllegalArgumentException("Cache size must be a power of two: " + cacheSize);
        }
        final MutableRegion[] cache = new MutableRegion[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            cache[i] = requireNonNull(regionFactory.apply(regionMetrics));
        }
        return cache;
    }

    @Override
    public MutableRegion get(final long position) {
        final int cacheIndex = (int)(cacheSizeMask & regionMetrics.regionIndex(position));
        return cache[cacheIndex];
    }

    @Override
    public void close() {
        for (final MutableRegion region : cache) {
            region.mappingState().close();
        }
    }
}
