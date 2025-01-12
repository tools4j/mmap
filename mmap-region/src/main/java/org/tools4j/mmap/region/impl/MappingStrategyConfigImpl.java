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

import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.config.MappingStrategyConfig;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

public class MappingStrategyConfigImpl implements MappingStrategyConfig {

    private final int regionSize;
    private final int cacheSize;
    private final int regionsToMapAhead;
    private final Supplier<? extends AsyncRuntime> mappingAsyncRuntimeSupplier;
    private final Supplier<? extends AsyncRuntime> unmappingAsyncRuntimeSupplier;

    public MappingStrategyConfigImpl() {
        this(MAPPING_STRATEGY_CONFIG_DEFAULTS);
    }

    public MappingStrategyConfigImpl(final MappingStrategyConfig toCopy) {
        this(toCopy.regionSize(), toCopy.cacheSize(), toCopy.regionsToMapAhead(),
                toCopy.mappingAsyncRuntimeSupplier(), toCopy.unmappingAsyncRuntimeSupplier());
    }

    public MappingStrategyConfigImpl(final int regionSize,
                                     final int cacheSize,
                                     final int regionsToMapAhead,
                                     final Supplier<? extends AsyncRuntime> mappingAsyncRuntimeSupplier,
                                     final Supplier<? extends AsyncRuntime> unmappingAsyncRuntimeSupplier) {
        validateRegionSize(regionSize);
        validateRegionCacheSize(cacheSize);
        validateFilesToCreateAhead(regionsToMapAhead);
        requireNonNull(mappingAsyncRuntimeSupplier);
        requireNonNull(unmappingAsyncRuntimeSupplier);
        this.regionSize = regionSize;
        this.cacheSize = cacheSize;
        this.regionsToMapAhead = regionsToMapAhead;
        this.mappingAsyncRuntimeSupplier = mappingAsyncRuntimeSupplier;
        this.unmappingAsyncRuntimeSupplier = unmappingAsyncRuntimeSupplier;
    }

    @Override
    public MappingStrategyConfig toImmutableMappingStrategyConfig() {
        return this;
    }

    @Override
    public int regionSize() {
        return regionSize;
    }

    @Override
    public int cacheSize() {
        return cacheSize;
    }

    @Override
    public int regionsToMapAhead() {
        return regionsToMapAhead;
    }

    @Override
    public Supplier<? extends AsyncRuntime> mappingAsyncRuntimeSupplier() {
        return mappingAsyncRuntimeSupplier;
    }

    @Override
    public Supplier<? extends AsyncRuntime> unmappingAsyncRuntimeSupplier() {
        return unmappingAsyncRuntimeSupplier;
    }

    @Override
    public String toString() {
        return "MappingStrategyConfigImpl" +
                ":regionSize=" + regionSize() +
                "|cacheSize=" + cacheSize() +
                "|regionsToMapAhead=" + regionsToMapAhead();
    }
}
