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

interface QueueHeaderConfig extends MappingConfig {
    QueueConfig queueConfig();

    @Override
    default long maxFileSize() {
        return queueConfig().maxHeaderFileSize();
    }

    @Override
    default boolean expandFile() {
        return queueConfig().expandHeaderFile();
    }

    @Override
    default boolean rollFiles() {
        return queueConfig().rollHeaderFile();
    }

    @Override
    default int filesToCreateAhead() {
        return queueConfig().headerFilesToCreateAhead();
    }

    static MappingConfig pollerHeaderConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueueHeaderConfig() {
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
                return queueConfig.pollerHeaderMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : pollerHeaderConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueuePollerHeaderConfig", this);
            }
        };
    }

    static MappingConfig readerHeaderConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueueHeaderConfig() {
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
                return queueConfig.readerHeaderMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : readerHeaderConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueueReaderHeaderConfig", this);
            }
        };
    }

    static MappingConfig appenderHeaderConfig(final QueueConfig queueConfig) {
        requireNonNull(queueConfig);
        return new QueueHeaderConfig() {
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
                return queueConfig.appenderHeaderMappingStrategy();
            }

            @Override
            public MappingConfig toImmutableMappingConfig() {
                final QueueConfig queueConfig = queueConfig();
                final QueueConfig immutableQueueConfig = queueConfig.toImmutableQueueConfig();
                return queueConfig == immutableQueueConfig ? this : appenderHeaderConfig(immutableQueueConfig);
            }

            @Override
            public String toString() {
                return MappingConfigImpl.toString("QueueAppenderHeaderConfig", this);
            }
        };
    }
}
