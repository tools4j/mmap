/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.config.MappingStrategy.AsyncOptions;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultMappingAsyncRuntime;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultUnmappingAsyncRuntime;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionsToMapAhead;

public class AheadMappingStrategy implements MappingStrategy, AsyncOptions {
    public static final String NAME = "AheadMappingStrategy";

    private final int regionSize;
    private final int cacheSize;
    private final int regionsToMapAhead;
    private final AsyncRuntime mappingRuntime;
    private final AsyncRuntime unmappingRuntime;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<AsyncOptions> asyncOptions = Optional.of(this);

    public AheadMappingStrategy(final int regionSize, final int cacheSize) {
        this(regionSize, cacheSize, cacheSize <= 1 ? cacheSize : cacheSize / 2);
    }

    public AheadMappingStrategy(final int regionSize, final int cacheSize, final int regionsToMapAhead) {
        this(regionSize, cacheSize, regionsToMapAhead, defaultMappingAsyncRuntime(), defaultUnmappingAsyncRuntime());
    }

    public AheadMappingStrategy(final int regionSize,
                                final int cacheSize,
                                final int regionsToMapAhead,
                                final AsyncRuntime mappingRuntime,
                                final AsyncRuntime unmappingRuntime) {
        validateRegionSize(cacheSize);
        validateRegionCacheSize(cacheSize);
        validateRegionsToMapAhead(regionsToMapAhead);
        requireNonNull(mappingRuntime);
        requireNonNull(unmappingRuntime);
        this.regionSize = regionSize;
        this.cacheSize = cacheSize;
        this.regionsToMapAhead = regionsToMapAhead;
        this.mappingRuntime = mappingRuntime;
        this.unmappingRuntime = unmappingRuntime;
    }

    public static MappingStrategy getDefault() {
        return MappingConfigurations.defaultAheadMappingStrategy();
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
    public AsyncRuntime mappingRuntime() {
        return mappingRuntime;
    }

    @Override
    public AsyncRuntime unmappingRuntime() {
        return unmappingRuntime;
    }

    @Override
    public Optional<AsyncOptions> asyncOptions() {
        return asyncOptions;
    }

    @Override
    public String toString() {
        return "AheadMappingStrategy" +
                ":regionSize=" + regionSize +
                "|cacheSize=" + cacheSize +
                "|regionsToMapAhead=" + regionsToMapAhead +
                "|mappingRuntime=" + mappingRuntime +
                "|unmappingRuntime=" + unmappingRuntime;
    }
}
