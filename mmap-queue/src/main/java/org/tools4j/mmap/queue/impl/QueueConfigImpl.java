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
    private final AppenderConfig appenderConfig;
    private final ReaderConfig pollerConfig;
    private final ReaderConfig entryReaderConfig;
    private final ReaderConfig entryIteratorConfig;
    private final IndexReaderConfig indexReaderConfig;

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
                queueConfig.appenderConfig(),
                queueConfig.pollerConfig(),
                queueConfig.entryReaderConfig(),
                queueConfig.entryIteratorConfig(),
                queueConfig.indexReaderConfig());
    }

    public QueueConfigImpl(final long maxHeaderFileSize,
                           final long maxPayloadFileSize,
                           final boolean expandHeaderFile,
                           final boolean expandPayloadFiles,
                           final boolean rollHeaderFile,
                           final boolean rollPayloadFiles,
                           final int headerFilesToCreateAhead,
                           final int payloadFilesToCreateAhead,
                           final AppenderConfig appenderConfig,
                           final ReaderConfig pollerConfig,
                           final ReaderConfig entryReaderConfig,
                           final ReaderConfig entryIteratorConfig,
                           final IndexReaderConfig indexReaderConfig) {
        this.maxHeaderFileSize = maxHeaderFileSize;
        this.maxPayloadFileSize = maxPayloadFileSize;
        this.expandHeaderFile = expandHeaderFile;
        this.expandPayloadFiles = expandPayloadFiles;
        this.rollHeaderFile = rollHeaderFile;
        this.rollPayloadFiles = rollPayloadFiles;
        this.headerFilesToCreateAhead = headerFilesToCreateAhead;
        this.payloadFilesToCreateAhead = payloadFilesToCreateAhead;
        this.appenderConfig = appenderConfig.toImmutableAppenderConfig();
        this.pollerConfig = pollerConfig.toImmutableReaderConfig();
        this.entryReaderConfig = entryReaderConfig.toImmutableReaderConfig();
        this.entryIteratorConfig = entryIteratorConfig.toImmutableReaderConfig();
        this.indexReaderConfig = indexReaderConfig.toImmutableIndexReaderConfig();
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
    public AppenderConfig appenderConfig() {
        return appenderConfig;
    }

    @Override
    public ReaderConfig pollerConfig() {
        return pollerConfig;
    }

    @Override
    public ReaderConfig entryReaderConfig() {
        return entryReaderConfig;
    }

    @Override
    public ReaderConfig entryIteratorConfig() {
        return entryIteratorConfig;
    }

    @Override
    public IndexReaderConfig indexReaderConfig() {
        return indexReaderConfig;
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
                "|appenderConfig={" + appenderConfig + "}" +
                "|pollerConfig={" + pollerConfig + "}" +
                "|entryReaderConfig={" + entryReaderConfig + "}" +
                "|entryIteratorConfig={" + entryIteratorConfig + "}" +
                "|indexReaderConfig={" + indexReaderConfig + "}";
    }
}
