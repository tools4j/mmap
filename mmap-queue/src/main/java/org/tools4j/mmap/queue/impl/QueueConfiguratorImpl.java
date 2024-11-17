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
import org.tools4j.mmap.queue.api.QueueConfigurations;
import org.tools4j.mmap.queue.api.QueueConfigurator;
import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.MappingStrategy;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;

public class QueueConfiguratorImpl implements QueueConfigurator {
    private final QueueConfig defaults;
    private long maxHeaderFileSize;
    private long maxPayloadFileSize;
    private Boolean expandHeaderFile;
    private Boolean expandPayloadFiles;
    private Boolean rollHeaderFile;
    private Boolean rollPayloadFiles;
    private int headerFilesToCreateAhead;
    private int payloadFilesToCreateAhead;
    private Boolean closePollerFiles;
    private Boolean closeReaderFiles;
    private MappingStrategy pollerHeaderMappingStrategy;
    private MappingStrategy pollerPayloadMappingStrategy;
    private MappingStrategy readerHeaderMappingStrategy;
    private MappingStrategy readerPayloadMappingStrategy;
    private MappingStrategy appenderHeaderMappingStrategy;
    private MappingStrategy appenderPayloadMappingStrategy;
    private final MappingConfig pollerHeaderConfig = QueueHeaderConfig.pollerHeaderConfig(this);
    private final MappingConfig pollerPayloadConfig = QueuePayloadConfig.pollerPayloadConfig(this);
    private final MappingConfig readerHeaderConfig = QueueHeaderConfig.readerHeaderConfig(this);
    private final MappingConfig readerPayloadConfig = QueuePayloadConfig.readerPayloadConfig(this);
    private final MappingConfig appenderHeaderConfig = QueueHeaderConfig.appenderHeaderConfig(this);
    private final MappingConfig appenderPayloadConfig = QueuePayloadConfig.appenderPayloadConfig(this);

    public QueueConfiguratorImpl() {
        this(QUEUE_CONFIG_DEFAULTS);
    }

