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

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.config.MappingStrategy.AsyncOptions;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultMappingAsyncRuntimeSupplier;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionsToMapAhead;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultUnmappingAsyncRuntimeSupplier;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionsToMapAhead;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

public class MappingStrategyConfiguratorImpl implements MappingStrategyConfigurator {

    private final MappingStrategyConfig defaults;
    private int regionSize = 0;
    private int cacheSize = -1;
    private int regionsToMapAhead = -1;
    private Supplier<? extends AsyncRuntime> mappingAsyncRuntimeSupplier;
    private Supplier<? extends AsyncRuntime> unmappingAsyncRuntimeSupplier;
    private static final AtomicInteger mapperCounter = new AtomicInteger();
    private static final AtomicInteger unmapperCounter = new AtomicInteger();

    public MappingStrategyConfiguratorImpl() {
        this(MAPPING_STRATEGY_CONFIG_DEFAULTS);
    }

    public MappingStrategyConfiguratorImpl(final MappingStrategy defaultStrategy) {
        this(toConfig(defaultStrategy));
    }

    private static MappingStrategyConfig toConfig(final MappingStrategy defaultStrategy) {
        requireNonNull(defaultStrategy);
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure()
                .regionSize(defaultStrategy.regionSize())
                .cacheSize(defaultStrategy.cacheSize())
                .regionsToMapAhead(0);
        final AsyncOptions asyncOptions = defaultStrategy.asyncOptions().orElse(null);
        if (asyncOptions != null) {
            config.regionsToMapAhead(asyncOptions.regionsToMapAhead())
                    .mappingAsyncRuntime(asyncOptions.mappingRuntime())
                    .unmappingAsyncRuntime(asyncOptions.unmappingRuntime());
        }
        return config;
    }

    public MappingStrategyConfiguratorImpl(final MappingStrategyConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public MappingStrategyConfigurator reset() {
        regionSize = 0;
        cacheSize = -1;
        regionsToMapAhead = -1;
        mappingAsyncRuntimeSupplier = null;
        unmappingAsyncRuntimeSupplier = null;
        return this;
    }

    @Override
    public int regionSize() {
        if (regionSize <= 0) {
            regionSize = defaults.regionSize();
        }
        if (regionSize <= 0) {
            regionSize = defaultRegionSize();
        }
        return regionSize;
    }

    @Override
    public MappingStrategyConfigurator regionSize(final int regionSize) {
        validateRegionSize(regionSize);
        this.regionSize = regionSize;
        return this;
    }

    @Override
    public int cacheSize() {
        if (cacheSize < 0) {
            cacheSize = defaults.cacheSize();
        }
        if (cacheSize < 0) {
            cacheSize = defaultRegionCacheSize();
        }
        return cacheSize;
    }

    @Override
    public MappingStrategyConfigurator cacheSize(final int cacheSize) {
        validateRegionCacheSize(cacheSize);
        this.cacheSize = cacheSize;
        return this;
    }

    @Override
    public int regionsToMapAhead() {
        if (regionsToMapAhead < 0) {
            regionsToMapAhead = defaults.regionsToMapAhead();
        }
        if (regionsToMapAhead < 0) {
            regionsToMapAhead = defaultRegionsToMapAhead();
        }
        return regionsToMapAhead;
    }

    @Override
    public MappingStrategyConfigurator regionsToMapAhead(final int regionsToMapAhead) {
        validateRegionsToMapAhead(regionsToMapAhead);
        this.regionsToMapAhead = regionsToMapAhead;
        return this;
    }

    @Override
    public Supplier<? extends AsyncRuntime> mappingAsyncRuntimeSupplier() {
        if (mappingAsyncRuntimeSupplier == null) {
            mappingAsyncRuntimeSupplier = defaults.mappingAsyncRuntimeSupplier();
        }
        if (mappingAsyncRuntimeSupplier == null) {
            mappingAsyncRuntimeSupplier = defaultMappingAsyncRuntimeSupplier();
        }
        return mappingAsyncRuntimeSupplier;
    }

    @Override
    public MappingStrategyConfigurator mappingAsyncRuntime(final AsyncRuntime mappingRuntime) {
        requireNonNull(mappingRuntime);
        return mappingAsyncRuntimeSupplier(() -> mappingRuntime);
    }

    @Override
    public MappingStrategyConfigurator mappingAsyncRuntimeSupplier(final Supplier<? extends AsyncRuntime> mappingRuntimeSupplier) {
        this.mappingAsyncRuntimeSupplier = requireNonNull(mappingRuntimeSupplier);
        return this;
    }

    @Override
    public MappingStrategyConfigurator mappingAsyncRuntimeSupplierUsing(final IdleStrategy idleStrategy) {
        requireNonNull(idleStrategy);
        return mappingAsyncRuntimeSupplierUsing(() -> idleStrategy);
    }

    @Override
    public MappingStrategyConfigurator mappingAsyncRuntimeSupplierUsing(final Supplier<? extends IdleStrategy> idleStrategy) {
        requireNonNull(idleStrategy);
        return mappingAsyncRuntimeSupplier(() -> AsyncRuntime.create(
                "mapper-" + mapperCounter.incrementAndGet(), idleStrategy.get(), true));
    }

    @Override
    public Supplier<? extends AsyncRuntime> unmappingAsyncRuntimeSupplier() {
        if (unmappingAsyncRuntimeSupplier == null) {
            unmappingAsyncRuntimeSupplier = defaults.unmappingAsyncRuntimeSupplier();
        }
        if (unmappingAsyncRuntimeSupplier == null) {
            unmappingAsyncRuntimeSupplier = defaultUnmappingAsyncRuntimeSupplier();
        }
        return unmappingAsyncRuntimeSupplier;
    }

    @Override
    public MappingStrategyConfigurator unmappingAsyncRuntime(final AsyncRuntime unmappingRuntime) {
        requireNonNull(unmappingRuntime);
        return unmappingAsyncRuntimeSupplier(() -> unmappingRuntime);
    }

    @Override
    public MappingStrategyConfigurator unmappingAsyncRuntimeSupplier(final Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier) {
        this.unmappingAsyncRuntimeSupplier = requireNonNull(unmappingRuntimeSupplier);
        return this;
    }

    @Override
    public MappingStrategyConfigurator unmappingAsyncRuntimeSupplierUsing(final IdleStrategy idleStrategy) {
        requireNonNull(idleStrategy);
        return unmappingAsyncRuntimeSupplierUsing(() -> idleStrategy);
    }

    @Override
    public MappingStrategyConfigurator unmappingAsyncRuntimeSupplierUsing(final Supplier<? extends IdleStrategy> idleStrategy) {
        requireNonNull(idleStrategy);
        return unmappingAsyncRuntimeSupplier(() -> AsyncRuntime.create(
                "unmapper-" + unmapperCounter.incrementAndGet(), idleStrategy.get(), true));
    }

    @Override
    public MappingStrategyConfig toImmutableMappingStrategyConfig() {
        return new MappingStrategyConfigImpl(this);
    }

    @Override
    public String toString() {
        return "MappingStrategyConfiguratorImpl" +
                ":regionSize=" + regionSize +
                "|cacheSize=" + cacheSize +
                "|regionsToMapAhead=" + regionsToMapAhead;
    }
}
