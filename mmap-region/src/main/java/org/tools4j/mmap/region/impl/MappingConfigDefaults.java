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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.MappingConfigurations;
import org.tools4j.mmap.region.api.MappingStrategy;

import static org.tools4j.mmap.region.api.MappingConfigurations.defaultCloseFiles;
import static org.tools4j.mmap.region.api.MappingConfigurations.defaultExpandFile;
import static org.tools4j.mmap.region.api.MappingConfigurations.defaultFilesToCreateAhead;
import static org.tools4j.mmap.region.api.MappingConfigurations.defaultMappingStrategy;
import static org.tools4j.mmap.region.api.MappingConfigurations.defaultMaxFileSize;
import static org.tools4j.mmap.region.api.MappingConfigurations.defaultRollFiles;

/**
 * Configuration taking values from {@link MappingConfigurations}.
 */
public enum MappingConfigDefaults implements MappingConfig {
    MAPPING_CONFIG_DEFAULTS;

    @Override
    public MappingConfig toImmutableMappingConfig() {
        return new MappingConfigImpl(this);
    }

    @Override
    public long maxFileSize() {
        return defaultMaxFileSize();
    }

    @Override
    public boolean expandFile() {
        return defaultExpandFile();
    }

    @Override
    public boolean rollFiles() {
        return defaultRollFiles();
    }

    @Override
    public boolean closeFiles() {
        return defaultCloseFiles();
    }

    @Override
    public int filesToCreateAhead() {
        return defaultFilesToCreateAhead();
    }

    @Override
    public MappingStrategy mappingStrategy() {
        return defaultMappingStrategy();
    }


    @Override
    public String toString() {
        return "MappingConfigDefaults" +
                ":maxFileSize=" + maxFileSize() +
                "|expandFile=" + expandFile() +
                "|rollFiles=" + rollFiles() +
                "|closeFiles=" + closeFiles() +
                "|filesToCreateAhead=" + filesToCreateAhead() +
                "|mappingStrategy=" + mappingStrategy();
    }
}
