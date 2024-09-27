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
package org.tools4j.mmap.region.api;

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.impl.RegionMapperFactories;

/**
 * Factory to create a {@link RegionMapper}.
 */
public interface RegionMapperFactory {
    /**
     * Factory constant for sync region mapping.
     */
    RegionMapperFactory SYNC = sync("RegionMapperFactory:SYNC");

    /**
     * Factory constant for async region mapping.
     */
    RegionMapperFactory ASYNC = async("RegionMapperFactory::ASYNC");

    /**
     * Creates and returns a new region mapper without cache.
     *
     * @param fileMapper        the file mapper to use
     * @param regionSize        the region size
     * @return a new region mapper for the provided parameters
     */
    RegionMapper create(FileMapper fileMapper, int regionSize);

    /**
     * Creates and returns a new region mapper with cache if {@code cacheSize > 1}.
     *
     * @param fileMapper        the file mapper to use
     * @param regionSize        the region size
     * @param regionCacheSize   the number of regions to cache
     * @param regionsToMapAhead regions to map-ahead if async mapping is used (ignored in sync mode)
     * @return a new region mapper for the provided parameters
     */
    RegionMapper create(FileMapper fileMapper,
                        int regionSize,
                        int regionCacheSize,
                        int regionsToMapAhead);

    /**
     * @return true if this factory creates an async mapper with support of background ahead mapping of regions.
     */
    boolean isAsync();

    static RegionMapperFactory sync(final String name) {
        return RegionMapperFactories.sync(name);
    }

    static RegionMapperFactory async(final String name) {
        return RegionMapperFactories.async(name);
    }

    static RegionMapperFactory async(final String name, final IdleStrategy idleStrategy) {
        return RegionMapperFactories.async(name, idleStrategy);
    }

    static RegionMapperFactory async(final String name,
                                     final AsyncRuntime mappingRuntime,
                                     final AsyncRuntime unmappingRuntime) {
        return RegionMapperFactories.async(name, mappingRuntime, unmappingRuntime);
    }
}
