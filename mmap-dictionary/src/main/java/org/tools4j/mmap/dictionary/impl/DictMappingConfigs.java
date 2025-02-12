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
package org.tools4j.mmap.dictionary.impl;

import org.tools4j.mmap.dictionary.config.DictionaryConfig;
import org.tools4j.mmap.dictionary.config.ReaderConfig;
import org.tools4j.mmap.dictionary.config.UpdaterConfig;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.impl.MappingConfigImpl;

import static java.util.Objects.requireNonNull;

enum DictMappingConfigs {
    ;

    static MappingConfig headerMappingConfig(final DictionaryConfig dictionaryConfig, final UpdaterConfig updaterConfig) {
        return headerMappingConfig(dictionaryConfig, updaterConfig.headerMappingStrategy(), true);
    }

    static MappingConfig headerMappingConfig(final DictionaryConfig dictionaryConfig, final ReaderConfig readerConfig) {
        return headerMappingConfig(dictionaryConfig, readerConfig.headerMappingStrategy(), readerConfig.closeHeaderFiles());
    }

    static MappingConfig headerMappingConfig(final DictionaryConfig dictionaryConfig,
                                             final MappingStrategy mappingStrategy,
                                             final boolean closeHeaderFiles) {
        requireNonNull(dictionaryConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
            @Override
            public long maxFileSize() {
                return dictionaryConfig.maxHeaderFileSize();
            }

            @Override
            public boolean expandFile() {
                return dictionaryConfig.expandHeaderFile();
            }

            @Override
            public boolean rollFiles() {
                return dictionaryConfig.rollHeaderFile();
            }

            @Override
            public boolean closeFiles() {
                return closeHeaderFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return dictionaryConfig.headerFilesToCreateAhead();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final DictionaryConfig immutableConfig = dictionaryConfig.toImmutableDictionaryConfig();
                return dictionaryConfig == immutableConfig ? this
                        : headerMappingConfig(immutableConfig, mappingStrategy, closeHeaderFiles);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("HeaderMappingConfig", this);
            }
        };
    }
    static MappingConfig payloadMappingConfig(final DictionaryConfig dictionaryConfig, final UpdaterConfig appenderConfig) {
        return headerMappingConfig(dictionaryConfig, appenderConfig.payloadMappingStrategy(), true);
    }

    static MappingConfig payloadMappingConfig(final DictionaryConfig dictionaryConfig, final ReaderConfig readerConfig) {
        return headerMappingConfig(dictionaryConfig, readerConfig.payloadMappingStrategy(), readerConfig.closePayloadFiles());
    }

    static MappingConfig payloadMappingConfig(final DictionaryConfig dictionaryConfig,
                                              final MappingStrategy mappingStrategy,
                                              final boolean closePayloadFiles) {
        requireNonNull(dictionaryConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
            @Override
            public long maxFileSize() {
                return dictionaryConfig.maxPayloadFileSize();
            }

            @Override
            public boolean expandFile() {
                return dictionaryConfig.expandPayloadFiles();
            }

            @Override
            public boolean rollFiles() {
                return dictionaryConfig.rollPayloadFiles();
            }

            @Override
            public boolean closeFiles() {
                return closePayloadFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return dictionaryConfig.payloadFilesToCreateAhead();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final DictionaryConfig immutableConfig = dictionaryConfig.toImmutableDictionaryConfig();
                return dictionaryConfig == immutableConfig ? this
                        : headerMappingConfig(immutableConfig, mappingStrategy, closePayloadFiles);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("PayloadMappingConfig", this);
            }
        };
    }
}
