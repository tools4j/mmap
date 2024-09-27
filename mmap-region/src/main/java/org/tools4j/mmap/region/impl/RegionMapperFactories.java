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

import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMapperFactory;

import static java.util.Objects.requireNonNull;

/**
 * Factory to create a {@link RegionMapper}, used by {@link RegionMapperFactory}.
 */
public enum RegionMapperFactories {
    ;
    public static RegionMapperFactory sync(final String name) {
        return new RegionMapperFactory() {
            @Override
            public RegionMapper create(final FileMapper fileMapper, final int regionSize) {
                return new SyncRegionMapper(fileMapper, regionSize);
            }

            @Override
            public RegionMapper create(final FileMapper fileMapper, final int regionSize, final int regionCacheSize, final int regionsToMapAhead) {
                if (regionCacheSize <= 1) {
                    return create(fileMapper, regionSize);
                }
                return new SyncRingRegionMapper(fileMapper, regionSize, regionCacheSize);
            }

            @Override
            public boolean isAsync() {
                return false;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static RegionMapperFactory async(final String name) {
        return async(name, BusySpinIdleStrategy.INSTANCE);
    }

    public static RegionMapperFactory async(final String name, final IdleStrategy idleStrategy) {
        return async(name,
                AsyncRuntime.create(idleStrategy, true),
                AsyncRuntime.create(new BackoffIdleStrategy(), true));
    }

    public static RegionMapperFactory async(final String name,
                                            final AsyncRuntime mappingRuntime,
                                            final AsyncRuntime unmappingRuntime) {
        requireNonNull(name);
        requireNonNull(mappingRuntime);
        requireNonNull(unmappingRuntime);
        return new RegionMapperFactory() {
            @Override
            public RegionMapper create(final FileMapper fileMapper, final int regionSize) {
                return create(fileMapper, regionSize, 1, 0);
            }

            @Override
            public RegionMapper create(final FileMapper fileMapper, final int regionSize, final int regionCacheSize, final int regionsToMapAhead) {
                return new AsyncRingRegionMapper(mappingRuntime, unmappingRuntime, fileMapper, regionSize,
                        regionCacheSize, regionsToMapAhead, 2 * regionCacheSize);
            }

            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}
