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
import org.tools4j.mmap.region.config.MappingStrategyConfig;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionLruCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

public record MappingStrategyConfigImpl(int regionSize, int cacheSize, int lruCacheSize, boolean deferUnmapping,
                                        Optional<AsyncMappingConfig> asyncMapping,
                                        Optional<AsyncUnmappingConfig> asyncUnmapping) implements MappingStrategyConfig {

    public MappingStrategyConfigImpl() {
        this(MAPPING_STRATEGY_CONFIG_DEFAULTS);
    }

    public MappingStrategyConfigImpl(final MappingStrategyConfig toCopy) {
        this(toCopy.regionSize(), toCopy.cacheSize(), toCopy.lruCacheSize(), toCopy.deferUnmapping(),
                toCopy.asyncMapping().map(AsyncMappingConfig::toImmutableConfig),
                toCopy.asyncUnmapping().map(AsyncUnmappingConfig::toImmutableConfig));
    }

    public MappingStrategyConfigImpl {
        validateRegionSize(regionSize);
        validateRegionCacheSize(cacheSize);
        validateRegionLruCacheSize(lruCacheSize);
        requireNonNull(asyncMapping);
        requireNonNull(asyncUnmapping);
    }

    @Override
    public MappingStrategyConfig toImmutableConfig() {
        return this;
    }

    @Override
    public String toString() {
        return toString("MappingStrategyConfigImpl", this);
    }

    public static String toString(final String name, final MappingStrategyConfig config) {
        return name +
                ":regionSize=" + config.regionSize() +
                "|cacheSize=" + config.cacheSize() +
                "|asyncMapping=" + config.asyncMapping().map(Object::toString).orElse("n/a") +
                "|asyncUnmapping=" + config.asyncUnmapping().map(Object::toString).orElse("n/a");
    }
}
