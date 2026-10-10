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

import static org.tools4j.mmap.mapping.impl.AsyncUnmappingConfigDefaults.ASYNC_UNMAPPING_CONFIG_DEFAULTS;

/**
 * Configuration of asynchronous unmapping, used if a {@link MappingStrategyConfig mapping strategy} has
 * {@linkplain MappingStrategyConfig#asyncUnmapping() async unmapping} enabled. Unmap requests are queued and performed
 * by an {@link AsyncRuntime} on a background thread, so that the thread releasing a region does not have to wait for
 * the unmapping operation.
 */
public interface AsyncUnmappingConfig {
    /**
     * Returns the capacity of the queue for pending unmap requests. If the queue is full, regions are unmapped
     * synchronously by the requesting thread.
     *
     * @return the capacity of the unmap request queue, a power of two
     */
    int unmappingCacheSize();

    /**
     * Returns the supplier of the runtime that performs unmapping operations. The supplier is invoked once for every
     * region mapper created with this configuration.
     *
     * @return the supplier of the async unmapping runtime
     */
    Supplier<? extends AsyncRuntime> unmappingRuntimeSupplier();

    /**
     * Returns an immutable version of this async unmapping config.
     *
     * @return an immutable version of this async unmapping config, for instance useful if this is an
     *         {@link AsyncUnmappingConfigurator}
     */
    AsyncUnmappingConfig toImmutableConfig();

    /**
     * Creates and returns a new configurator instance that allows customization of async unmapping configuration.
     * System defaults are used where no custom configuration is provided.
     *
     * @return a new async unmapping configurator
     * @see #getDefault()
     */
    static AsyncUnmappingConfigurator configure() {
        return AsyncUnmappingConfigurator.configure();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of async unmapping configuration. The
     * provided default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new async unmapping configurator
     */
    static AsyncUnmappingConfigurator configure(final AsyncUnmappingConfig defaults) {
        return AsyncUnmappingConfigurator.configure(defaults);
    }

    /**
     * Returns the async unmapping config system defaults.
     *
     * @return the default async unmapping config
     * @see MappingConfigurations
     */
    static AsyncUnmappingConfig getDefault() {
        return ASYNC_UNMAPPING_CONFIG_DEFAULTS;
    }
}
