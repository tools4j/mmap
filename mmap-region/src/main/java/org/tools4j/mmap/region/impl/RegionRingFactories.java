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

import org.tools4j.mmap.region.api.*;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RegionRingFactories {
    private RegionRingFactories() {
        throw new IllegalStateException("RegionRingFactories is not instantiable");
    }

    /**
     * Creates a region mapper thread and returns the factory that will
     * register created regions in the thread to watch for regions that require mapping.
     *
     * @param regionFactory region factory
     * @param runtime async runtime
     * @return instance of {@link RegionRingFactory}
     */
    public static RegionRingFactory async(final RegionFactory<? extends AsyncRegion> regionFactory,
                                           final AsyncRuntime runtime)
    {
        Objects.requireNonNull(regionFactory);
        Objects.requireNonNull(runtime);

        return new RegionRingFactory() {
            @Override
            public Region[] create(int ringSize, int regionSize, FileMapper fileMapper, long timeout, TimeUnit timeUnit) {
                final AsyncRegion[] regions = new AsyncRegion[ringSize];

                for (int i = 0; i < ringSize; i++) {
                    regions[i] = regionFactory.create(regionSize, fileMapper, timeout, timeUnit);
                }

                runtime.register(() -> {
                    int processed = 0;
                    for (final AsyncRegionMapper asyncRegionMapper : regions) {
                        try {
                            processed += asyncRegionMapper.processRequest() ? 1 : 0;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                    return processed;
                });
                return regions;
            }

            @Override
            public String toString() {
                return "ASYNC (" + regionFactory + ") with " + runtime;
            }
        };
    }

    /**
     * Returns the factory that will
     * map regions synchronously.
     *
     * @param regionFactory region factory
     * @return instance of {@link RegionRingFactory}
     */
    public static RegionRingFactory sync(final RegionFactory<? extends Region> regionFactory) {
        Objects.requireNonNull(regionFactory);

        return new RegionRingFactory() {
            @Override
            public Region[] create(int ringSize, int regionSize, FileMapper fileMapper, long timeout, TimeUnit timeUnit) {
                final Region[] regions = new Region[ringSize];

                for (int i = 0; i < ringSize; i++) {
                    regions[i] = regionFactory.create(regionSize, fileMapper, timeout, timeUnit);
                }
                return regions;
            }

            @Override
            public String toString() {
                return "SYNC RegionRingFactory";
            }
        };
    }

    public static RegionRingFactory async(final AsyncRuntime asyncRuntime) {
        return async(RegionFactory.ASYNC_VOLATILE_STATE_MACHINE, asyncRuntime);
    }

    public static RegionRingFactory sync() {
        return sync(RegionFactory.SYNC);
    }

}
