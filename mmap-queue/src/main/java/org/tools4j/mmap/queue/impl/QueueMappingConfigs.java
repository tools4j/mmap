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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.config.AppenderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.impl.MappingConfigImpl;

import static java.util.Objects.requireNonNull;

enum QueueMappingConfigs {
    ;

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final AppenderConfig appenderConfig) {
        return headerMappingConfig(queueConfig, appenderConfig.headerMappingStrategy(), true);
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final ReaderConfig readerConfig) {
        return headerMappingConfig(queueConfig, readerConfig.headerMappingStrategy(), readerConfig.closeHeaderFiles());
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig, final IndexReaderConfig indexReaderConfig) {
        return headerMappingConfig(queueConfig, indexReaderConfig.headerMappingStrategy(), indexReaderConfig.closeHeaderFiles());
    }

    static MappingConfig headerMappingConfig(final QueueConfig queueConfig,
                                             final MappingStrategy mappingStrategy,
                                             final boolean closeHeaderFiles) {
        requireNonNull(queueConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
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
            public boolean closeFiles() {
                return closeHeaderFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return queueConfig.headerFilesToCreateAhead();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig immutableConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableConfig ? this
                        : headerMappingConfig(immutableConfig, mappingStrategy, closeHeaderFiles);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("HeaderMappingConfig", this);
            }
        };
    }
    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig, final AppenderConfig appenderConfig) {
        return headerMappingConfig(queueConfig, appenderConfig.payloadMappingStrategy(), true);
    }

    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig, final ReaderConfig readerConfig) {
        return headerMappingConfig(queueConfig, readerConfig.payloadMappingStrategy(), readerConfig.closePayloadFiles());
    }

    static MappingConfig payloadMappingConfig(final QueueConfig queueConfig,
                                              final MappingStrategy mappingStrategy,
                                              final boolean closePayloadFiles) {
        requireNonNull(queueConfig);
        requireNonNull(mappingStrategy);
        return new MappingConfig() {
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
            public boolean closeFiles() {
                return closePayloadFiles;
            }

            @Override
            public int filesToCreateAhead() {
                return queueConfig.payloadFilesToCreateAhead();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return mappingStrategy;
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig immutableConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableConfig ? this
                        : headerMappingConfig(immutableConfig, mappingStrategy, closePayloadFiles);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("PayloadMappingConfig", this);
            }
        };
    }
}
