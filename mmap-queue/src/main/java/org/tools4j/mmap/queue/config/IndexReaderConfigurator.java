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
package org.tools4j.mmap.queue.config;

import org.tools4j.mmap.queue.impl.IndexReaderConfiguratorImpl;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

/**
 * Configurator to build an {@link IndexReaderConfig}; values not set explicitly are taken from the defaults the
 * configurator was created with.
 */
public interface IndexReaderConfigurator extends IndexReaderConfig {
    /**
     * Sets the mapping strategy for the header file to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    IndexReaderConfigurator headerMappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the header file.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    IndexReaderConfigurator headerMappingStrategy(MappingStrategyConfig config);

    /**
     * Configures the mapping strategy for the header file, usually provided in lambda-format, for instance:
     * <pre>{@code
     * config.headerMappingStrategy(cfg -> cfg.cacheSize(16));
     * }</pre>
     * The configurator passed to the consumer starts from the current header mapping strategy of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategy
     * @return this configurator for method chaining
     */
    IndexReaderConfigurator headerMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the maximum number of header files kept open at once if header files are rolled.
     *
     * @param maxOpenHeaderFiles the maximum number of header files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    IndexReaderConfigurator maxOpenHeaderFiles(int maxOpenHeaderFiles);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this
     * configurator was created with.
     *
     * @return this configurator for method chaining
     */
    IndexReaderConfigurator reset();

    /**
     * Returns a new configurator to build an index reader configuration, with values not set explicitly taken from the
     * {@link IndexReaderConfig#getDefault() default index reader configuration}.
     *
     * @return a new configurator
     */
    static IndexReaderConfigurator configure() {
        return new IndexReaderConfiguratorImpl();
    }

    /**
     * Returns a new configurator with values not set explicitly taken from the given defaults.
     *
     * @param defaults the configuration providing values not set explicitly
     * @return a new configurator
     */
    static IndexReaderConfigurator configure(final IndexReaderConfig defaults) {
        return new IndexReaderConfiguratorImpl(defaults);
    }
}
