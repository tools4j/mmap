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

import org.agrona.concurrent.IdleStrategy;
import org.tools4j.mmap.mapping.api.AsyncRuntime;
import org.tools4j.mmap.mapping.impl.AsyncMappingConfiguratorImpl;

import java.util.function.Supplier;

/**
 * Configurator to build an {@link AsyncMappingConfig} for asynchronous ahead-mapping.
 * <p>
 * The {@code mappingRuntime..(..)} methods are alternative ways to define the
 * {@linkplain #mappingRuntimeSupplier() runtime supplier}; the last one invoked takes effect.
 */
public interface AsyncMappingConfigurator extends AsyncMappingConfig {
    /**
     * Sets the number of regions mapped ahead of the current region when sequential access is detected.
     *
     * @param regionsToMapAhead the number of regions to map ahead, at least one
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if regions to map ahead is less than one
     */
    AsyncMappingConfigurator regionsToMapAhead(int regionsToMapAhead);

    /**
     * Sets the size of the cache holding regions that were mapped ahead, or zero to use the smallest power of two
     * that is no less than {@link #regionsToMapAhead()}. A non-zero cache size must be no less than the number of
     * regions to map ahead, which is validated when a region mapper is created.
     *
     * @param cacheSize zero, or a power of two no less than {@link #regionsToMapAhead()}
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if cache size is negative, or positive but not a power of two
     */
    AsyncMappingConfigurator aheadMappingCacheSize(int cacheSize);

    /**
     * Sets the runtime to perform ahead-mapping operations for all region mappers created with this configuration.
     *
     * @param mappingRuntime the async mapping runtime
     * @return this configurator for method chaining
     */
    AsyncMappingConfigurator mappingRuntime(AsyncRuntime mappingRuntime);

    /**
     * Sets the runtime to perform ahead-mapping operations to an instance provided by
     * {@link org.tools4j.mmap.mapping.api.AsyncRuntimeInstances AsyncRuntimeInstances} according to the given sharing
     * policy.
     *
     * @param sharingPolicy the policy defining if and how the runtime is shared
     * @return this configurator for method chaining
     * @see org.tools4j.mmap.mapping.api.AsyncRuntimeInstances#mappingRuntimeSupplier(SharingPolicy)
     */
    AsyncMappingConfigurator mappingRuntime(SharingPolicy sharingPolicy);

    /**
     * Sets the supplier of the runtime to perform ahead-mapping operations, invoked once for every region mapper
     * created with this configuration.
     *
     * @param mappingRuntimeSupplier the supplier of the async mapping runtime
     * @return this configurator for method chaining
     */
    AsyncMappingConfigurator mappingRuntimeSupplier(Supplier<? extends AsyncRuntime> mappingRuntimeSupplier);

    /**
     * Configures a new runtime for every region mapper created with this configuration, all of them using the given
     * idle strategy instance. The runtimes stop automatically when their last region mapper is closed.
     * <p>
     * Note that the same idle strategy instance is used by all runtimes; use
     * {@link #mappingRuntimeUsing(Supplier)} instead for idle strategies that hold state.
     *
     * @param idleStrategy the idle strategy used by all runtimes
     * @return this configurator for method chaining
     */
    AsyncMappingConfigurator mappingRuntimeUsing(IdleStrategy idleStrategy);

    /**
     * Configures a new runtime for every region mapper created with this configuration, each with an idle strategy
     * obtained from the given supplier. The runtimes stop automatically when their last region mapper is closed.
     *
     * @param idleStrategy the supplier of an idle strategy for every new runtime
     * @return this configurator for method chaining
     */
    AsyncMappingConfigurator mappingRuntimeUsing(Supplier<? extends IdleStrategy> idleStrategy);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this configurator
     * was created with.
     *
     * @return this configurator for method chaining
     */
    AsyncMappingConfigurator reset();

    /**
     * Creates and returns a new configurator instance that allows customization of async mapping configuration.
     * System defaults are used where no custom configuration is provided.
     *
     * @return a new async mapping configurator
     * @see AsyncMappingConfig#getDefault()
     */
    static AsyncMappingConfigurator configure() {
        return new AsyncMappingConfiguratorImpl();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of async mapping configuration. The
     * provided default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new async mapping configurator
     */
    static AsyncMappingConfigurator configure(final AsyncMappingConfig defaults) {
        return new AsyncMappingConfiguratorImpl(defaults);
    }
}
