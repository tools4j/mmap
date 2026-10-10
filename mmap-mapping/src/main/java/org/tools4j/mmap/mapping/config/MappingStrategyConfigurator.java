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

import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.impl.MappingStrategyConfiguratorImpl;

import java.util.function.Consumer;

/**
 * Configurator to build a {@link MappingStrategyConfig} used to parameterize the mapping operations for
 * {@link DynamicMapping dynamic mappings}.
 */
public interface MappingStrategyConfigurator extends MappingStrategyConfig {
    /**
     * Sets the size of the region mapped into memory when an actual mapping operation occurs. Region sizes must be a
     * power of two and a multiple of the OS dependant
     * {@linkplain org.tools4j.mmap.mapping.impl.Constants#REGION_SIZE_GRANULARITY page size}.
     * Typical OS page sizes are 4K (x86, ARM), 16K (Mac M1, ARM64) or 64K, and typical region sizes are between 4K
     * and 4M.
     *
     * @param regionSize the region size in bytes
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if region size is not a power of two or not a multiple of the OS page size
     */
    MappingStrategyConfigurator regionSize(int regionSize);

    /**
     * Sets the size of the ring cache keeping the most recently used regions mapped. If an
     * {@linkplain #lruCacheSize(int) LRU cache} is also used, the ring cache is placed in front of it.
     *
     * @param cacheSize the ring cache size, a power of two
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if cache size is not a power of two
     */
    MappingStrategyConfigurator cacheSize(int cacheSize);

    /**
     * Sets the size of the cache keeping regions mapped and evicting them on a least-recently-used (LRU) basis.
     *
     * @param cacheSize the LRU cache size, or zero for no LRU cache
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if cache size is negative
     */
    MappingStrategyConfigurator lruCacheSize(int cacheSize);

    /**
     * Sets whether unmapping operations are deferred until it becomes necessary, for instance due to cache eviction.
     *
     * @param deferUnmapping true to defer unmapping operations until they become necessary
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator deferUnmapping(boolean deferUnmapping);

    /**
     * Enables or disables asynchronous ahead-mapping. When enabling, the current async mapping configuration of this
     * configurator is kept if present (set previously or from the defaults this configurator was created with), and
     * system defaults are used otherwise.
     *
     * @param asyncMapping true to enable and false to disable async mapping
     * @return this configurator for method chaining
     * @see AsyncMappingConfig#getDefault()
     */
    MappingStrategyConfigurator asyncMapping(boolean asyncMapping);

    /**
     * Enables asynchronous ahead-mapping with the given configuration.
     *
     * @param config the async mapping configuration
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator asyncMapping(AsyncMappingConfig config);

    /**
     * Enables and configures asynchronous ahead-mapping, usually provided in lambda-format:
     * <pre><code>
     * strategyConfig.asyncMapping(cfg -&gt; cfg.regionsToMapAhead(4));
     * </code></pre>
     * The configurator passed to the consumer starts from the current async mapping configuration of this
     * configurator if present (set previously or from the defaults this configurator was created with), and from
     * system defaults otherwise.
     *
     * @param configurator a consumer for the configurator to customize async mapping configuration
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator asyncMapping(Consumer<? super AsyncMappingConfigurator> configurator);

    /**
     * Enables or disables asynchronous unmapping. When enabling, the current async unmapping configuration of this
     * configurator is kept if present (set previously or from the defaults this configurator was created with), and
     * system defaults are used otherwise.
     *
     * @param asyncUnmapping true to enable and false to disable async unmapping
     * @return this configurator for method chaining
     * @see AsyncUnmappingConfig#getDefault()
     */
    MappingStrategyConfigurator asyncUnmapping(boolean asyncUnmapping);

    /**
     * Enables asynchronous unmapping with the given configuration.
     *
     * @param config the async unmapping configuration
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator asyncUnmapping(AsyncUnmappingConfig config);

    /**
     * Enables and configures asynchronous unmapping, usually provided in lambda-format:
     * <pre><code>
     * strategyConfig.asyncUnmapping(cfg -&gt; cfg.unmappingCacheSize(64));
     * </code></pre>
     * The configurator passed to the consumer starts from the current async unmapping configuration of this
     * configurator if present (set previously or from the defaults this configurator was created with), and from
     * system defaults otherwise.
     *
     * @param configurator a consumer for the configurator to customize async unmapping configuration
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator asyncUnmapping(Consumer<? super AsyncUnmappingConfigurator> configurator);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this configurator
     * was created with.
     *
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator reset();

    /**
     * Creates and returns a new configurator instance that allows customization of mapping strategy configuration.
     * System defaults are used where no custom configuration is provided.
     *
     * @return a new mapping strategy configurator
     * @see MappingStrategyConfig#getDefault()
     */
    static MappingStrategyConfigurator configure() {
        return new MappingStrategyConfiguratorImpl();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of mapping strategy configuration. The
     * provided default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new mapping strategy configurator
     */
    static MappingStrategyConfigurator configure(final MappingStrategyConfig defaults) {
        return new MappingStrategyConfiguratorImpl(defaults);
    }
}
