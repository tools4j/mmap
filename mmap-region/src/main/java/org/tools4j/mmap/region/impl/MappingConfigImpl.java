/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.config.MappingStrategy;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;

public class MappingConfigImpl implements MappingConfig {
    private final long maxFileSize;
    private final boolean expandFile;
    private final boolean rollFiles;
    private final boolean closeFiles;
    private final int filesToCreateAhead;
    private final MappingStrategy mappingStrategy;

    public MappingConfigImpl() {
        this(MAPPING_CONFIG_DEFAULTS);
    }

    public MappingConfigImpl(final MappingConfig toCopy) {
        this(toCopy.maxFileSize(), toCopy.expandFile(), toCopy.rollFiles(), toCopy.closeFiles(),
                toCopy.filesToCreateAhead(), toCopy.mappingStrategy());
    }

    public MappingConfigImpl(final long maxFileSize,
                             final boolean expandFile,
                             final boolean rollFiles,
                             final boolean closeFiles,
                             final int filesToCreateAhead,
                             final MappingStrategy mappingStrategy) {
        validateMaxFileSize(maxFileSize);
        validateFilesToCreateAhead(filesToCreateAhead);
        requireNonNull(mappingStrategy);
        this.maxFileSize = maxFileSize;
        this.expandFile = expandFile;
        this.rollFiles = rollFiles;
        this.closeFiles = closeFiles;
        this.filesToCreateAhead = filesToCreateAhead;
        this.mappingStrategy = mappingStrategy;
    }

    @Override
    public MappingConfig toImmutableMappingConfig() {
        return this;
    }

    @Override
    public long maxFileSize() {
        return maxFileSize;
    }

    @Override
    public boolean expandFile() {
        return expandFile;
    }

    @Override
    public boolean rollFiles() {
        return rollFiles;
    }

    @Override
    public boolean closeFiles() {
        return closeFiles;
    }

    @Override
    public int filesToCreateAhead() {
        return filesToCreateAhead;
    }

    @Override
    public MappingStrategy mappingStrategy() {
        return mappingStrategy;
    }


    @Override
    public String toString() {
        return toString("MappingConfigImpl", this);
    }

    public static String toString(final String name, final MappingConfig config) {
        return name +
                ":maxFileSize=" + config.maxFileSize() +
                "|expandFile=" + config.expandFile() +
                "|rollFiles=" + config.rollFiles() +
                "|closeFiles=" + config.closeFiles() +
                "|filesToCreateAhead=" + config.filesToCreateAhead() +
                "|mappingStrategy=" + config.mappingStrategy();
    }
}
