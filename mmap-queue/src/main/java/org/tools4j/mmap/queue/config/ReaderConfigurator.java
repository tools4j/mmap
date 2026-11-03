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

import org.tools4j.mmap.queue.impl.ReaderConfiguratorImpl;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

/**
 * Configurator to build a {@link ReaderConfig} for a poller, entry reader or entry iterator; values not set explicitly
 * are taken from the defaults the configurator was created with.
 */
public interface ReaderConfigurator extends ReaderConfig {
    /**
     * Sets the mapping strategy for the header and payload files to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    ReaderConfigurator mappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the header and payload files.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    ReaderConfigurator mappingStrategy(MappingStrategyConfig config);

    /**
     * Configures the mapping strategy for the header and payload files, usually provided in lambda-format, for
     * instance:
     * <pre>{@code
     * config.mappingStrategy(cfg -> cfg.cacheSize(16));
     * }</pre>
     * The consumer is applied to the header and to the payload mapping strategy separately, each time with a
     * configurator starting from the current strategy of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategy
     * @return this configurator for method chaining
     */
    ReaderConfigurator mappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the header file to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    ReaderConfigurator headerMappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the header file.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    ReaderConfigurator headerMappingStrategy(MappingStrategyConfig config);

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
    ReaderConfigurator headerMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the payload files to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    ReaderConfigurator payloadMappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the payload files.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    ReaderConfigurator payloadMappingStrategy(MappingStrategyConfig config);

    /**
     * Configures the mapping strategy for the payload files, usually provided in lambda-format, for instance:
     * <pre>{@code
     * config.payloadMappingStrategy(cfg -> cfg.cacheSize(16));
     * }</pre>
     * The configurator passed to the consumer starts from the current payload mapping strategy of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategy
     * @return this configurator for method chaining
     */
    ReaderConfigurator payloadMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the maximum number of header files kept open at once if header files are rolled.
     *
     * @param maxOpenHeaderFiles the maximum number of header files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    ReaderConfigurator maxOpenHeaderFiles(int maxOpenHeaderFiles);

    /**
     * Sets the maximum number of payload files kept open at once if payload files are rolled.
     *
     * @param maxOpenPayloadFiles the maximum number of payload files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    ReaderConfigurator maxOpenPayloadFiles(int maxOpenPayloadFiles);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this
     * configurator was created with.
     *
     * @return this configurator for method chaining
     */
    ReaderConfigurator reset();

    /**
     * Returns a new configurator to build a poller configuration, with values not set explicitly taken from the
     * {@link ReaderConfig#getDefaultPollerConfig() default poller configuration}.
     *
     * @return a new configurator
     */
    static ReaderConfigurator configurePoller() {
        return ReaderConfiguratorImpl.createPollerConfigurator();
    }

    /**
     * Returns a new configurator to build an entry reader configuration, with values not set explicitly taken from the
     * {@link ReaderConfig#getDefaultEntryReaderConfig() default entry reader configuration}.
     *
     * @return a new configurator
     */
    static ReaderConfigurator configureEntryReader() {
        return ReaderConfiguratorImpl.createEntryReaderConfigurator();
    }

    /**
     * Returns a new configurator to build an entry iterator configuration, with values not set explicitly taken from
     * the {@link ReaderConfig#getDefaultEntryIteratorConfig() default entry iterator configuration}.
     *
     * @return a new configurator
     */
    static ReaderConfigurator configureEntryIterator() {
        return ReaderConfiguratorImpl.createEntryIteratorConfigurator();
    }

    /**
     * Returns a new configurator with values not set explicitly taken from the given defaults. The defaults must
     * provide valid values: there is no further fallback, and reading a value that is neither set explicitly nor
     * provided by the defaults throws an {@link IllegalStateException}.
     *
     * @param defaults the configuration providing values not set explicitly
     * @return a new configurator
     */
    static ReaderConfigurator configure(final ReaderConfig defaults) {
        return ReaderConfiguratorImpl.createConfigurator(defaults);
    }
}
