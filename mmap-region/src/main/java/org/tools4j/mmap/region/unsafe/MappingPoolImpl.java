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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.AdaptiveMapping;
import org.tools4j.mmap.region.api.DynamicMapping;
import org.tools4j.mmap.region.api.ElasticMapping;
import org.tools4j.mmap.region.api.MappingPool;
import org.tools4j.mmap.region.api.RegionMapping;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.AdaptiveMappingImpl;
import org.tools4j.mmap.region.impl.ElasticMappingImpl;
import org.tools4j.mmap.region.impl.RegionMappingImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;

public final class MappingPoolImpl implements MappingPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingPoolImpl.class);
    private final RegionMapper regionMapper;
    private final List<DynamicMapping> mappings;

    @Unsafe
    public MappingPoolImpl(final RegionMapper baseMapper, final int initialPoolSize) {
        this(baseMapper, Function.identity(), initialPoolSize);
    }

    @Unsafe
    public MappingPoolImpl(final RegionMapper baseMapper,
                           final Function<? super RegionMapper, ? extends RegionMapper> cacheFactory,
                           final int initialPoolSize) {
        this.regionMapper = cacheFactory.apply(new RefCountRegionMapper(baseMapper, initialPoolSize));
        this.mappings = new ArrayList<>(initialPoolSize);
    }

    @Override
    public AccessMode accessMode() {
        return regionMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMapper.regionMetrics();
    }

    @Override
    public RegionMapping acquireRegionMapping() {
        validateNotClosed(this);
        return register(new RegionMappingImpl(regionMapper, false));
    }

    @Override
    public ElasticMapping acquireElasticMapping() {
        validateNotClosed(this);
        return register(new ElasticMappingImpl(regionMapper, false));
    }

    @Override
    public AdaptiveMapping acquireAdaptiveMapping() {
        validateNotClosed(this);
        return register(new AdaptiveMappingImpl(regionMapper, false));
    }

    private <T extends DynamicMapping> T register(final T mapping) {
        mappings.add(mapping);
        return mapping;
    }

    @Override
    public boolean isClosed() {
        return regionMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            try {
                for (int i = 0; i < mappings.size(); i++) {
                    mappings.get(i).close();
                }
            } finally {
                mappings.clear();
                regionMapper.close();
                LOGGER.info("Closed mapping pool: regionMapper={}", regionMapper);
            }
        }
    }

    @Override
    public String toString() {
        return "MappingPoolImpl" +
                ":mappings=" + mappings.size() +
                "|accessMode=" + accessMode() +
                "|regionSize=" + regionSize() +
                "|closed=" + isClosed();
    }
}
