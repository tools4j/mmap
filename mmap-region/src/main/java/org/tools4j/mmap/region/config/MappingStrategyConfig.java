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

import java.util.Optional;

import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS;

public interface MappingStrategyConfig {
    int regionSize();
    int cacheSize();
    int lruCacheSize();
    boolean deferUnmapping();

    Optional<AsyncMappingConfig> asyncMapping();
    Optional<AsyncUnmappingConfig> asyncUnmapping();

    MappingStrategyConfig toImmutableMappingStrategyConfig();

    static MappingStrategyConfigurator configure() {
        return MappingStrategyConfigurator.configure();
    }

    static MappingStrategyConfigurator configure(final MappingStrategyConfig defaults) {
        return MappingStrategyConfigurator.configure(defaults);
    }

    static MappingStrategyConfig getDefault() {
        return MAPPING_STRATEGY_CONFIG_DEFAULTS;
    }
    static MappingStrategyConfig getDefaultSync() {
        return MAPPING_STRATEGY_CONFIG_SYNC_DEFAULTS;
    }
    static MappingStrategyConfig getDefaultSyncWithAsyncUnmapping() {
        return MAPPING_STRATEGY_CONFIG_SYNC_WITH_ASYNC_UNMAPPING_DEFAULTS;
    }
    static MappingStrategyConfig getDefaultAsyncMapAhead() {
        return MAPPING_STRATEGY_CONFIG_ASYNC_MAP_AHEAD_DEFAULTS;
    }
}