    public QueueConfiguratorImpl(final QueueConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public QueueConfigurator reset() {
        maxHeaderFileSize = 0;
        maxPayloadFileSize = 0;
        expandHeaderFile = null;
        expandPayloadFiles = null;
        rollHeaderFile = null;
        rollPayloadFiles = null;
        headerFilesToCreateAhead = -1;
        payloadFilesToCreateAhead = -1;
        closePollerFiles = null;
        closeReaderFiles = null;
        pollerHeaderMappingStrategy = null;
        pollerPayloadMappingStrategy = null;
        readerHeaderMappingStrategy = null;
        readerPayloadMappingStrategy = null;
        appenderHeaderMappingStrategy = null;
        appenderPayloadMappingStrategy = null;
        return this;
    }

    @Override
    public long maxHeaderFileSize() {
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = defaults.maxHeaderFileSize();
        }
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = QueueConfigurations.defaultMaxHeaderFileSize();
        }
        return maxHeaderFileSize;
    }

    @Override
    public QueueConfigurator maxHeaderFileSize(final long maxHeaderFileSize) {
        validateMaxFileSize(maxHeaderFileSize);
        this.maxHeaderFileSize = maxHeaderFileSize;
        return this;
    }

    @Override
    public long maxPayloadFileSize() {
        if (maxPayloadFileSize <= 0) {
            maxPayloadFileSize = defaults.maxPayloadFileSize();
        }
        if (maxPayloadFileSize <= 0) {
            maxPayloadFileSize = QueueConfigurations.defaultMaxPayloadFileSize();
        }
        return maxPayloadFileSize;
    }

    @Override
    public QueueConfigurator maxPayloadFileSize(final long maxPayloadFileSize) {
        validateMaxFileSize(maxPayloadFileSize);
        this.maxPayloadFileSize = maxPayloadFileSize;
        return this;
    }

    @Override
    public boolean expandHeaderFile() {
        if (expandHeaderFile == null) {
            expandHeaderFile = defaults.expandHeaderFile();
        }
        return expandHeaderFile;
    }

    @Override
    public QueueConfigurator expandHeaderFile(final boolean expandHeaderFile) {
        this.expandHeaderFile = expandHeaderFile;
        return this;
    }

    @Override
    public boolean expandPayloadFiles() {
        if (expandPayloadFiles == null) {
            expandPayloadFiles = defaults.expandPayloadFiles();
        }
        return expandPayloadFiles;
    }

    @Override
    public QueueConfigurator expandPayloadFiles(final boolean expandPayloadFiles) {
        this.expandPayloadFiles = expandPayloadFiles;
        return this;
    }

    @Override
    public boolean rollHeaderFile() {
        if (rollHeaderFile == null) {
            rollHeaderFile = defaults.rollHeaderFile();
        }
        return rollHeaderFile;
    }

    @Override
    public QueueConfigurator rollHeaderFile(final boolean rollHeaderFile) {
        this.rollHeaderFile = rollHeaderFile;
        return this;
    }

    @Override
    public boolean rollPayloadFiles() {
        if (rollPayloadFiles == null) {
            rollPayloadFiles = defaults.rollPayloadFiles();
        }
        return rollPayloadFiles;
    }

    @Override
    public QueueConfigurator rollPayloadFiles(final boolean rollPayloadFiles) {
        this.rollPayloadFiles = rollPayloadFiles;
        return this;
    }

    @Override
    public int headerFilesToCreateAhead() {
        if (headerFilesToCreateAhead < 0) {
            headerFilesToCreateAhead = defaults.headerFilesToCreateAhead();
        }
        if (headerFilesToCreateAhead < 0) {
            headerFilesToCreateAhead = QueueConfigurations.defaultHeaderFilesToCreateAhead();
        }
        return headerFilesToCreateAhead;
    }

    @Override
    public QueueConfigurator headerFilesToCreateAhead(final int headerFilesToCreateAhead) {
        validateFilesToCreateAhead(headerFilesToCreateAhead);
        this.headerFilesToCreateAhead = headerFilesToCreateAhead;
        return this;
    }

    @Override
    public int payloadFilesToCreateAhead() {
        if (payloadFilesToCreateAhead < 0) {
            payloadFilesToCreateAhead = defaults.payloadFilesToCreateAhead();
        }
        if (payloadFilesToCreateAhead < 0) {
            payloadFilesToCreateAhead = QueueConfigurations.defaultPayloadFilesToCreateAhead();
        }
        return payloadFilesToCreateAhead;
    }

    @Override
    public QueueConfigurator payloadFilesToCreateAhead(final int payloadFilesToCreateAhead) {
        validateFilesToCreateAhead(payloadFilesToCreateAhead);
        this.payloadFilesToCreateAhead = payloadFilesToCreateAhead;
        return this;
    }

    @Override
    public boolean closePollerFiles() {
        if (closePollerFiles == null) {
            closePollerFiles = defaults.closePollerFiles();
        }
        return closePollerFiles;
    }

    @Override
    public QueueConfigurator closePollerFiles(final boolean closePollerFiles) {
        this.closePollerFiles = closePollerFiles;
        return this;
    }

    @Override
    public boolean closeReaderFiles() {
        if (closeReaderFiles == null) {
            closeReaderFiles = defaults.closeReaderFiles();
        }
        return closeReaderFiles;
    }

    @Override
    public QueueConfigurator closeReaderFiles(final boolean closeReaderFiles) {
        this.closeReaderFiles = closeReaderFiles;
        return this;
    }

    @Override
    public MappingStrategy pollerHeaderMappingStrategy() {
        if (pollerHeaderMappingStrategy == null) {
            pollerHeaderMappingStrategy = defaults.pollerHeaderMappingStrategy();
        }
        if (pollerHeaderMappingStrategy == null) {
            pollerHeaderMappingStrategy = QueueConfigurations.defaultPollerHeaderMappingStrategy();
        }
        return pollerHeaderMappingStrategy;
    }

    @Override
    public QueueConfigurator pollerHeaderMappingStrategy(final MappingStrategy mappingStrategy) {
        this.pollerHeaderMappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingStrategy pollerPayloadMappingStrategy() {
        if (pollerPayloadMappingStrategy == null) {
            pollerPayloadMappingStrategy = defaults.pollerPayloadMappingStrategy();
        }
        if (pollerPayloadMappingStrategy == null) {
            pollerPayloadMappingStrategy = QueueConfigurations.defaultPollerPayloadMappingStrategy();
        }
        return pollerPayloadMappingStrategy;
    }

    @Override
    public QueueConfigurator pollerPayloadMappingStrategy(final MappingStrategy mappingStrategy) {
        this.pollerPayloadMappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingStrategy readerHeaderMappingStrategy() {
        if (readerHeaderMappingStrategy == null) {
            readerHeaderMappingStrategy = defaults.readerHeaderMappingStrategy();
        }
        if (readerHeaderMappingStrategy == null) {
            readerHeaderMappingStrategy = QueueConfigurations.defaultReaderHeaderMappingStrategy();
        }
        return readerHeaderMappingStrategy;
    }

    @Override
    public QueueConfigurator readerHeaderMappingStrategy(final MappingStrategy mappingStrategy) {
        this.readerHeaderMappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingStrategy readerPayloadMappingStrategy() {
        if (readerPayloadMappingStrategy == null) {
            readerPayloadMappingStrategy = defaults.readerPayloadMappingStrategy();
        }
        if (readerPayloadMappingStrategy == null) {
            readerPayloadMappingStrategy = QueueConfigurations.defaultReaderPayloadMappingStrategy();
        }
        return readerPayloadMappingStrategy;
    }

    @Override
    public QueueConfigurator readerPayloadMappingStrategy(final MappingStrategy mappingStrategy) {
        this.readerPayloadMappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingStrategy appenderHeaderMappingStrategy() {
        if (appenderHeaderMappingStrategy == null) {
            appenderHeaderMappingStrategy = defaults.appenderHeaderMappingStrategy();
        }
        if (appenderHeaderMappingStrategy == null) {
            appenderHeaderMappingStrategy = QueueConfigurations.defaultAppenderHeaderMappingStrategy();
        }
        return appenderHeaderMappingStrategy;
    }

    @Override
    public QueueConfigurator appenderHeaderMappingStrategy(final MappingStrategy mappingStrategy) {
        this.appenderHeaderMappingStrategy = requireNonNull(mappingStrategy);
        return this;
    }

    @Override
    public MappingStrategy appenderPayloadMappingStrategy() {
        if (appenderPayloadMappingStrategy == null) {
            appenderPayloadMappingStrategy = defaults.appenderPayloadMappingStrategy();
        }
        if (appenderPayloadMappingStrategy == null) {
            appenderPayloadMappingStrategy = QueueConfigurations.defaultAppenderPayloadMappingStrategy();
        }
        return appenderPayloadMappingStrategy;
    }

    @Override
    public QueueConfigurator appenderPayloadMappingStrategy(final MappingStrategy mappingStrategy) {
        this.appenderPayloadMappingStrategy = requireNonNull(mappingStrategy);
        return this;
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
        return new QueueConfigImpl(this);
    }

    @Override
    public String toString() {
        return "QueueConfiguratorImpl" +
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
