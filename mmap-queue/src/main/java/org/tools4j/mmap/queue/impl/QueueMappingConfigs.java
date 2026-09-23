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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.impl.MappingConfigImpl;

import static java.util.Objects.requireNonNull;

enum QueueMappingConfigs {
    ;
    static final int MAX_OPEN_FILES = 4096;//FIXME

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final AppenderConfig appenderConfig) {
        return headerMappingConfig(queueConfig, appenderConfig.headerMappingStrategy(),
                appenderConfig.maxOpenHeaderFiles(), appenderConfig.headerFilesToCreateAhead());
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final ReaderConfig readerConfig) {
        return headerMappingConfig(queueConfig, readerConfig.headerMappingStrategy(), readerConfig.maxOpenHeaderFiles(), 0);
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final IndexReaderConfig indexReaderConfig) {
        return headerMappingConfig(queueConfig, indexReaderConfig.headerMappingStrategy(), indexReaderConfig.maxOpenHeaderFiles(), 0);
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig,
                                             final MappingStrategyConfig mappingStrategy,
                                             final int maxOpenFiles,
                                             final int filesToCreateAhead) {
        requireNonNull(queueConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
            @Override
            public long minFileSize() {
                return 0;//FIXME
            }

            @Override
            public long maxFileSize() {
                return queueConfig.maxHeaderFileSize();
            }

            @Override
            public boolean expandFile() {
                return queueConfig.expandHeaderFile();
            }

            @Override
            public boolean rollFiles() {
                return queueConfig.rollHeaderFile();
            }

            @Override
            public int maxOpenFiles() {
                return maxOpenFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return filesToCreateAhead;
            }

            @Override
            public MappingStrategyConfig mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableConfig() {
                final QueueConfig immutableConfig = queueConfig.toImmutableQueueConfig();
                final MappingStrategyConfig immutableStrategy = mappingStrategy.toImmutableConfig();
                return queueConfig == immutableConfig && mappingStrategy == immutableStrategy ? this
                        : headerMappingConfig(immutableConfig, immutableStrategy, maxOpenFiles, filesToCreateAhead);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("HeaderMappingConfig", this);
            }
        };
    }
    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig, final AppenderConfig appenderConfig) {
        return payloadMappingConfig(queueConfig, appenderConfig.payloadMappingStrategy(),
                appenderConfig.maxOpenHeaderFiles(), appenderConfig.payloadFilesToCreateAhead());
    }

    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig, final ReaderConfig readerConfig) {
        return payloadMappingConfig(queueConfig, readerConfig.payloadMappingStrategy(), readerConfig.maxOpenPayloadFiles(), 0);
    }

    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig,
                                              final MappingStrategyConfig mappingStrategy,
                                              final int maxOpenFiles,
                                              final int filesToCreateAhead) {
        requireNonNull(queueConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
            @Override
            public long minFileSize() {
                return 0;//FIXME
            }

            @Override
            public long maxFileSize() {
                return queueConfig.maxPayloadFileSize();
            }

            @Override
            public boolean expandFile() {
                return queueConfig.expandPayloadFiles();
            }

            @Override
            public boolean rollFiles() {
                return queueConfig.rollPayloadFiles();
            }

            @Override
            public int maxOpenFiles() {
                return maxOpenFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return filesToCreateAhead;
            }

            @Override
            public MappingStrategyConfig mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableConfig() {
                final QueueConfig immutableConfig = queueConfig.toImmutableQueueConfig();
                final MappingStrategyConfig immutableStrategy = mappingStrategy.toImmutableConfig();
                return queueConfig == immutableConfig && mappingStrategy == immutableStrategy ? this
                        : payloadMappingConfig(immutableConfig, immutableStrategy, maxOpenFiles, filesToCreateAhead);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("PayloadMappingConfig", this);
            }
        };
    }
}
