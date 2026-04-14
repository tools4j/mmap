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
import org.tools4j.mmap.region.config.AsyncUnmappingConfig;
import org.tools4j.mmap.region.config.AsyncUnmappingConfigurator;
import org.tools4j.mmap.region.config.SharingPolicy;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultMappingAsyncRuntimeSupplier;
import static org.tools4j.mmap.region.config.MappingConfigurations.defaultUnmappingCacheSize;
import static org.tools4j.mmap.region.impl.AsyncUnmappingConfigDefaults.ASYNC_UNMAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateUnmappingCacheSize;

public class AsyncUnmappingConfiguratorImpl implements AsyncUnmappingConfigurator {

    private final AsyncUnmappingConfig defaults;
    private int unmappingCacheSize;
    private Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier;
    private static final AtomicInteger unmapperCounter = new AtomicInteger();

    public AsyncUnmappingConfiguratorImpl() {
        this(ASYNC_UNMAPPING_CONFIG_DEFAULTS);
    }

    public AsyncUnmappingConfiguratorImpl(final AsyncUnmappingConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public AsyncUnmappingConfigurator reset() {
        unmappingCacheSize = 0;
        unmappingRuntimeSupplier = null;
        return this;
    }

    @Override
    public int unmappingCacheSize() {
        if (unmappingCacheSize <= 0) {
            unmappingCacheSize = defaults.unmappingCacheSize();
        }
        if (unmappingCacheSize <= 0) {
            unmappingCacheSize = defaultUnmappingCacheSize();
        }
        return unmappingCacheSize;
    }

    @Override
    public AsyncUnmappingConfigurator unmappingCacheSize(final int cacheSize) {
        validateUnmappingCacheSize(cacheSize);
        this.unmappingCacheSize = cacheSize;
        return this;
    }

    @Override
    public Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier() {
        if (unmappingRuntimeSupplier == null) {
            unmappingRuntimeSupplier = defaults.unmappingRuntimeSupplier();
        }
        if (unmappingRuntimeSupplier == null) {
            unmappingRuntimeSupplier = defaultMappingAsyncRuntimeSupplier();
        }
        return unmappingRuntimeSupplier;
    }

    @Override
    public AsyncUnmappingConfigurator unmappingRuntime(final AsyncRuntime unmappingRuntime) {
        requireNonNull(unmappingRuntime);
        return unmappingRuntimeSupplier(() -> unmappingRuntime);
    }

    @Override
    public AsyncUnmappingConfigurator unmappingRuntimeShared(final SharingPolicy sharingPolicy) {
        return unmappingRuntimeSupplier(AsyncRuntimeInstances.unmappingRuntimeSupplier(sharingPolicy));
    }

    @Override
    public AsyncUnmappingConfigurator unmappingRuntimeSupplier(final Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier) {
        this.unmappingRuntimeSupplier = requireNonNull(unmappingRuntimeSupplier);
        return this;
    }

    @Override
    public AsyncUnmappingConfigurator unmappingRuntimeSupplier(final IdleStrategy idleStrategy) {
        requireNonNull(idleStrategy);
        return unmappingRuntimeSupplierUsing(() -> idleStrategy);
    }

    @Override
    public AsyncUnmappingConfigurator unmappingRuntimeSupplierUsing(final Supplier<? extends IdleStrategy> idleStrategy) {
        requireNonNull(idleStrategy);
        return unmappingRuntimeSupplier(() -> AsyncRuntime.create(
                "unmapper-" + unmapperCounter.incrementAndGet(), idleStrategy.get(), true));
    }

    @Override
    public AsyncUnmappingConfig toImmutableConfig() {
        return new AsyncUnmappingConfigImpl(this);
    }

    @Override
    public String toString() {
        return "AsyncUnmappingConfiguratorImpl" +
                ":unmappingCacheSize=" + unmappingCacheSize +
                "|defaults=" + defaults;
    }
}
