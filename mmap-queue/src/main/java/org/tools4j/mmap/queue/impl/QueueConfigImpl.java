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

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;

public class QueueConfigImpl implements QueueConfig {
    private final long maxHeaderFileSize;
    private final long maxPayloadFileSize;
    private final boolean expandHeaderFile;
    private final boolean expandPayloadFiles;
    private final boolean rollHeaderFile;
    private final boolean rollPayloadFiles;
    private final int headerFilesToCreateAhead;
    private final int payloadFilesToCreateAhead;
    private final boolean closePollerFiles;
    private final boolean closeReaderFiles;

    private final MappingStrategy pollerHeaderMappingStrategy;
    private final MappingStrategy pollerPayloadMappingStrategy;
    private final MappingStrategy readerHeaderMappingStrategy;
    private final MappingStrategy readerPayloadMappingStrategy;
    private final MappingStrategy appenderHeaderMappingStrategy;
    private final MappingStrategy appenderPayloadMappingStrategy;
    private final MappingConfig pollerHeaderConfig = QueueHeaderConfig.pollerHeaderConfig(this);
    private final MappingConfig pollerPayloadConfig = QueuePayloadConfig.pollerPayloadConfig(this);
    private final MappingConfig readerHeaderConfig = QueueHeaderConfig.readerHeaderConfig(this);
    private final MappingConfig readerPayloadConfig = QueuePayloadConfig.readerPayloadConfig(this);
    private final MappingConfig appenderHeaderConfig = QueueHeaderConfig.appenderHeaderConfig(this);
    private final MappingConfig appenderPayloadConfig = QueuePayloadConfig.appenderPayloadConfig(this);

    public QueueConfigImpl() {
        this(QUEUE_CONFIG_DEFAULTS);
    }

    public QueueConfigImpl(final QueueConfig queueConfig) {
        this(queueConfig.maxHeaderFileSize(),
                queueConfig.maxPayloadFileSize(),
                queueConfig.expandHeaderFile(),
                queueConfig.expandPayloadFiles(),
                queueConfig.rollHeaderFile(),
                queueConfig.rollPayloadFiles(),
                queueConfig.headerFilesToCreateAhead(),
                queueConfig.payloadFilesToCreateAhead(),
                queueConfig.closePollerFiles(),
                queueConfig.closeReaderFiles(),
                queueConfig.pollerHeaderMappingStrategy(),
                queueConfig.pollerPayloadMappingStrategy(),
                queueConfig.readerHeaderMappingStrategy(),
                queueConfig.readerPayloadMappingStrategy(),
                queueConfig.appenderHeaderMappingStrategy(),
                queueConfig.appenderPayloadMappingStrategy());
    }

    public QueueConfigImpl(final long maxHeaderFileSize,
                           final long maxPayloadFileSize,
                           final boolean expandHeaderFile,
                           final boolean expandPayloadFiles,
                           final boolean rollHeaderFile,
                           final boolean rollPayloadFiles,
                           final int headerFilesToCreateAhead,
                           final int payloadFilesToCreateAhead,
                           final boolean closePollerFiles,
                           final boolean closeReaderFiles,
                           final MappingStrategy pollerHeaderMappingStrategy,
                           final MappingStrategy pollerPayloadMappingStrategy,
                           final MappingStrategy readerHeaderMappingStrategy,
                           final MappingStrategy readerPayloadMappingStrategy,
                           final MappingStrategy appenderHeaderMappingStrategy,
                           final MappingStrategy appenderPayloadMappingStrategy) {
        this.maxHeaderFileSize = maxHeaderFileSize;
        this.maxPayloadFileSize = maxPayloadFileSize;
        this.expandHeaderFile = expandHeaderFile;
        this.expandPayloadFiles = expandPayloadFiles;
        this.rollHeaderFile = rollHeaderFile;
        this.rollPayloadFiles = rollPayloadFiles;
        this.headerFilesToCreateAhead = headerFilesToCreateAhead;
        this.payloadFilesToCreateAhead = payloadFilesToCreateAhead;
        this.closePollerFiles = closePollerFiles;
        this.closeReaderFiles = closeReaderFiles;
        this.pollerHeaderMappingStrategy = requireNonNull(pollerHeaderMappingStrategy);
        this.pollerPayloadMappingStrategy = requireNonNull(pollerPayloadMappingStrategy);
        this.readerHeaderMappingStrategy = requireNonNull(readerHeaderMappingStrategy);
        this.readerPayloadMappingStrategy = requireNonNull(readerPayloadMappingStrategy);
        this.appenderHeaderMappingStrategy = requireNonNull(appenderHeaderMappingStrategy);
        this.appenderPayloadMappingStrategy = requireNonNull(appenderPayloadMappingStrategy);
    }

    @Override
    public long maxHeaderFileSize() {
        return maxHeaderFileSize;
    }

    @Override
    public long maxPayloadFileSize() {
        return maxPayloadFileSize;
    }

    @Override
    public boolean expandHeaderFile() {
        return expandHeaderFile;
    }

    @Override
    public boolean expandPayloadFiles() {
        return expandPayloadFiles;
    }

    @Override
    public boolean rollHeaderFile() {
        return rollHeaderFile;
    }

    @Override
    public boolean rollPayloadFiles() {
        return rollPayloadFiles;
    }

    @Override
    public int headerFilesToCreateAhead() {
        return headerFilesToCreateAhead;
    }

    @Override
    public int payloadFilesToCreateAhead() {
        return payloadFilesToCreateAhead;
    }

    @Override
    public boolean closePollerFiles() {
        return closePollerFiles;
    }

    @Override
    public boolean closeReaderFiles() {
        return closeReaderFiles;
    }

    @Override
    public MappingStrategy pollerHeaderMappingStrategy() {
        return pollerHeaderMappingStrategy;
    }

    @Override
    public MappingStrategy pollerPayloadMappingStrategy() {
        return pollerPayloadMappingStrategy;
    }

    @Override
    public MappingStrategy readerHeaderMappingStrategy() {
        return readerHeaderMappingStrategy;
    }

    @Override
    public MappingStrategy readerPayloadMappingStrategy() {
        return readerPayloadMappingStrategy;
    }

    @Override
    public MappingStrategy appenderHeaderMappingStrategy() {
        return appenderHeaderMappingStrategy;
    }

    @Override
    public MappingStrategy appenderPayloadMappingStrategy() {
        return appenderPayloadMappingStrategy;
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
                ":maxHeaderFileSize=" + maxHeaderFileSize +
                "|maxPayloadFileSize=" + maxPayloadFileSize +
                "|expandHeaderFile=" + expandHeaderFile +
                "|expandPayloadFiles=" + expandPayloadFiles +
                "|rollHeaderFile=" + rollHeaderFile +
                "|rollPayloadFiles=" + rollPayloadFiles +
                "|headerFilesToCreateAhead=" + headerFilesToCreateAhead +
                "|payloadFilesToCreateAhead=" + payloadFilesToCreateAhead +
                "|closePollerFiles=" + closePollerFiles +
                "|closeReaderFiles=" + closeReaderFiles +
                "|pollerHeaderMappingStrategy=" + pollerHeaderMappingStrategy +
                "|pollerPayloadMappingStrategy=" + pollerPayloadMappingStrategy +
                "|readerHeaderMappingStrategy=" + readerHeaderMappingStrategy +
                "|readerPayloadMappingStrategy=" + readerPayloadMappingStrategy +
                "|appenderHeaderMappingStrategy=" + appenderHeaderMappingStrategy +
                "|appenderPayloadMappingStrategy=" + appenderPayloadMappingStrategy;
    }
}
