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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.config.MappingConfigurations;
import org.tools4j.mmap.region.config.MappingConfigurator;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxOpenFiles;
import static org.tools4j.mmap.region.impl.Constraints.validateMinFileSize;
import static org.tools4j.mmap.region.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.MappingStrategyConfigDefaults.MAPPING_STRATEGY_CONFIG_DEFAULTS;

public class MappingConfiguratorImpl implements MappingConfigurator {
    protected final MappingConfig defaults;
    protected long minFileSize;
    protected long maxFileSize;
    protected Boolean expandFile;
    protected Boolean rollFiles;
    protected int maxOpenFiles;
    protected int filesToCreateAhead;
    protected MappingStrategyConfig mappingStrategy;

    public MappingConfiguratorImpl() {
        this(MAPPING_CONFIG_DEFAULTS);
    }

    public MappingConfiguratorImpl(final MappingConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public MappingConfigurator reset() {
        this.minFileSize = -1;
        this.maxFileSize = 0;
        this.expandFile = null;
        this.rollFiles = null;
        this.maxOpenFiles = -1;
        this.filesToCreateAhead = -1;
        this.mappingStrategy = null;
        return this;
    }

    @Override
    public MappingConfig toImmutableConfig() {
        return new MappingConfigImpl(this);
    }

    @Override
    public long minFileSize() {
        if (minFileSize < 0) {
            minFileSize = defaults.minFileSize();
        }
        if (minFileSize < 0) {
            minFileSize = MappingConfigurations.defaultMinFileSize();
        }
        return minFileSize;
    }

    @Override
    public long maxFileSize() {
        if (maxFileSize <= 0) {
            maxFileSize = defaults.maxFileSize();
        }
        if (maxFileSize <= 0) {
            maxFileSize = MappingConfigurations.defaultMaxFileSize();
        }
        return maxFileSize;
    }

    @Override
    public boolean expandFile() {
        if (expandFile == null) {
            expandFile = defaults.expandFile();
        }
        return expandFile;
    }

    @Override
    public boolean rollFiles() {
        if (rollFiles == null) {
            rollFiles = defaults.rollFiles();
        }
        return rollFiles;
    }

    @Override
    public int maxOpenFiles() {
        if (maxOpenFiles < 0) {
            maxOpenFiles = defaults.maxOpenFiles();
        }
        if (maxOpenFiles < 0) {
            maxOpenFiles = defaults.maxOpenFiles();
        }
        return maxOpenFiles;
    }

    @Override
    public int filesToCreateAhead() {
        if (filesToCreateAhead < 0) {
            filesToCreateAhead = defaults.filesToCreateAhead();
        }
        if (filesToCreateAhead < 0) {
            filesToCreateAhead = MappingConfigurations.defaultFilesToCreateAhead();
        }
        return filesToCreateAhead;
    }

    @Override
    public MappingStrategyConfig mappingStrategy() {
        if (mappingStrategy == null) {
            mappingStrategy = defaults.mappingStrategy();
        }
        if (mappingStrategy == null) {
            mappingStrategy = MAPPING_STRATEGY_CONFIG_DEFAULTS;
        }
        return mappingStrategy;
    }

    @Override
    public MappingConfigurator minFileSize(final long minFileSize) {
        validateMinFileSize(minFileSize);
        this.minFileSize = minFileSize;
        return this;
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
    public MappingConfigurator maxOpenFiles(final int maxOpenFiles) {
        validateMaxOpenFiles(maxOpenFiles);
        this.maxOpenFiles = maxOpenFiles;
        return this;
    }

    @Override
    public MappingConfigurator filesToCreateAhead(final int filesToCreateAhead) {
        validateFilesToCreateAhead(filesToCreateAhead);
        this.filesToCreateAhead = filesToCreateAhead;
        return this;
    }

    @Override
    public MappingConfigurator mappingStrategy(final MappingStrategyConfig mappingStrategy) {
        this.mappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingConfigurator mappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = mappingStrategy != null
                ? MappingStrategyConfigurator.configure(mappingStrategy) : MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return mappingStrategy(config);
    }

    @Override
    public String toString() {
        return "MappingConfiguratorImpl" +
                ":minFileSize=" + minFileSize +
                "|maxFileSize=" + maxFileSize +
                "|expandFile=" + expandFile +
                "|rollFiles=" + rollFiles +
                "|maxOpenFiles=" + maxOpenFiles +
                "|filesToCreateAhead=" + filesToCreateAhead +
                "|mappingStrategy=" + mappingStrategy +
                "|defaults=" + defaults;
    }
}
