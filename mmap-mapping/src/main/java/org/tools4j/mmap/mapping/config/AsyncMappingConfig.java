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
package org.tools4j.mmap.mapping.config;

import org.tools4j.mmap.mapping.api.AsyncRuntime;

import java.util.function.Supplier;

import static org.tools4j.mmap.mapping.impl.AsyncMappingConfigDefaults.ASYNC_MAPPING_CONFIG_DEFAULTS;

/**
 * Configuration of asynchronous ahead-mapping, used if a {@link MappingStrategyConfig mapping strategy} has
 * {@linkplain MappingStrategyConfig#asyncMapping() async mapping} enabled. When sequential forward or backward access
 * is detected, upcoming regions are mapped ahead by an {@link AsyncRuntime} on a background thread, so that moving to
 * the next region does not have to wait for the mapping operation.
 */
public interface AsyncMappingConfig {
    /**
     * Returns the number of regions mapped ahead of the current region when sequential access is detected.
     *
     * @return the number of regions to map ahead, at least one
     */
    int regionsToMapAhead();

    /**
     * Returns the size of the cache holding regions that were mapped ahead, or zero to use the smallest power of two
     * that is no less than {@link #regionsToMapAhead()}.
     *
     * @return zero, or a power of two no less than {@link #regionsToMapAhead()}
     */
    int aheadMappingCacheSize();

    /**
     * Returns the supplier of the runtime that performs ahead-mapping operations. The supplier is invoked once for
     * every region mapper created with this configuration.
     *
     * @return the supplier of the async mapping runtime
     */
    Supplier<? extends AsyncRuntime> mappingRuntimeSupplier();

    /**
     * Returns an immutable version of this async mapping config.
     *
     * @return an immutable version of this async mapping config, for instance useful if this is an
     *         {@link AsyncMappingConfigurator}
     */
    AsyncMappingConfig toImmutableConfig();

    /**
     * Creates and returns a new configurator instance that allows customization of async mapping configuration.
     * System defaults are used where no custom configuration is provided.
     *
     * @return a new async mapping configurator
     * @see #getDefault()
     */
    static AsyncMappingConfigurator configure() {
        return AsyncMappingConfigurator.configure();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of async mapping configuration. The
     * provided default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new async mapping configurator
     */
    static AsyncMappingConfigurator configure(final AsyncMappingConfig defaults) {
        return AsyncMappingConfigurator.configure(defaults);
    }

    /**
     * Returns the async mapping config system defaults.
     *
     * @return the default async mapping config
     * @see MappingConfigurations
     */
    static AsyncMappingConfig getDefault() {
        return ASYNC_MAPPING_CONFIG_DEFAULTS;
    }
}
