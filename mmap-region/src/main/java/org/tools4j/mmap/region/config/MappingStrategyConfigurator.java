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
package org.tools4j.mmap.region.config;

import org.tools4j.mmap.region.api.DynamicMapping;
import org.tools4j.mmap.region.impl.MappingStrategyConfiguratorImpl;

import java.util.function.Consumer;

/**
 * Configurator to build a {@link MappingStrategyConfig} used to parameterize the mapping operations for
 * {@link DynamicMapping dynamic mappings}.
 */
public interface MappingStrategyConfigurator extends MappingStrategyConfig {
    /** @return the size of the region mapped into memory when an actual mapping operation occurs */
    /**
     * Sets the size of the region mapped into memory when an actual mapping operation occurs. Region sizes must be a
     * power of two and a multiple of the OS dependant
     * {@linkplain org.tools4j.mmap.region.impl.Constants#REGION_SIZE_GRANULARITY page size}.
     * Typical OS page sizes are 4K (x86, ARM), 16K (Mac M1, ARM64) or 64K, and typical region sizes are between 4K
     * and 4M.
     *
     * @param regionSize the region size in bytes
     * @return this configurator for method chaining
     */
    MappingStrategyConfigurator regionSize(int regionSize);
    MappingStrategyConfigurator cacheSize(int cacheSize);
    MappingStrategyConfigurator lruCacheSize(int cacheSize);
    MappingStrategyConfigurator deferUnmapping(boolean deferUnmapping);
    MappingStrategyConfigurator asyncMapping(boolean asyncMapping);
    MappingStrategyConfigurator asyncMapping(AsyncMappingConfig config);
    MappingStrategyConfigurator asyncMapping(Consumer<? super AsyncMappingConfigurator> configurator);
    MappingStrategyConfigurator asyncUnmapping(boolean asyncUnmapping);
    MappingStrategyConfigurator asyncUnmapping(AsyncUnmappingConfig config);
    MappingStrategyConfigurator asyncUnmapping(Consumer<? super AsyncUnmappingConfigurator> configurator);

    MappingStrategyConfigurator reset();

    static MappingStrategyConfigurator configure() {
        return new MappingStrategyConfiguratorImpl();
    }

    static MappingStrategyConfigurator configure(final MappingStrategyConfig defaults) {
        return new MappingStrategyConfiguratorImpl(defaults);
    }
}
