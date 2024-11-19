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
import org.tools4j.mmap.queue.config.AppenderConfigurator;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfigurator;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurator;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.config.ReaderConfigurator;

import java.util.function.Consumer;

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
    private AppenderConfig appenderConfig;
    private ReaderConfig pollerConfig;
    private ReaderConfig readerConfig;
    private IndexReaderConfig indexReaderConfig;

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
        appenderConfig = null;
        pollerConfig = null;
        readerConfig = null;
        indexReaderConfig = null;
        return this;
    }

    @Override
    public long maxHeaderFileSize() {
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = defaults.maxHeaderFileSize();
        }
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxHeaderFileSize();
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
            maxPayloadFileSize = org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxPayloadFileSize();
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
            headerFilesToCreateAhead = org.tools4j.mmap.queue.config.QueueConfigurations.defaultHeaderFilesToCreateAhead();
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
            payloadFilesToCreateAhead = org.tools4j.mmap.queue.config.QueueConfigurations.defaultPayloadFilesToCreateAhead();
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
    public AppenderConfig appenderConfig() {
        if (appenderConfig == null) {
            appenderConfig = defaults.appenderConfig();
        }
        return appenderConfig;
    }

    @Override
    public QueueConfigurator appenderConfig(final AppenderConfig config) {
        this.appenderConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator appenderConfig(final Consumer<? super AppenderConfigurator> configurator) {
        final AppenderConfigurator config = AppenderConfigurator.configure();
        configurator.accept(config);
        this.appenderConfig = config;
        return this;
    }

    @Override
    public ReaderConfig pollerConfig() {
        if (pollerConfig == null) {
            pollerConfig = defaults.pollerConfig();
        }
        return pollerConfig;
    }

    @Override
    public QueueConfigurator pollerConfig(final ReaderConfig config) {
        this.pollerConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator pollerConfig(final Consumer<? super ReaderConfigurator> configurator) {
        final ReaderConfigurator config = ReaderConfigurator.configurePoller();
        configurator.accept(config);
        this.pollerConfig = config;
        return this;
    }

    @Override
    public ReaderConfig readerConfig() {
        if (readerConfig == null) {
            readerConfig = defaults.readerConfig();
        }
        return readerConfig;
    }

    @Override
    public QueueConfigurator readerConfig(final ReaderConfig config) {
        this.readerConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator readerConfig(final Consumer<? super ReaderConfigurator> configurator) {
        final ReaderConfigurator config = ReaderConfigurator.configureReader();
        configurator.accept(config);
        this.readerConfig = config;
        return this;
    }

    @Override
    public IndexReaderConfig indexReaderConfig() {
        if (indexReaderConfig == null) {
            indexReaderConfig = defaults.indexReaderConfig();
        }
        return indexReaderConfig;
    }

    @Override
    public QueueConfigurator indexReaderConfig(final IndexReaderConfig config) {
        this.indexReaderConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator indexReaderConfig(final Consumer<? super IndexReaderConfigurator> configurator) {
        final IndexReaderConfigurator config = IndexReaderConfigurator.configure();
        configurator.accept(config);
        this.indexReaderConfig = config;
        return this;
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
                "|appenderConfig={" + appenderConfig + "}" +
                "|pollerConfig={" + pollerConfig + "}" +
                "|readerConfig={" + readerConfig + "}" +
                "|indexReaderConfig={" + indexReaderConfig + "}";
    }
}
