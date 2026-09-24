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

import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.config.ReaderConfigurator;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryReaderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultEntryReaderPayloadMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxOpenEntryReaderHeaderFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxOpenEntryReaderPayloadFiles;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

public class ReaderConfiguratorImpl implements ReaderConfigurator {
    private final ReaderConfig defaults;
    private MappingStrategyConfig headerMappingStrategy;
    private MappingStrategyConfig payloadMappingStrategy;
    private int maxOpenHeaderFiles;
    private int maxOpenPayloadFiles;

    private ReaderConfiguratorImpl(final ReaderConfig defaults) {
        this.defaults = requireNonNull(defaults);
        reset();
    }

    public static ReaderConfigurator createConfigurator(final ReaderConfig defaults) {
        return new ReaderConfiguratorImpl(defaults);
    }

    public static ReaderConfigurator createPollerConfigurator() {
        return createConfigurator(POLLER_CONFIG_DEFAULTS);
    }

    public static ReaderConfigurator createEntryReaderConfigurator() {
        return createConfigurator(ENTRY_READER_CONFIG_DEFAULTS);
    }

    public static ReaderConfigurator createEntryIteratorConfigurator() {
        return createConfigurator(ENTRY_ITERATOR_CONFIG_DEFAULTS);
    }

    @Override
    public ReaderConfigurator reset() {
        headerMappingStrategy = null;
        payloadMappingStrategy = null;
        maxOpenHeaderFiles = 0;
        maxOpenPayloadFiles = 0;
        return this;
    }

    @Override
    public ReaderConfigurator mappingStrategy(final MappingStrategy strategy) {
        return headerMappingStrategy(strategy).payloadMappingStrategy(strategy);
    }

    @Override
    public ReaderConfigurator mappingStrategy(final MappingStrategyConfig config) {
        return headerMappingStrategy(config).payloadMappingStrategy(config);
    }

    @Override
    public ReaderConfigurator mappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return headerMappingStrategy(config).payloadMappingStrategy(config);
    }

    @Override
    public MappingStrategyConfig headerMappingStrategy() {
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaults.headerMappingStrategy();
        }
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaultEntryReaderHeaderMappingStrategy();
        }
        return headerMappingStrategy;
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final MappingStrategy strategy) {
        return headerMappingStrategy(strategy.mappingStrategyConfig());
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final MappingStrategyConfig config) {
        this.headerMappingStrategy = requireNonNull(config);
        return this;
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return headerMappingStrategy(config);
    }

    @Override
    public MappingStrategyConfig payloadMappingStrategy() {
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaults.payloadMappingStrategy();
        }
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaultEntryReaderPayloadMappingStrategy();
        }
        return payloadMappingStrategy;
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final MappingStrategy strategy) {
        return payloadMappingStrategy(strategy.mappingStrategyConfig());
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final MappingStrategyConfig config) {
        this.payloadMappingStrategy = requireNonNull(config);
        return this;
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return payloadMappingStrategy(config);
    }

    @Override
    public int maxOpenHeaderFiles() {
        if (maxOpenHeaderFiles <= 0) {
            maxOpenHeaderFiles = defaults.maxOpenHeaderFiles();
        }
        if (maxOpenHeaderFiles <= 0) {
            maxOpenHeaderFiles = defaultMaxOpenEntryReaderHeaderFiles();
        }
        return maxOpenHeaderFiles;
    }

    @Override
    public ReaderConfigurator maxOpenHeaderFiles(final int maxOpenHeaderFiles) {
        this.maxOpenHeaderFiles = maxOpenHeaderFiles;
        return this;
    }

    @Override
    public int maxOpenPayloadFiles() {
        if (maxOpenPayloadFiles <= 0) {
            maxOpenPayloadFiles = defaults.maxOpenPayloadFiles();
        }
        if (maxOpenPayloadFiles <= 0) {
            maxOpenPayloadFiles = defaultMaxOpenEntryReaderPayloadFiles();
        }
        return maxOpenPayloadFiles;
    }

    @Override
    public ReaderConfigurator maxOpenPayloadFiles(final int maxOpenPayloadFiles) {
        this.maxOpenPayloadFiles = maxOpenPayloadFiles;
        return this;
    }


    @Override
    public ReaderConfig toImmutableReaderConfig() {
        return new ReaderConfigImpl(this);
    }

    @Override
    public String toString() {
        return "ReaderConfiguratorImpl" +
                ":defaults=" + defaults.getClass().getSimpleName() +
                "|headerMappingStrategy=" + headerMappingStrategy +
                "|payloadMappingStrategy=" + payloadMappingStrategy +
                "|maxOpenHeaderFiles=" + maxOpenHeaderFiles +
                "|maxOpenPayloadFiles=" + maxOpenPayloadFiles;
    }
}
