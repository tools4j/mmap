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

import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

final class SyncRegionManager implements RegionManager {
    private final RegionMetrics regionMetrics;
    private final RegionCache regionCache;

    SyncRegionManager(final FileMapper fileMapper, final RegionMetrics regionMetrics, final int regionCacheSize) {
        this.regionMetrics = requireNonNull(regionMetrics);
        this.regionCache = new RingRegionCache(regionMetrics, fileMapper, SyncMappingStateMachine::new, this, regionCacheSize);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public Region map(final long position) {
        validPosition(position);
        return mapRegionFromCache(position);
    }

    @Override
    public Region mapFrom(final long position, final MutableRegion from) {
        validPosition(position);
        final MappingState mappingState = from.mappingState();
        if (mappingState.requestLocal(position)) {
            return from;
        }
        return mapRegionFromCache(position);
    }

    private Region mapRegionFromCache(final long position) {
        final MutableRegion region = regionCache.get(position);
        region.mappingState().request(position);
        return region;
    }

    @Override
    public void close() {
        regionCache.close();
    }

    @Override
    public String toString() {
        return "SyncRegionManager:regionSize=" + regionMetrics.regionSize();
    }
}
