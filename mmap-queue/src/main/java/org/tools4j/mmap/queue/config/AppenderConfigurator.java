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

import org.tools4j.mmap.queue.impl.AppenderConfiguratorImpl;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

/**
 * Configurator to build an {@link AppenderConfig}; values not set explicitly are taken from the defaults the
 * configurator was created with.
 */
public interface AppenderConfigurator extends AppenderConfig {
    /**
     * Sets the mapping strategy for the header and payload files to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    AppenderConfigurator mappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the header and payload files.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    AppenderConfigurator mappingStrategy(MappingStrategyConfig config);

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
    AppenderConfigurator mappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the header file to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    AppenderConfigurator headerMappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the header file.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    AppenderConfigurator headerMappingStrategy(MappingStrategyConfig config);

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
    AppenderConfigurator headerMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the payload files to the given predefined strategy.
     *
     * @param strategy the mapping strategy
     * @return this configurator for method chaining
     */
    AppenderConfigurator payloadMappingStrategy(MappingStrategy strategy);

    /**
     * Sets the mapping strategy configuration for the payload files.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    AppenderConfigurator payloadMappingStrategy(MappingStrategyConfig config);

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
    AppenderConfigurator payloadMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the maximum number of header files kept open at once if header files are rolled.
     *
     * @param maxOpenHeaderFiles the maximum number of header files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    AppenderConfigurator maxOpenHeaderFiles(int maxOpenHeaderFiles);

    /**
     * Sets the maximum number of payload files kept open at once if payload files are rolled.
     *
     * @param maxOpenPayloadFiles the maximum number of payload files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    AppenderConfigurator maxOpenPayloadFiles(int maxOpenPayloadFiles);

    /**
     * Sets the number of header files to create ahead if header files are rolled.
     *
     * @param headerFilesToCreateAhead the number of header files to create ahead, zero for none
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    AppenderConfigurator headerFilesToCreateAhead(int headerFilesToCreateAhead);

    /**
     * Sets the number of payload files to create ahead if payload files are rolled.
     *
     * @param payloadFilesToCreateAhead the number of payload files to create ahead, zero for none
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    AppenderConfigurator payloadFilesToCreateAhead(int payloadFilesToCreateAhead);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this
     * configurator was created with.
     *
     * @return this configurator for method chaining
     */
    AppenderConfigurator reset();

    /**
     * Returns a new configurator to build an appender configuration, with values not set explicitly taken from the
     * {@link AppenderConfig#getDefault() default appender configuration}.
     *
     * @return a new configurator
     */
    static AppenderConfigurator configure() {
        return new AppenderConfiguratorImpl();
    }

    /**
     * Returns a new configurator with values not set explicitly taken from the given defaults.
     *
     * @param defaults the configuration providing values not set explicitly
     * @return a new configurator
     */
    static AppenderConfigurator configure(final AppenderConfig defaults) {
        return new AppenderConfiguratorImpl(defaults);
    }
}
