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
package org.tools4j.mmap.region.config;

import org.tools4j.mmap.region.api.DynamicMapping;

import java.util.Optional;

import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS;

/**
 * Configuration used to parameterize the actual mapping operations used for {@link DynamicMapping dynamic mappings}.
 */
public interface MappingStrategyConfig {
    /** @return the size of the region mapped into memory when an actual mapping operation occurs */
    int regionSize();
    /** @return the cache size with mapped regions, usually a ring cache */
    int cacheSize();
    /** @return the LRU cache size with mapped regions evicting mappings on a least-recently-used (LRU) basis */
    int lruCacheSize();
    /** @return true if unmapping operations are to be deferred until it becomes necessary, for instance due to cache eviction */
    boolean deferUnmapping();

    /** @return the async mapping configuration, or absent to indicate that all mappings are performed synchronously */
    Optional<AsyncMappingConfig> asyncMapping();
    /** @return the async unmapping configuration, or absent to indicate that all un-mappings are performed synchronously */
    Optional<AsyncUnmappingConfig> asyncUnmapping();

    /** @return an immutable version of this mapping strategy config, for instance useful if this is a {@link MappingStrategyConfigurator} */
    MappingStrategyConfig toImmutableConfig();

    /**
     * Creates and returns a new configurator instance that allows customization of mapping strategy configuration.
     * System defaults are used where no custom configuration is provided.
     *
     * @return a new mapping strategy configurator
     * @see #getDefault()
     */
    static MappingStrategyConfigurator configure() {
        return MappingStrategyConfigurator.configure();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of mapping strategy configuration. The
     * provided default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new mapping strategy configurator
     */
    static MappingStrategyConfigurator configure(final MappingStrategyConfig defaults) {
        return MappingStrategyConfigurator.configure(defaults);
    }

    /**
     * Returns the mapping strategy config system defaults.
     * @return the default mapping strategy config
     * @see org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults#MAPPING_STRATEGY_CONFIG_DEFAULTS
     */
    static MappingStrategyConfig getDefault() {
        return MAPPING_STRATEGY_CONFIG_DEFAULTS;
    }

    /**
     * Returns the system default config for the synchronous mapping strategy
     * @return the default mapping strategy config for synchronous mapping
     * @see org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults#MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS
     */
    static MappingStrategyConfig getDefaultSync() {
        return MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS;
    }

    /**
     * Returns the system default config for the synchronous mapping strategy with asynchronous unmapping
     * @return the default mapping strategy config for synchronous mapping with asynchronous unmapping
     * @see org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults#MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS
     */
    static MappingStrategyConfig getDefaultSyncWithAsyncUnmapping() {
        return MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS;
    }

    /**
     * Returns the system default config for the asynchronous map-ahead strategy
     * @return the default mapping strategy config for asynchronous ahead mapping
     * @see org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults#MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS
     */
    static MappingStrategyConfig getDefaultAsyncMapAhead() {
        return MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS;
    }
}
