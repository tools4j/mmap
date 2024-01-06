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

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;

/**
 * Facade with factory methods to create {@link RegionMapper} instances.  The facade provide access to public parts of
 * {@link RegionManager} implementations without exposing the parts that are package private.
 */
public enum RegionMapperFactories {
    ;
    public static final IdleStrategy DEFAULT_ASYNC_RUNTIME_IDLE_STRATEGY = BusySpinIdleStrategy.INSTANCE;
    public static final int DEFAULT_REGIONS_TO_MAP_AHEAD = 1;
    public static RegionMapper sync(final FileMapper fileMapper, final int regionSize, final int regionCacheSize) {
        return sync(fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize);
    }

    public static RegionMapper sync(final FileMapper fileMapper,
                                    final RegionMetrics regionMetrics,
                                    final int regionCacheSize) {
        return new SyncRegionManager(fileMapper, regionMetrics, regionCacheSize);
    }

    public static RegionMapper async(final FileMapper fileMapper, final int regionSize, final int regionCacheSize) {
        return async(fileMapper, regionSize, regionCacheSize, Math.min(regionCacheSize - 1, DEFAULT_REGIONS_TO_MAP_AHEAD));
    }

    public static RegionMapper async(final FileMapper fileMapper,
                                     final int regionSize,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead) {
        return async(fileMapper, new PowerOfTwoRegionMetrics(regionSize), regionCacheSize, regionsToMapAhead);
    }

    public static RegionMapper async(final FileMapper fileMapper,
                                     final RegionMetrics regionMetrics,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead) {
        return async(AsyncRuntime.create(DEFAULT_ASYNC_RUNTIME_IDLE_STRATEGY), fileMapper, regionMetrics,
                regionCacheSize, regionsToMapAhead, true);
    }

    public static RegionMapper async(final AsyncRuntime asyncRuntime,
                                     final FileMapper fileMapper,
                                     final RegionMetrics regionMetrics,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead,
                                     final boolean stopRuntimeOnClose) {
        return new AsyncRegionManager(asyncRuntime, fileMapper, regionMetrics, regionCacheSize, regionsToMapAhead,
                stopRuntimeOnClose);
    }

    public static RegionMapper async(final FileMapper fileMapper,
                                     final int regionSize,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead,
                                     final WaitingPolicy waitingPolicy,
                                     final TimeoutHandler<Region> timeoutHandler) {
        return new ManagedRegionMapper(
                async(fileMapper, regionSize, regionCacheSize, regionsToMapAhead),
                waitingPolicy, timeoutHandler);
    }

    public static RegionMapper async(final AsyncRuntime asyncRuntime,
                                     final FileMapper fileMapper,
                                     final RegionMetrics regionMetrics,
                                     final int regionCacheSize,
                                     final int regionsToMapAhead,
                                     final boolean stopRuntimeOnClose,
                                     final WaitingPolicy waitingPolicy,
                                     final TimeoutHandler<Region> timeoutHandler) {
        return new ManagedRegionMapper(
                async(asyncRuntime, fileMapper, regionMetrics, regionCacheSize, regionsToMapAhead, stopRuntimeOnClose),
                waitingPolicy, timeoutHandler);
    }

    public static RegionMapper async(final RegionMapper regionMapper,
                                     final WaitingPolicy waitingPolicy,
                                     final TimeoutHandler<Region> timeoutHandler) {
        if (regionMapper instanceof ManagedRegionMapper) {
            final ManagedRegionMapper managedMapper = (ManagedRegionMapper)regionMapper;
            if (managedMapper.waitingPolicy() == waitingPolicy && managedMapper.timeoutHandler() == timeoutHandler) {
                return managedMapper;
            }
            return new ManagedRegionMapper(managedMapper.regionMapper(), waitingPolicy, timeoutHandler);
        }
        return new ManagedRegionMapper(regionMapper, waitingPolicy, timeoutHandler);
    }
}
