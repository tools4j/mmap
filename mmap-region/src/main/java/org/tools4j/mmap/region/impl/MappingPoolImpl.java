/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2LongCounterMap;
import org.agrona.collections.Long2LongHashMap;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.AdaptiveMapping;
import org.tools4j.mmap.region.api.DynamicMapping;
import org.tools4j.mmap.region.api.ElasticMapping;
import org.tools4j.mmap.region.api.MappingPool;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;

public final class MappingPoolImpl implements MappingPool {

    private final RegionMapper regionMapper;
    private final boolean closeFileMapperOnClose;
    private final RegionMetrics regionMetrics;
    private final Long2LongHashMap positionToAddress;
    private final Long2LongCounterMap positionToRefCount;

    @Unsafe
    public MappingPoolImpl(final RegionMapper regionMapper,
                           final boolean closeFileMapperOnClose,
                           final int initialPoolSize) {
        this.regionMapper = requireNonNull(regionMapper);
        this.closeFileMapperOnClose = closeFileMapperOnClose;
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionMapper.regionSize());
        this.positionToAddress = new Long2LongHashMap(initialPoolSize, Hashing.DEFAULT_LOAD_FACTOR, NULL_ADDRESS);
        this.positionToRefCount = new Long2LongCounterMap(initialPoolSize, Hashing.DEFAULT_LOAD_FACTOR, 0);
    }

    @Override
    public AccessMode accessMode() {
        return regionMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public DynamicMapping acquireDynamicMapping() {
        return null;
    }

    @Override
    public AdaptiveMapping acquireAdaptiveMapping() {
        return null;
    }

    @Override
    public ElasticMapping acquireElasticMapping() {
        return null;
    }

    @Override
    public boolean isClosed() {
        return regionMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
//            try {
//                positionToAddress.forEachLong((pos, addr) -> {
//                    if (pos != NULL_POSITION) {
//                        fileMapper.unmap(pos, addr, regionSize);
//                    }
//                });
//                positionToAddress.clear();
//                positionToRefCount.clear();
//                fileMapper.close();
//            } finally {
//                positionToAddress.clear();
//                positionToRefCount.clear();
//                fileMapper.close();
//                LOGGER.info("Closed ref-count file mapper: fileMapper={}", fileMapper);
//            }
//        }
//
//        if (!regionMapper.isClosed()) {
//            clearMapping();
//            if (closeFileMapperOnClose) {
//                regionMapper.close();
//            }
        }
    }

    @Override
    public String toString() {
        return "MappingPoolImpl" +
                ":mapped=" + positionToAddress.size() +
                "|regionSize=" + regionSize() +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
