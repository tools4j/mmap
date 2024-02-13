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
import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;
import org.tools4j.mmap.region.api.RegionMetrics;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

public final class RingCacheRegionMapper implements RegionMapper {

    private final RegionMetrics regionMetrics;
    private final RegionMapper[] cache;
    private final int cacheSizeMask;
    private final int regionsToMapAhead;
    private final Runnable closeFinalizer;

    private long lastRegionStartPosition;

    public RingCacheRegionMapper(final FileMapper fileMapper,
                                 final RegionMapperFactory regionMapperFactory,
                                 final RegionMetrics regionMetrics,
                                 final int cacheSize,
                                 final int regionsToMapAhead,
                                 final Runnable closeFinalizer) {
        this.regionMetrics = requireNonNull(regionMetrics);
        this.cache = initCache(regionMetrics, fileMapper, regionMapperFactory, cacheSize);
        this.cacheSizeMask = cacheSize - 1;
        if (regionsToMapAhead + cacheSize < 0 || regionsToMapAhead >= cacheSize) {
            throw new IllegalArgumentException("Regions to map ahead must be in [-" + cacheSize + ", " + (cacheSize-1) +
                    " for region cache size " + cacheSize + " but was " + regionsToMapAhead);
        }
        this.regionsToMapAhead = regionsToMapAhead >= 0 ? regionsToMapAhead : cacheSize + regionsToMapAhead;
        this.closeFinalizer = requireNonNull(closeFinalizer);
        this.lastRegionStartPosition = -regionMetrics.regionSize();
    }

    private static RegionMapper[] initCache(final RegionMetrics regionMetrics,
                                            final FileMapper fileMapper,
                                            final RegionMapperFactory regionMapperFactory,
                                            final int cacheSize) {
        requireNonNull(regionMetrics);
        requireNonNull(fileMapper);
        requireNonNull(regionMapperFactory);
        if (!BitUtil.isPowerOfTwo(cacheSize)) {
            throw new IllegalArgumentException("Cache size must be a power of two: " + cacheSize);
        }
        final RegionMapper[] cache = new RegionMapper[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            cache[i] = requireNonNull(regionMapperFactory.create(fileMapper, regionMetrics, 1, 0, () -> {}));
        }
        return cache;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public int map(final long position, final DirectBuffer buffer) {
        validPosition(position);
        final int cacheIndex = (int)(cacheSizeMask & regionMetrics.regionIndex(position));
        final int result = cache[cacheIndex].map(position, buffer);
        if (result > 0) {
            mapAhead(position, cacheIndex);
        }
        return result;
    }

    private void mapAhead(final long position, final int cacheIndex) {
        assert position >= 0;
        final int mapAhead = regionsToMapAhead;
        if (mapAhead <= 0) {
            return;
        }
        long regionStartPosition = regionMetrics.regionPosition(position);
        if (regionStartPosition != lastRegionStartPosition) {
            final int regionSize = regionMetrics.regionSize();
            if (regionStartPosition == lastRegionStartPosition + regionSize) {
                //sequential forward access
                for (int i = 1; i <= mapAhead; i++) {
                    regionStartPosition += regionSize;
                    if (0 >= cache[cacheSizeMask & (cacheIndex + i)].map(regionStartPosition, null)) {
                        return;
                    }
                }
            } else if (regionStartPosition == lastRegionStartPosition - regionSize) {
                //sequential backward access
                for (int i = 1; i <= mapAhead && regionStartPosition >= regionSize; i++) {
                    regionStartPosition -= regionSize;
                    if (0 >= cache[cacheSizeMask & (cacheIndex - i)].map(regionStartPosition, null)) {
                        return;
                    }
                }
            }
            //else: random access pattern, we don't try to predict the next access
            lastRegionStartPosition = regionStartPosition;
        }
    }

    @Override
    public boolean isAsync() {
        return cache[0].isAsync();
    }

    @Override
    public boolean isClosed() {
        return cache[0].isClosed();
    }

    @Override
    public void close(final long maxWaitMillis) {
        final long startTimeMillis = System.currentTimeMillis();
        for (final RegionMapper mapper : cache) {
            final long elapsedTimeMillis = System.currentTimeMillis() - startTimeMillis;
            final long maxWaitRemainingMillis = Math.max(0, maxWaitMillis - elapsedTimeMillis);
            mapper.close(maxWaitRemainingMillis);
        }
    }

    @Override
    public String toString() {
        return "RingCacheRegionMapper:isAsync=" + isAsync() + "|cache=" + Arrays.toString(cache);
    }
}
