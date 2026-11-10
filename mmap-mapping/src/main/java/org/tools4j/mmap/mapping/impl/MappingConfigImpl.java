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
package org.tools4j.mmap.mapping.impl;

import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.impl.MappingConfigDefaults.MAPPING_CONFIG_DEFAULTS;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateFileSizes;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateFilesToCreateAhead;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMaxOpenFiles;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMinFileSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateRegionSize;


public record MappingConfigImpl(long minFileSize, long maxFileSize, int regionSize, boolean expandFile,
                                boolean rollFiles, int maxOpenFiles, int filesToCreateAhead,
                                FileInitialiser fileInitialiser, MappingStrategyConfig mappingStrategy,
                                LifecycleListener lifecycleListener, ErrorListener errorListener)
        implements MappingConfig {
    public MappingConfigImpl() {
        this(MAPPING_CONFIG_DEFAULTS);
    }

    public MappingConfigImpl(final MappingConfig toCopy) {
        this(toCopy.minFileSize(), toCopy.maxFileSize(), toCopy.regionSize(), toCopy.expandFile(),
                toCopy.rollFiles(), toCopy.maxOpenFiles(), toCopy.filesToCreateAhead(),
                toCopy.fileInitialiser(), toCopy.mappingStrategy().toImmutableConfig(), toCopy.lifecycleListener(),
                toCopy.errorListener());
    }

    public MappingConfigImpl {
        validateMinFileSize(minFileSize);
        validateMaxFileSize(maxFileSize);
        validateRegionSize(regionSize);
        validateMaxOpenFiles(maxOpenFiles);
        validateFilesToCreateAhead(filesToCreateAhead);
        requireNonNull(fileInitialiser);
        requireNonNull(mappingStrategy);
        requireNonNull(lifecycleListener);
        requireNonNull(errorListener);
        validateFileSizes(minFileSize, maxFileSize, expandFile, regionSize);
    }

    @Override
    public MappingConfig toImmutableConfig() {
        return this;
    }

    @Override
    public String toString() {
        return toString("MappingConfigImpl", this);
    }

    public static String toString(final String name, final MappingConfig config) {
        return name +
                ":minFileSize=" + config.minFileSize() +
                "|maxFileSize=" + config.maxFileSize() +
                "|regionSize=" + config.regionSize() +
                "|expandFile=" + config.expandFile() +
                "|rollFiles=" + config.rollFiles() +
                "|maxOpenFiles=" + config.maxOpenFiles() +
                "|filesToCreateAhead=" + config.filesToCreateAhead() +
                "|fileInitialiser=" + config.fileInitialiser() +
                "|mappingStrategy=" + config.mappingStrategy() +
                "|lifecycleListener=" + config.lifecycleListener() +
                "|errorListener=" + config.errorListener();
    }
}
