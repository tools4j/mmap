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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.config.AsyncMappingConfig;
import org.tools4j.mmap.region.config.AsyncMappingConfigurator;
import org.tools4j.mmap.region.config.AsyncUnmappingConfig;
import org.tools4j.mmap.region.config.AsyncUnmappingConfigurator;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultAsyncMapping;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultAsyncUnmapping;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionLruCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionSize;
import static org.tools4j.mmap.region.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.AsyncUnmappingConfigDefaults.ASYNC_UNMAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionLruCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class MappingStrategyConfiguratorImpl implements MappingStrategyConfigurator {

    private final MappingStrategyConfig defaults;
    private int regionSize = 0;
    private int cacheSize = -1;
    private int lruCacheSize = -1;
    private Boolean deferUnmapping;
    private Optional<AsyncMappingConfig> asyncMapping;
    private Optional<AsyncUnmappingConfig> asyncUnmapping;

    public MappingStrategyConfiguratorImpl() {
        this(MAPPING_STRATEGY_CONFIG_DEFAULTS);
    }

    public MappingStrategyConfiguratorImpl(final MappingStrategyConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    @SuppressWarnings("OptionalAssignedToNull")
    public MappingStrategyConfigurator reset() {
        regionSize = 0;
        cacheSize = -1;
        lruCacheSize = -1;
        deferUnmapping = null;
        asyncMapping = null;
        asyncUnmapping = null;
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
    public int lruCacheSize() {
        if (lruCacheSize < 0) {
            lruCacheSize = defaults.lruCacheSize();
        }
        if (lruCacheSize < 0) {
            lruCacheSize = defaultRegionLruCacheSize();
        }
        return lruCacheSize;
    }

    @Override
    public boolean deferUnmapping() {
        if (deferUnmapping == null) {
            deferUnmapping = defaults.deferUnmapping();
        }
        return deferUnmapping;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public Optional<AsyncMappingConfig> asyncMapping() {
        if (asyncMapping == null) {
            asyncMapping = defaults.asyncMapping();
        }
        if (asyncMapping == null) {
            asyncMapping = defaultAsyncMapping() ? Optional.of(ASYNC_MAPPING_CONFIG_DEFAULTS) : Optional.empty();
        }
        return asyncMapping;
    }

    @SuppressWarnings("OptionalAssignedToNull")
    @Override
    public Optional<AsyncUnmappingConfig> asyncUnmapping() {
        if (asyncUnmapping == null) {
            asyncUnmapping = defaults.asyncUnmapping();
        }
        if (asyncUnmapping == null) {
            asyncUnmapping = defaultAsyncUnmapping() ? Optional.of(ASYNC_UNMAPPING_CONFIG_DEFAULTS) : Optional.empty();
        }
        return asyncUnmapping;
    }

    @Override
    public MappingStrategyConfigurator regionSize(final int regionSize) {
        validateRegionSize(regionSize);
        this.regionSize = regionSize;
        return this;
    }

    @Override
    public MappingStrategyConfigurator cacheSize(final int cacheSize) {
        validateRegionCacheSize(cacheSize);
        this.cacheSize = cacheSize;
        return this;
    }

    @Override
    public MappingStrategyConfigurator lruCacheSize(final int cacheSize) {
        validateRegionLruCacheSize(cacheSize);
        this.lruCacheSize = cacheSize;
        return this;
    }

    @Override
    public MappingStrategyConfigurator deferUnmapping(final boolean deferUnmapping) {
        this.deferUnmapping = deferUnmapping;
        return this;
    }

    @Override
    public MappingStrategyConfigurator asyncMapping(final boolean async) {
        this.asyncMapping = async
                ? asyncMapping != null && asyncMapping.isPresent() ? asyncMapping : Optional.of(ASYNC_MAPPING_CONFIG_DEFAULTS)
                : Optional.empty();
        return this;
    }

    @Override
    public MappingStrategyConfigurator asyncMapping(final AsyncMappingConfig config) {
        this.asyncMapping = Optional.of(config);
        return this;
    }

    @Override
    public MappingStrategyConfigurator asyncMapping(final Consumer<? super AsyncMappingConfigurator> configurator) {
        final AsyncMappingConfigurator config = asyncMapping != null && asyncMapping.isPresent()
                ? AsyncMappingConfigurator.configure(asyncMapping.get())
                : AsyncMappingConfigurator.configure();
        configurator.accept(config);
        return asyncMapping(config);
    }

    @Override
    public MappingStrategyConfigurator asyncUnmapping(final boolean async) {
        this.asyncUnmapping = async
                ? asyncUnmapping != null && asyncUnmapping.isPresent() ? asyncUnmapping : Optional.of(ASYNC_UNMAPPING_CONFIG_DEFAULTS)
                : Optional.empty();
        return this;
    }

    @Override
    public MappingStrategyConfigurator asyncUnmapping(final AsyncUnmappingConfig config) {
        this.asyncUnmapping = Optional.of(config);
        return this;
    }

    @Override
    public MappingStrategyConfigurator asyncUnmapping(final Consumer<? super AsyncUnmappingConfigurator> configurator) {
        final AsyncUnmappingConfigurator config = asyncUnmapping != null && asyncUnmapping.isPresent()
                ? AsyncUnmappingConfigurator.configure(asyncUnmapping.get())
                : AsyncUnmappingConfigurator.configure();
        configurator.accept(config);
        return asyncUnmapping(config);
    }

    @Override
    public MappingStrategyConfig toImmutableConfig() {
        return new MappingStrategyConfigImpl(this);
    }

    @Override
    @SuppressWarnings("OptionalAssignedToNull")
    public String toString() {
        return "MappingStrategyConfiguratorImpl" +
                ":regionSize=" + regionSize +
                "|cacheSize=" + cacheSize +
                "|lruCacheSize=" + lruCacheSize +
                "|deferUnmapping=" + deferUnmapping +
                "|asyncMapping=" + (asyncMapping == null ? null : asyncMapping.map(Object::toString).orElse("n/a")) +
                "|asyncUnmapping=" + (asyncUnmapping == null ? null : asyncUnmapping.map(Object::toString).orElse("n/a")) +
                "|defaults=" + defaults;
    }
}
