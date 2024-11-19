/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.RegionMapping;

/**
 * Configuration used to create {@link RegionMapping region mappings} from files through {@link Mappings}.
 */
public interface MappingConfig {
    long maxFileSize();
    boolean expandFile();
    boolean rollFiles();

    /** @return in {@link #rollFiles()} mode, close files after unmapping the last region of a file */
    boolean closeFiles();
    int filesToCreateAhead();
    MappingStrategy mappingStrategy();

    MappingConfig toImmutableMappingConfig();

    static MappingConfigurator configure() {
        return MappingConfigurator.create();
    }

    static MappingConfigurator configure(final MappingConfig defaults) {
        return MappingConfigurator.create(defaults);
    }

    static MappingConfig getDefault() {
        return MappingConfigurations.defaultMappingConfig();
    }
}