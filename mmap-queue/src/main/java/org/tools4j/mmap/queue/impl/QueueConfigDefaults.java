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

import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultAppenderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultAppenderPayloadMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultClosePollerFiles;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultCloseReaderFiles;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultExpandHeaderFile;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultExpandPayloadFiles;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultHeaderFilesToCreateAhead;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultMaxHeaderFileSize;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultMaxPayloadFileSize;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultPayloadFilesToCreateAhead;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultPollerHeaderMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultPollerPayloadMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultReaderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultReaderPayloadMappingStrategy;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultRollHeaderFile;
import static org.tools4j.mmap.queue.api.QueueConfigurations.defaultRollPayloadFiles;

public enum QueueConfigDefaults implements QueueConfig {
    QUEUE_CONFIG_DEFAULTS;
    private final MappingConfig pollerHeaderConfig = QueueHeaderConfig.pollerHeaderConfig(this);
    private final MappingConfig pollerPayloadConfig = QueuePayloadConfig.pollerPayloadConfig(this);
    private final MappingConfig readerHeaderConfig = QueueHeaderConfig.readerHeaderConfig(this);
    private final MappingConfig readerPayloadConfig = QueuePayloadConfig.readerPayloadConfig(this);
    private final MappingConfig appenderHeaderConfig = QueueHeaderConfig.appenderHeaderConfig(this);
    private final MappingConfig appenderPayloadConfig = QueuePayloadConfig.appenderPayloadConfig(this);

    @Override
    public long maxHeaderFileSize() {
        return defaultMaxHeaderFileSize();
    }

    @Override
    public long maxPayloadFileSize() {
        return defaultMaxPayloadFileSize();
    }

    @Override
    public boolean expandHeaderFile() {
        return defaultExpandHeaderFile();
    }

    @Override
    public boolean expandPayloadFiles() {
        return defaultExpandPayloadFiles();
    }

    @Override
    public boolean rollHeaderFile() {
        return defaultRollHeaderFile();
    }

    @Override
    public boolean rollPayloadFiles() {
        return defaultRollPayloadFiles();
    }

    @Override
    public int headerFilesToCreateAhead() {
        return defaultHeaderFilesToCreateAhead();
    }

    @Override
    public int payloadFilesToCreateAhead() {
        return defaultPayloadFilesToCreateAhead();
    }

    @Override
    public boolean closePollerFiles() {
        return defaultClosePollerFiles();
    }

    @Override
    public boolean closeReaderFiles() {
        return defaultCloseReaderFiles();
    }

    @Override
    public MappingStrategy pollerHeaderMappingStrategy() {
        return defaultPollerHeaderMappingStrategy();
    }

    @Override
    public MappingStrategy pollerPayloadMappingStrategy() {
        return defaultPollerPayloadMappingStrategy();
    }

    @Override
    public MappingStrategy readerHeaderMappingStrategy() {
        return defaultReaderHeaderMappingStrategy();
    }

    @Override
    public MappingStrategy readerPayloadMappingStrategy() {
        return defaultReaderPayloadMappingStrategy();
    }

    @Override
    public MappingStrategy appenderHeaderMappingStrategy() {
        return defaultAppenderHeaderMappingStrategy();
    }

    @Override
    public MappingStrategy appenderPayloadMappingStrategy() {
        return defaultAppenderPayloadMappingStrategy();
    }

    @Override
    public MappingConfig pollerHeaderConfig() {
        return pollerHeaderConfig;
    }

    @Override
    public MappingConfig pollerPayloadConfig() {
        return pollerPayloadConfig;
    }

    @Override
    public MappingConfig readerHeaderConfig() {
        return readerHeaderConfig;
    }

    @Override
    public MappingConfig readerPayloadConfig() {
        return readerPayloadConfig;
    }

    @Override
    public MappingConfig appenderHeaderConfig() {
        return appenderHeaderConfig;
    }

    @Override
    public MappingConfig appenderPayloadConfig() {
        return appenderPayloadConfig;
    }

    @Override
    public QueueConfig toImmutableQueueConfig() {
        return this;
    }

    @Override
    public String toString() {
        return "QueueConfigImpl" +
                ":maxHeaderFileSize=" + maxHeaderFileSize() +
                "|maxPayloadFileSize=" + maxPayloadFileSize() +
                "|expandHeaderFile=" + expandHeaderFile() +
                "|expandPayloadFiles=" + expandPayloadFiles() +
                "|rollHeaderFile=" + rollHeaderFile() +
                "|rollPayloadFiles=" + rollPayloadFiles() +
                "|headerFilesToCreateAhead=" + headerFilesToCreateAhead() +
                "|payloadFilesToCreateAhead=" + payloadFilesToCreateAhead() +
                "|closePollerFiles=" + closePollerFiles() +
                "|closeReaderFiles=" + closeReaderFiles() +
                "|pollerHeaderMappingStrategy=" + pollerHeaderMappingStrategy() +
                "|pollerPayloadMappingStrategy=" + pollerPayloadMappingStrategy() +
                "|readerHeaderMappingStrategy=" + readerHeaderMappingStrategy() +
                "|readerPayloadMappingStrategy=" + readerPayloadMappingStrategy() +
                "|appenderHeaderMappingStrategy=" + appenderHeaderMappingStrategy() +
                "|appenderPayloadMappingStrategy=" + appenderPayloadMappingStrategy();
    }
}
