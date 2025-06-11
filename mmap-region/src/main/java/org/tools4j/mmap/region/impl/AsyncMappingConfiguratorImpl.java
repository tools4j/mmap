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

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.region.api.AsyncRuntime;
import org.tools4j.mmap.region.api.AsyncRuntimeInstances;
import org.tools4j.mmap.region.config.AsyncMappingConfig;
import org.tools4j.mmap.region.config.AsyncMappingConfigurator;
import org.tools4j.mmap.region.config.SharingPolicy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.AsyncRuntimeInstances.newMappingRuntimeInstance;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultAheadMappingCacheSize;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultMappingAsyncRuntimeSupplier;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultRegionsToMapAhead;
import static org.tools4j.mmap.region.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateAheadMappingCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionsToMapAhead;

public class AsyncMappingConfiguratorImpl implements AsyncMappingConfigurator {

    private final AsyncMappingConfig defaults;
    private int regionsToMapAhead;
    private int aheadMappingCacheSize;
    private Supplier<? extends AsyncRuntime> mappingRuntimeSupplier;
    private static final AtomicInteger mapperCounter = new AtomicInteger();

    public AsyncMappingConfiguratorImpl() {
        this(ASYNC_MAPPING_CONFIG_DEFAULTS);
    }

    public AsyncMappingConfiguratorImpl(final AsyncMappingConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public AsyncMappingConfigurator reset() {
        regionsToMapAhead = 0;
        aheadMappingCacheSize = 0;
        mappingRuntimeSupplier = null;
        return this;
    }

    @Override
    public int regionsToMapAhead() {
        if (regionsToMapAhead <= 0) {
            regionsToMapAhead = defaults.regionsToMapAhead();
        }
        if (regionsToMapAhead <= 0) {
            regionsToMapAhead = defaultRegionsToMapAhead();
        }
        return regionsToMapAhead;
    }

    @Override
    public AsyncMappingConfigurator regionsToMapAhead(final int regionsToMapAhead) {
        validateRegionsToMapAhead(regionsToMapAhead);
        this.regionsToMapAhead = regionsToMapAhead;
        return this;
    }

    @Override
    public int aheadMappingCacheSize() {
        if (aheadMappingCacheSize <= 0) {
            aheadMappingCacheSize = defaults.aheadMappingCacheSize();
        }
        if (aheadMappingCacheSize <= 0) {
            aheadMappingCacheSize = defaultAheadMappingCacheSize();
        }
        return aheadMappingCacheSize;
    }

    @Override
    public AsyncMappingConfigurator aheadMappingCacheSize(final int cacheSize) {
        validateAheadMappingCacheSize(cacheSize);
        this.aheadMappingCacheSize = cacheSize;
        return this;
    }

    @Override
    public Supplier<? extends AsyncRuntime> mappingRuntimeSupplier() {
        if (mappingRuntimeSupplier == null) {
            mappingRuntimeSupplier = defaults.mappingRuntimeSupplier();
        }
        if (mappingRuntimeSupplier == null) {
            mappingRuntimeSupplier = defaultMappingAsyncRuntimeSupplier();
        }
        return mappingRuntimeSupplier;
    }

    @Override
    public AsyncMappingConfigurator mappingRuntime(final AsyncRuntime mappingRuntime) {
        requireNonNull(mappingRuntime);
        return mappingRuntimeSupplier(() -> mappingRuntime);
    }

    @Override
    public AsyncMappingConfigurator mappingRuntimeShared(final SharingPolicy sharingPolicy) {
        return mappingRuntimeSupplier(AsyncRuntimeInstances.mappingRuntimeSupplier(sharingPolicy));
    }

    @Override
    public AsyncMappingConfigurator mappingRuntimeSupplier(final Supplier<? extends AsyncRuntime> mappingRuntimeSupplier) {
        this.mappingRuntimeSupplier = requireNonNull(mappingRuntimeSupplier);
        return this;
    }

    @Override
    public AsyncMappingConfigurator mappingRuntimeSupplier(final IdleStrategy idleStrategy) {
        requireNonNull(idleStrategy);
        return mappingRuntimeSupplierUsing(() -> idleStrategy);
    }

    @Override
    public AsyncMappingConfigurator mappingRuntimeSupplierUsing(final Supplier<? extends IdleStrategy> idleStrategy) {
        requireNonNull(idleStrategy);
        return mappingRuntimeSupplier(() -> AsyncRuntime.create(
                "mapper-" + mapperCounter.incrementAndGet(), idleStrategy.get(), true));
    }

    @Override
    public AsyncMappingConfig toImmutableAsyncMappingConfig() {
        return new AsyncMappingConfigImpl(this);
    }

    @Override
    public String toString() {
        return "AsyncMappingConfiguratorImpl" +
                ":regionsToMapAhead=" + regionsToMapAhead +
                "|aheadMappingCacheSize=" + aheadMappingCacheSize +
                "|defaults=" + defaults;
    }
}
