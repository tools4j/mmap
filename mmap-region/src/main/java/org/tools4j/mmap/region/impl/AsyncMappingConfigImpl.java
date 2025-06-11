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

import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.config.AsyncMappingConfig;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateAheadMappingCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionsToMapAhead;

public record AsyncMappingConfigImpl(int regionsToMapAhead,
                                     int aheadMappingCacheSize,
                                     Supplier<? extends AsyncRuntime> mappingRuntimeSupplier) implements AsyncMappingConfig {
    public AsyncMappingConfigImpl() {
        this(ASYNC_MAPPING_CONFIG_DEFAULTS);
    }

    public AsyncMappingConfigImpl(final AsyncMappingConfig toCopy) {
        this(toCopy.regionsToMapAhead(), toCopy.aheadMappingCacheSize(), toCopy.mappingRuntimeSupplier());
    }

    public AsyncMappingConfigImpl {
        validateRegionsToMapAhead(regionsToMapAhead);
        validateAheadMappingCacheSize(aheadMappingCacheSize);
        requireNonNull(mappingRuntimeSupplier);
    }

    @Override
    public AsyncMappingConfig toImmutableAsyncMappingConfig() {
        return this;
    }

    @Override
    public String toString() {
        return toString("AsyncMappingConfigImpl", this);
    }

    public static String toString(final String name, final AsyncMappingConfig config) {
        return name +
                ":regionsToMapAhead=" + config.regionsToMapAhead() +
                "|aheadMappingCacheSize=" + config.aheadMappingCacheSize();
    }
}
