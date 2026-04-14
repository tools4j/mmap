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
import org.tools4j.mmap.region.config.AsyncUnmappingConfig;
import org.tools4j.mmap.region.config.MappingConfigurations;
import org.tools4j.mmap.region.config.MappingStrategyConfig;

import java.util.Optional;

import static org.tools4j.mmap.region.config.MappingConfigurations.defaultAsyncMapping;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultAsyncUnmapping;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultDeferUnmapping;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionLruCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionSize;
import static org.tools4j.mmap.region.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.AsyncUnmappingConfigDefaults.ASYNC_UNMAPPING_CONFIG_DEFAULTS;

/**
 * Configuration taking values from {@link MappingConfigurations}.
 */
public enum MappingStrategyConfigDefaults implements MappingStrategyConfig {
    MAPPING_STRATEGY_CONFIG_DEFAULTS,
    MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS,
    MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS,
    MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS;

    private final Optional<AsyncMappingConfig> asyncMappingConfig = Optional.of(ASYNC_MAPPING_CONFIG_DEFAULTS);
    private final Optional<AsyncUnmappingConfig> asyncUnmappingConfig = Optional.of(ASYNC_UNMAPPING_CONFIG_DEFAULTS);

    @Override
    public MappingStrategyConfig toImmutableConfig() {
        return new MappingStrategyConfigImpl(this);
    }

    @Override
    public int regionSize() {
        return defaultRegionSize();
    }

    @Override
    public int cacheSize() {
        return defaultRegionCacheSize();
    }

    @Override
    public int lruCacheSize() {
        return defaultRegionLruCacheSize();
    }

    @Override
    public boolean deferUnmapping() {
        return defaultDeferUnmapping();
    }

    public boolean useAsyncMapping() {
        return this == MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS || defaultAsyncMapping();
    }

    public boolean useAsyncUnmapping() {
        return this == MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS ||
                this == MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS || defaultAsyncUnmapping();
    }

    @Override
    public Optional<AsyncMappingConfig> asyncMapping() {
        return useAsyncMapping() ? asyncMappingConfig : Optional.empty();
    }

    @Override
    public Optional<AsyncUnmappingConfig> asyncUnmapping() {
        return useAsyncUnmapping() ? asyncUnmappingConfig : Optional.empty();
    }

    @Override
    public String toString() {
        return MappingStrategyConfigImpl.toString("MappingStrategyDefaults", this);
    }
}
