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

import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validPosition;

final class AsyncRegionManager implements RegionManager {
    private final AsyncRuntime asyncRuntime;
    private final RegionMetrics regionMetrics;
    private final RegionCache regionCache;
    private final int regionsToMapAhead;
    private final boolean stopRuntimeOnClose;

    AsyncRegionManager(final AsyncRuntime asyncRuntime,
                       final FileMapper fileMapper,
                       final RegionMetrics regionMetrics,
                       final int regionCacheSize,
                       final int regionsToMapAhead,
                       final boolean stopRuntimeOnClose) {
        this.asyncRuntime = requireNonNull(asyncRuntime);
        this.regionMetrics = requireNonNull(regionMetrics);
        this.regionCache = new RingRegionCache(regionMetrics, fileMapper, asyncStateMachineFactory(asyncRuntime), this, regionCacheSize);
        this.regionsToMapAhead = Math.min(regionCacheSize - 1, regionsToMapAhead >= 0 ? regionsToMapAhead : regionCacheSize + regionsToMapAhead);
        this.stopRuntimeOnClose = stopRuntimeOnClose;
    }

    private static MutableMappingState.Factory asyncStateMachineFactory(final AsyncRuntime asyncRuntime) {
        requireNonNull(asyncRuntime);
        return (mapper, metrics) -> new AsyncMappingStateMachine(mapper, metrics, asyncRuntime);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public Region map(final long position) {
        validPosition(position);
        return mapFrom(position, -regionMetrics.regionSize());
    }

    @Override
    public Region mapFrom(final long position, final MutableRegion from) {
        validPosition(position);
        final MutableMappingState mappingState = from.mappingState();
        if (mappingState.requestLocal(position)) {
            return from;
        }
        return mapFrom(position, from.regionStartPosition());
    }

    private Region mapFrom(final long position, final long previousRegionStartPosition) {
        final MutableRegion region = regionCache.get(position);
        if (region.mappingState().request(position)) {
            mapAhead(position, previousRegionStartPosition);
        }
        return region;
    }

    private void mapAhead(final long position, final long previousRegionStartPosition) {
        if (regionsToMapAhead <= 0) {
            return;
        }
        final long regionSize = regionMetrics.regionSize();
        final long regionStartPosition = regionMetrics.regionPosition(position);
        final long positionDelta = regionStartPosition - previousRegionStartPosition;
        if (positionDelta == regionSize || positionDelta == -regionSize) {
            //move forward or backward one region, we map-ahead in the same direction
            long nextPosition = regionStartPosition + positionDelta;
            for (int i = 0; i < regionsToMapAhead && nextPosition >= 0; i++) {
                regionCache.get(nextPosition).mappingState().request(nextPosition);
                nextPosition += positionDelta;
            }
        } //else: other random access pattern, no look-ahead as we cannot predict what comes next
    }

    @Override
    public void close() {
        regionCache.close();
        if (stopRuntimeOnClose) {
            asyncRuntime.stop(false);
        }
    }

    @Override
    public String toString() {
        return "AsyncRegionManager:regionSize=" + regionMetrics.regionSize();
    }
}
