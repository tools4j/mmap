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

import org.tools4j.mmap.region.api.DefaultValues;
import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.MappingConfigurator;
import org.tools4j.mmap.region.api.MappingStrategy;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;

public class MappingConfiguratorImpl implements MappingConfigurator {
    private long maxFileSize;
    private Boolean expandFile;
    private Boolean rollFiles;
    private int filesToCreateAhead = -1;
    private MappingStrategy mappingStrategy;

    @Override
    public MappingConfigurator reset() {
        maxFileSize = 0;
        expandFile = null;
        rollFiles = null;
        filesToCreateAhead = -1;
        mappingStrategy = null;
        return this;
    }

    @Override
    public MappingConfig immutable() {
        return new MappingConfigImpl(this);
    }

    @Override
    public long maxFileSize() {
        if (maxFileSize == 0) {
            maxFileSize = DefaultValues.defaultMaxFileSize();
        }
        return maxFileSize;
    }

    @Override
    public boolean expandFile() {
        if (expandFile == null) {
            expandFile = DefaultValues.defaultExpandFile();
        }
        return expandFile;
    }

    @Override
    public boolean rollFiles() {
        if (rollFiles == null) {
            rollFiles = DefaultValues.defaultRollFiles();
        }
        return rollFiles;
    }

    @Override
    public int filesToCreateAhead() {
        if (filesToCreateAhead < 0) {
            filesToCreateAhead = DefaultValues.defaultFilesToCreateAhead();
        }
        return filesToCreateAhead;
    }

    @Override
    public MappingStrategy mappingStrategy() {
        if (mappingStrategy == null) {
            mappingStrategy = DefaultValues.defaultMappingStrategy();
        }
        return mappingStrategy;
    }

    @Override
    public MappingConfigurator maxFileSize(final long maxFileSize) {
        validateMaxFileSize(maxFileSize);
        this.maxFileSize = maxFileSize;
        return this;
    }

    @Override
    public MappingConfigurator expandFile(final boolean expandFile) {
        this.expandFile = expandFile;
        return this;
    }

    @Override
    public MappingConfigurator rollFiles(final boolean rollFiles) {
        this.rollFiles = rollFiles;
        return this;
    }

    @Override
    public MappingConfigurator filesToCreateAhead(final int filesToCreateAhead) {
        validateFilesToCreateAhead(filesToCreateAhead);
        this.filesToCreateAhead = filesToCreateAhead;
        return this;
    }

    @Override
    public MappingConfigurator mappingStrategy(final MappingStrategy mappingStrategy) {
        this.mappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public String toString() {
        return "MappingConfiguratorImpl" +
                ":maxFileSize=" + maxFileSize +
                "|expandFile=" + expandFile +
                "|rollFiles=" + rollFiles +
                "|filesToCreateAhead=" + filesToCreateAhead +
                "|mappingStrategy=" + mappingStrategy;
    }
}