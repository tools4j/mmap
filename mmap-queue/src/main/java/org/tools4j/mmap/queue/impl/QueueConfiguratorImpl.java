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
import org.tools4j.mmap.queue.config.AppenderConfigurator;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfigurator;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurator;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.config.ReaderConfigurator;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultHeaderRegionSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxAppenders;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxHeaderFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxPayloadFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMinHeaderFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMinPayloadFileSize;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultPayloadRegionSize;
import static org.tools4j.mmap.queue.impl.QueueConfigDefaults.QUEUE_CONFIG_DEFAULTS;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxAppenders;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMinFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionSize;

public class QueueConfiguratorImpl implements QueueConfigurator {
    private final QueueConfig defaults;
    private int maxAppenders;
    private long minHeaderFileSize;
    private long maxHeaderFileSize;
    private long minPayloadFileSize;
    private long maxPayloadFileSize;
    private int headerRegionSize;
    private int payloadRegionSize;
    private Boolean expandHeaderFile;
    private Boolean expandPayloadFiles;
    private Boolean rollHeaderFile;
    private Boolean rollPayloadFiles;
    private AppenderConfig appenderConfig;
    private ReaderConfig pollerConfig;
    private ReaderConfig entryReaderConfig;
    private ReaderConfig entryIteratorConfig;
    private IndexReaderConfig indexReaderConfig;

    public QueueConfiguratorImpl() {
        this(QUEUE_CONFIG_DEFAULTS);
    }

    public QueueConfiguratorImpl(final QueueConfig defaults) {
        this.defaults = requireNonNull(defaults);
        reset();
    }

    @Override
    public QueueConfigurator reset() {
        maxAppenders = 0;
        minHeaderFileSize = -1;
        maxHeaderFileSize = 0;
        minPayloadFileSize = -1;
        maxPayloadFileSize = 0;
        headerRegionSize = 0;
        payloadRegionSize = 0;
        expandHeaderFile = null;
        expandPayloadFiles = null;
        rollHeaderFile = null;
        rollPayloadFiles = null;
        appenderConfig = null;
        pollerConfig = null;
        entryReaderConfig = null;
        entryIteratorConfig = null;
        indexReaderConfig = null;
        return this;
    }

    @Override
    public int maxAppenders() {
        if (maxAppenders <= 0) {
            maxAppenders = defaults.maxAppenders();
        }
        if (maxAppenders <= 0) {
            maxAppenders = defaultMaxAppenders();
        }
        return maxAppenders;
    }

    @Override
    public QueueConfigurator maxAppenders(final int maxAppenders) {
        validateMaxAppenders(maxAppenders);
        this.maxAppenders = maxAppenders;
        return this;
    }

    @Override
    public long minHeaderFileSize() {
        if (minHeaderFileSize < 0) {
            minHeaderFileSize = defaults.minHeaderFileSize();
        }
        if (minHeaderFileSize < 0) {
            minHeaderFileSize = defaultMinHeaderFileSize();
        }
        return minHeaderFileSize;
    }

    @Override
    public QueueConfigurator minHeaderFileSize(final long minHeaderFileSize) {
        validateMinFileSize(minHeaderFileSize);
        this.minHeaderFileSize = minHeaderFileSize;
        return this;
    }

