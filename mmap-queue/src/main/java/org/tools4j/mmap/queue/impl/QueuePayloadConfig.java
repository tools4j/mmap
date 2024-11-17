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

import org.tools4j.mmap.queue.api.QueueConfig;
import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.MappingStrategy;
import org.tools4j.mmap.region.impl.MappingConfigImpl;

import static java.util.Objects.requireNonNull;

interface QueuePayloadConfig extends MappingConfig {
    QueueConfig queueConfig();

    @Override
    default long maxFileSize() {
        return queueConfig().maxPayloadFileSize();
    }

    @Override
    default boolean expandFile() {
        return queueConfig().expandPayloadFiles();
    }

    @Override
    default boolean rollFiles() {
        return queueConfig().rollPayloadFiles();
    }

    @Override
    default int filesToCreateAhead() {
        return queueConfig().payloadFilesToCreateAhead();
    }

    static MappingConfig pollerPayloadConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueuePayloadConfig() {
            @Override
            public QueueConfig queueConfig() {
                return queueConfig;
            }

            @Override
            public boolean closeFiles() {
                return queueConfig.closePollerFiles();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return queueConfig.pollerPayloadMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : pollerPayloadConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueuePollerPayloadConfig", this);
            }
        };
    }

    static MappingConfig readerPayloadConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueuePayloadConfig() {
            @Override
            public QueueConfig queueConfig() {
                return queueConfig;
            }

            @Override
            public boolean closeFiles() {
                return queueConfig.closeReaderFiles();
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return queueConfig.readerPayloadMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : readerPayloadConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueueReaderPayloadConfig", this);
            }
        };
    }

    static MappingConfig appenderPayloadConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueuePayloadConfig() {
            @Override
            public QueueConfig queueConfig() {
                return queueConfig;
            }

            @Override
            public boolean closeFiles() {
                return true;
            }

            @Override
            public MappingStrategy mappingStrategy() {
                return queueConfig.appenderPayloadMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : appenderPayloadConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueueAppenderPayloadConfig", this);
            }
        };
    }

}
