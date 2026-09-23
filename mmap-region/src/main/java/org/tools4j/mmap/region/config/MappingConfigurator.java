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
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.RegionMapping;
import org.tools4j.mmap.region.impl.MappingConfiguratorImpl;

import java.util.function.Consumer;

/**
 * Configurator to build a {@link MappingConfig} used to create {@link DynamicMapping dynamic mappings} from files
 * through {@link Mappings}.
 */
public interface MappingConfigurator extends MappingConfig {
    /**
     * Sets the minimum size (and incremental size) of the file, applicable only if
     * {@linkplain #expandFile() expand-file} mode is in use. The minimum file size must be a multiple of the mapping
     * strategy's {@linkplain MappingStrategyConfigurator#regionSize() region size}, or zero in which case the file is
     * expanded in region-size increments.
     *
     * @param minFileSize the minimum file size in bytes, also used as minimum file size increments; minimum size zero
     *                    implies region-sized increments
     * @return this configurator for method chaining
     * @see #expandFile(boolean) 
     * @see #maxFileSize(long)
     */
    MappingConfigurator minFileSize(long minFileSize);

    /**
     * Sets the maximum size of a file newly created when mapped. The maximum file size must be a multiple of the
     * mapping strategy's {@linkplain MappingStrategyConfigurator#regionSize() region size}. If
     * {@linkplain #expandFile() expand-file} mode is in use, the maximum file size must also be a multiple of
     * {@link #minFileSize() minimum file size} unless that is set to zero (which implies region-sized increments).
     *
     * @param maxFileSize the maximum file size in bytes
     * @return this configurator for method chaining
     * @see #minFileSize(long) 
     * @see #expandFile(boolean)  
     */
    MappingConfigurator maxFileSize(long maxFileSize);

    /**
     * Sets the expand-file option, true if files should be expanded as needed, and false to create the full-size file
     * on initiation.
     * @param expandFile true if files should be expanded as needed, and false to create the max-size file on initiation
     * @return this configurator for method chaining
     * @see #minFileSize(long)
     * @see #maxFileSize(long)
     */
    MappingConfigurator expandFile(boolean expandFile);

    /**
     * Sets the roll-files option, true if files should be indexed and rolled when they reach the
     * {@linkplain #maxFileSize() maximum file size}.
     *
     * @param rollFiles true if files should be rolled when the maximum file size is reached
     * @return this configurator for method chaining
     * @see #maxFileSize(long)
     * @see #maxOpenFiles(int)
     */
    MappingConfigurator rollFiles(boolean rollFiles);

    /**
     * Sets the maximum number of open files, applicable only if {@linkplain #rollFiles() file rolling} is used.
     *
     * @param maxOpenFiles the maximum files to keep open in file rolling mode
     * @return this configurator for method chaining
     * @see #rollFiles(boolean)
     */
    MappingConfigurator maxOpenFiles(int maxOpenFiles);

    /**
     * Sets the number of files to create ahead, that is, before they are actually used for mappings.
     *
     * @param filesToCreateAhead the number of files to create ahead, zero to disable or a positive number to enable
     * @return this configurator for method chaining
     */
    MappingConfigurator filesToCreateAhead(int filesToCreateAhead);
    /**
     * Sets the mapping strategy configuration to use. Consider using {@link #configure(MappingConfig)} instead.
     *
     * @param config the mapping strategy configuration
     * @return this configurator for method chaining
     */
    MappingConfigurator mappingStrategy(MappingStrategyConfig config);
    /**
     * Configures the mapping strategy to use, usually provided in lambda-format:
     * <pre><code>
     * mappingConfig.mappingStrategy(cfg -> cfg.cacheSize(16).asyncMapping(true));
     * </code></pre>
     *
     * @param configurator a consumer for the configurator to customize strategy configuration
     * @return this configurator for method chaining
     */
    MappingConfigurator mappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Resets all previously customized values back to default values.
     *
     * @return this configurator for method chaining
     */
    MappingConfigurator reset();


    /**
     * Creates and returns a new configurator instance that allows customization of mapping configuration. System
     * defaults are used where no custom configuration is provided.
     *
     * @return a new mapping configurator
     * @see MappingConfig#getDefault()
     */
    static MappingConfigurator configure() {
        return new MappingConfiguratorImpl();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of mapping configuration. The provided
     * default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new mapping configurator
     */
    static MappingConfigurator configure(final MappingConfig defaults) {
        return new MappingConfiguratorImpl(defaults);
    }
}
