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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.AdaptiveMapping;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.MappingPool;
import org.tools4j.mmap.mapping.api.RegionMapping;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.api.Unsafe;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.impl.Constraints.validateNotClosed;
import static org.tools4j.mmap.mapping.unsafe.RegionMappers.createRefCountRegionMapper;

public final class MappingPoolImpl implements MappingPool, Describable {

    private final RegionMapper regionMapper;
    private final List<DynamicMapping> mappings;
    private final LifecycleListener lifecycleListener;

    @Unsafe
    public MappingPoolImpl(final RegionMapper baseMapper, final int initialPoolSize) {
        this(baseMapper, initialPoolSize, MappingConfigurations.defaultLifecycleListener());
    }

    @Unsafe
    public MappingPoolImpl(final RegionMapper baseMapper,
                           final int initialPoolSize,
                           final LifecycleListener lifecycleListener) {
        this(baseMapper, Function.identity(), initialPoolSize, lifecycleListener);
    }

    @Unsafe
    public MappingPoolImpl(final RegionMapper baseMapper,
                           final Function<? super RegionMapper, ? extends RegionMapper> cacheFactory,
                           final int initialPoolSize,
                           final LifecycleListener lifecycleListener) {
        this.regionMapper = cacheFactory.apply(createRefCountRegionMapper(baseMapper, initialPoolSize));
        this.mappings = new ArrayList<>(initialPoolSize);
        this.lifecycleListener = requireNonNull(lifecycleListener);
        lifecycleListener.onOpened(this);
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
                lifecycleListener.onClosed(this);
            }
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("MappingPoolImpl")
                .append(":mappings=").append(mappings.size())
                .append("|accessMode=").append(accessMode())
                .append("|regionSize=").append(regionSize())
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
