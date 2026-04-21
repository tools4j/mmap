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

import static org.tools4j.mmap.region.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;

/**
 * Configuration used to create {@link DynamicMapping dynamic mappings} from files through {@link Mappings}.
 */
public interface MappingConfig {
    /** @return the maximum size of the mapped file */
    long maxFileSize();
    /** @return true if files should be expanded as needed, and false to create the full-size file on initiation */
    boolean expandFile();
    /** @return true if files should be rolled (with indexation) when the {@linkplain #maxFileSize() maximum file size} is reached */
    boolean rollFiles();

    /** @return if true and {@linkplain #rollFiles() file rolling} is used, files are closed after unmapping the last region of the file */
    boolean closeFiles();

    /** @return the number of files to create ahead, that is, before they are actually used for mappings */
    int filesToCreateAhead();

    /** @return the mapping strategy to use */
    MappingStrategyConfig mappingStrategy();

    /** @return an immutable version of this mapping config, for instance useful if this is a {@link MappingConfigurator}*/
    MappingConfig toImmutableConfig();

    /**
     * Creates and returns a new configurator instance that allows customization of mapping configuration. System
     * defaults are used where no custom configuration is provided.
     *
     * @return a new mapping configurator
     * @see #getDefault()
     */
    static MappingConfigurator configure() {
        return MappingConfigurator.configure();
    }

    /**
     * Creates and returns a new configurator instance that allows customization of mapping configuration. The provided
     * default configuration values are used where no custom configuration is provided.
     *
     * @param defaults the default configuration values to use if no custom override is made
     * @return a new mapping configurator
     */
    static MappingConfigurator configure(final MappingConfig defaults) {
        return MappingConfigurator.configure(defaults);
    }

    /**
     * Returns the mapping config system defaults.
     * @return the default mapping config
     * @see org.tools4j.mmap.region.impl.MappingConfigDefaults
     */
    static MappingConfig getDefault() {
        return MAPPING_CONFIG_DEFAULTS;
    }
}
