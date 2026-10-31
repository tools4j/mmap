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

import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

/**
 * Configuration of a queue {@link org.tools4j.mmap.queue.api.IndexReader IndexReader}, with mapping strategy and file
 * settings for the header file; index readers access no payload files.
 * <p>
 * The {@link #getDefault() default} configuration is defined through system properties, see
 * {@link QueueConfigurations}. Custom configurations are built with an {@link IndexReaderConfigurator} obtained
 * through {@link #configure()}.
 */
public interface IndexReaderConfig {
    /**
     * Returns the mapping strategy for the header file.
     *
     * @return the header mapping strategy
     */
    MappingStrategyConfig headerMappingStrategy();

    /**
     * Returns the maximum number of header files kept open at once if header files are rolled; files are closed on a
     * least-recently-used basis. The value must be no less than the number of threads mapping files, that is, one
     * plus one each for async mapping and async unmapping if used by the header mapping strategy.
     *
     * @return the maximum number of header files kept open, at least one
     */
    int maxOpenHeaderFiles();

    /**
     * Returns an immutable snapshot of this configuration, or this configuration itself if it is immutable already.
     *
     * @return an immutable index reader configuration with the values of this configuration
     */
    IndexReaderConfig toImmutableIndexReaderConfig();

    /**
     * Returns a new configurator to build an index reader configuration, with values not set explicitly taken from
     * the {@link #getDefault() default} configuration.
     *
     * @return a new index reader configurator
     */
    static IndexReaderConfigurator configure() {
        return IndexReaderConfigurator.configure();
    }

    /**
     * Returns the default index reader configuration, with values defined through system properties.
     *
     * @return the default index reader configuration
     * @see QueueConfigurations
     */
    static IndexReaderConfig getDefault() {
        return QueueConfigurations.defaultIndexReaderConfig();
    }
}
