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
import org.tools4j.mmap.mapping.api.Mappings;

import static org.tools4j.mmap.mapping.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;

/**
 * Configuration used to create {@link DynamicMapping dynamic mappings} from files through {@link Mappings}.
 */
public interface MappingConfig {
    /**
     * Returns the minimum size (and size increment) of a file when {@link #expandFile() expand-file} mode is in use.
     *
     * @return the minimum size (and size increment) of file when {@link #expandFile() expand-file} mode is in use
     */
    long minFileSize();

    /**
     * Returns the maximum file size. Files are created with this size unless {@linkplain #expandFile() expand-file}
     * mode is in use, in which case files grow up to this size. If {@linkplain #rollFiles() file rolling} is used,
     * this is the size of every individual file, and the next file is used for positions beyond.
     *
     * @return the maximum file size in bytes, a power of two and a multiple of the region size
     */
    long maxFileSize();

    /**
     * Returns whether files should be expanded as needed, or created at maximum size upfront.
     *
     * @return true if files should be expanded as needed, and false to create the max-size file on initiation
     */
    boolean expandFile();

    /**
     * Returns whether files should be rolled (with indexation) when the maximum file size is reached.
     *
     * @return true if files should be rolled (with indexation) when the {@linkplain #maxFileSize() maximum file size}
     *         is reached
     */
    boolean rollFiles();

    /**
     * Returns the maximum number of files kept open at once if file rolling is used; files are closed on a
     * least-recently-used basis. The value must be no less than the number of threads mapping files, that is, one
     * plus one each for async mapping and async unmapping if used.
     *
     * @return the maximum files kept open if {@linkplain #rollFiles() file rolling} is used
     */
    int maxOpenFiles();

    /**
     * Returns the number of files to create ahead if file rolling is used, that is, before they are actually used
     * for mappings.
     *
     * @return the number of files to create ahead if {@linkplain #rollFiles() file rolling} is used, zero if none
     */
    int filesToCreateAhead();

    /**
     * Returns the mapping strategy to use.
     *
     * @return the mapping strategy to use
     */
    MappingStrategyConfig mappingStrategy();

    /**
     * Returns an immutable version of this mapping config.
     *
     * @return an immutable version of this mapping config, for instance useful if this is a {@link MappingConfigurator}
     */
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
     * @see org.tools4j.mmap.mapping.impl.MappingConfigDefaults
     */
    static MappingConfig getDefault() {
        return MAPPING_CONFIG_DEFAULTS;
    }
}