    @Override
    public long maxHeaderFileSize() {
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = defaults.maxHeaderFileSize();
        }
        if (maxHeaderFileSize <= 0) {
            maxHeaderFileSize = defaultMaxHeaderFileSize();
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
    public long minPayloadFileSize() {
        if (minPayloadFileSize < 0) {
            minPayloadFileSize = defaults.minPayloadFileSize();
        }
        if (minPayloadFileSize < 0) {
            minPayloadFileSize = defaultMinPayloadFileSize();
        }
        return minPayloadFileSize;
    }

    @Override
    public QueueConfigurator minPayloadFileSize(final long minPayloadFileSize) {
        validateMinFileSize(minPayloadFileSize);
        this.minPayloadFileSize = minPayloadFileSize;
        return this;
    }

    @Override
    public long maxPayloadFileSize() {
        if (maxPayloadFileSize <= 0) {
            maxPayloadFileSize = defaults.maxPayloadFileSize();
        }
        if (maxPayloadFileSize <= 0) {
            maxPayloadFileSize = defaultMaxPayloadFileSize();
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
    public int headerRegionSize() {
        if (headerRegionSize <= 0) {
            headerRegionSize = defaults.headerRegionSize();
        }
        if (headerRegionSize <= 0) {
            headerRegionSize = defaultHeaderRegionSize();
        }
        return headerRegionSize;
    }

    @Override
    public QueueConfigurator headerRegionSize(final int headerRegionSize) {
        validateRegionSize(headerRegionSize);
        this.headerRegionSize = headerRegionSize;
        return this;
    }

    @Override
    public int payloadRegionSize() {
        if (payloadRegionSize <= 0) {
            payloadRegionSize = defaults.payloadRegionSize();
        }
        if (payloadRegionSize <= 0) {
            payloadRegionSize = defaultPayloadRegionSize();
        }
        return payloadRegionSize;
    }

    @Override
    public QueueConfigurator payloadRegionSize(final int payloadRegionSize) {
        validateRegionSize(payloadRegionSize);
        this.payloadRegionSize = payloadRegionSize;
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
    public QueueConfigurator maxOpenHeaderFiles(final int maxOpenHeaderFiles) {
        appenderConfig(cfg -> cfg.maxOpenHeaderFiles(maxOpenHeaderFiles));
        pollerConfig(cfg -> cfg.maxOpenHeaderFiles(maxOpenHeaderFiles));
        entryReaderConfig(cfg -> cfg.maxOpenHeaderFiles(maxOpenHeaderFiles));
        entryIteratorConfig(cfg -> cfg.maxOpenHeaderFiles(maxOpenHeaderFiles));
        indexReaderConfig(cfg -> cfg.maxOpenHeaderFiles(maxOpenHeaderFiles));
        return this;
    }

    @Override
    public QueueConfigurator maxOpenPayloadFiles(final int maxOpenPayloadFiles) {
        appenderConfig(cfg -> cfg.maxOpenPayloadFiles(maxOpenPayloadFiles));
        pollerConfig(cfg -> cfg.maxOpenPayloadFiles(maxOpenPayloadFiles));
        entryReaderConfig(cfg -> cfg.maxOpenPayloadFiles(maxOpenPayloadFiles));
        entryIteratorConfig(cfg -> cfg.maxOpenPayloadFiles(maxOpenPayloadFiles));
        return this;
    }

    @Override
    public QueueConfigurator headerFilesToCreateAhead(final int headerFilesToCreateAhead) {
        appenderConfig(cfg -> cfg.headerFilesToCreateAhead(headerFilesToCreateAhead));
        return this;
    }

    @Override
    public QueueConfigurator payloadFilesToCreateAhead(final int payloadFilesToCreateAhead) {
        appenderConfig(cfg -> cfg.payloadFilesToCreateAhead(payloadFilesToCreateAhead));
        return this;
    }

    @Override
    public QueueConfigurator mappingStrategy(final MappingStrategy mappingStrategy) {
        return mappingStrategy(mappingStrategy.mappingStrategyConfig());
    }

    @Override
    public QueueConfigurator mappingStrategy(final MappingStrategyConfig config) {
        requireNonNull(config);
        appenderConfig(cfg -> cfg.headerMappingStrategy(config).payloadMappingStrategy(config));
        pollerConfig(cfg -> cfg.headerMappingStrategy(config).payloadMappingStrategy(config));
        entryReaderConfig(cfg -> cfg.headerMappingStrategy(config).payloadMappingStrategy(config));
        entryIteratorConfig(cfg -> cfg.headerMappingStrategy(config).payloadMappingStrategy(config));
        indexReaderConfig(cfg -> cfg.headerMappingStrategy(config));
        return this;
    }

    @Override
    public QueueConfigurator mappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        return headerMappingStrategy(configurator).payloadMappingStrategy(configurator);
    }

    @Override
    public QueueConfigurator headerMappingStrategy(final MappingStrategy mappingStrategy) {
        return headerMappingStrategy(mappingStrategy.mappingStrategyConfig());
    }

    @Override
    public QueueConfigurator headerMappingStrategy(final MappingStrategyConfig config) {
        requireNonNull(config);
        appenderConfig(cfg -> cfg.headerMappingStrategy(config));
        pollerConfig(cfg -> cfg.headerMappingStrategy(config));
        entryReaderConfig(cfg -> cfg.headerMappingStrategy(config));
        entryIteratorConfig(cfg -> cfg.headerMappingStrategy(config));
        indexReaderConfig(cfg -> cfg.headerMappingStrategy(config));
        return this;
    }

    @Override
    public QueueConfigurator headerMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        requireNonNull(configurator);
        appenderConfig(cfg -> cfg.headerMappingStrategy(configurator));
        pollerConfig(cfg -> cfg.headerMappingStrategy(configurator));
        entryReaderConfig(cfg -> cfg.headerMappingStrategy(configurator));
        entryIteratorConfig(cfg -> cfg.headerMappingStrategy(configurator));
        indexReaderConfig(cfg -> cfg.headerMappingStrategy(configurator));
        return this;
    }

    @Override
    public QueueConfigurator payloadMappingStrategy(final MappingStrategy mappingStrategy) {
        return payloadMappingStrategy(mappingStrategy.mappingStrategyConfig());
    }

    @Override
    public QueueConfigurator payloadMappingStrategy(final MappingStrategyConfig config) {
        requireNonNull(config);
        appenderConfig(cfg -> cfg.payloadMappingStrategy(config));
        pollerConfig(cfg -> cfg.payloadMappingStrategy(config));
        entryReaderConfig(cfg -> cfg.payloadMappingStrategy(config));
        entryIteratorConfig(cfg -> cfg.payloadMappingStrategy(config));
        return this;
    }

    @Override
    public QueueConfigurator payloadMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        requireNonNull(configurator);
        appenderConfig(cfg -> cfg.payloadMappingStrategy(configurator));
        pollerConfig(cfg -> cfg.payloadMappingStrategy(configurator));
        entryReaderConfig(cfg -> cfg.payloadMappingStrategy(configurator));
        entryIteratorConfig(cfg -> cfg.payloadMappingStrategy(configurator));
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
        final AppenderConfigurator config = AppenderConfigurator.configure(appenderConfig());
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
        final ReaderConfigurator config = ReaderConfigurator.configure(pollerConfig());
        configurator.accept(config);
        this.pollerConfig = config;
        return this;
    }

    @Override
    public ReaderConfig entryReaderConfig() {
        if (entryReaderConfig == null) {
            entryReaderConfig = defaults.entryReaderConfig();
        }
        return entryReaderConfig;
    }

    @Override
    public QueueConfigurator entryReaderConfig(final ReaderConfig config) {
        this.entryReaderConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator entryReaderConfig(final Consumer<? super ReaderConfigurator> configurator) {
        final ReaderConfigurator config = ReaderConfigurator.configure(entryReaderConfig());
        configurator.accept(config);
        this.entryReaderConfig = config;
        return this;
    }

    @Override
    public ReaderConfig entryIteratorConfig() {
        if (entryIteratorConfig == null) {
            entryIteratorConfig = defaults.entryIteratorConfig();
        }
        return entryIteratorConfig;
    }

    @Override
    public QueueConfigurator entryIteratorConfig(final ReaderConfig config) {
        entryIteratorConfig = requireNonNull(config);
        return this;
    }

    @Override
    public QueueConfigurator entryIteratorConfig(final Consumer<? super ReaderConfigurator> configurator) {
        final ReaderConfigurator config = ReaderConfigurator.configure(entryIteratorConfig());
        configurator.accept(config);
        this.entryIteratorConfig = config;
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
        final IndexReaderConfigurator config = IndexReaderConfigurator.configure(indexReaderConfig());
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
                ":maxAppenders=" + maxAppenders +
                "|minHeaderFileSize=" + minHeaderFileSize +
                "|maxHeaderFileSize=" + maxHeaderFileSize +
                "|minPayloadFileSize=" + minPayloadFileSize +
                "|maxPayloadFileSize=" + maxPayloadFileSize +
                "|headerRegionSize=" + headerRegionSize +
                "|payloadRegionSize=" + payloadRegionSize +
                "|expandHeaderFile=" + expandHeaderFile +
                "|expandPayloadFiles=" + expandPayloadFiles +
                "|rollHeaderFile=" + rollHeaderFile +
                "|rollPayloadFiles=" + rollPayloadFiles +
                "|appenderConfig={" + appenderConfig + "}" +
                "|pollerConfig={" + pollerConfig + "}" +
                "|entryReaderConfig={" + entryReaderConfig + "}" +
                "|entryIteratorConfig={" + entryIteratorConfig + "}" +
                "|indexReaderConfig={" + indexReaderConfig + "}";
    }
}
