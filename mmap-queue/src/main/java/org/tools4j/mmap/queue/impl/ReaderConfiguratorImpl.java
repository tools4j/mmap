/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.queue.config.ReaderConfigurator;
import org.tools4j.mmap.region.config.MappingStrategy;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_ITERATOR_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.ENTRY_READER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.queue.impl.ReaderConfigDefaults.POLLER_CONFIG_DEFAULTS;

public class ReaderConfiguratorImpl implements ReaderConfigurator {
    private final ReaderConfig defaults;
    private MappingStrategy headerMappingStrategy;
    private MappingStrategy payloadMappingStrategy;
    private Boolean closeHeaderFiles;
    private Boolean closePayloadFiles;

    private ReaderConfiguratorImpl(final ReaderConfig defaults) {
        this.defaults = requireNonNull(defaults);
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
        closeHeaderFiles = null;
        closePayloadFiles = null;
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
    public MappingStrategy headerMappingStrategy() {
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaults.headerMappingStrategy();
        }
        return headerMappingStrategy;
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final MappingStrategy strategy) {
        this.headerMappingStrategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final MappingStrategyConfig config) {
        return headerMappingStrategy(MappingStrategy.create(config));
    }

    @Override
    public ReaderConfigurator headerMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return headerMappingStrategy(config);
    }

    @Override
    public MappingStrategy payloadMappingStrategy() {
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaults.payloadMappingStrategy();
        }
        return payloadMappingStrategy;
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final MappingStrategy strategy) {
        this.payloadMappingStrategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final MappingStrategyConfig config) {
        return payloadMappingStrategy(MappingStrategy.create(config));
    }

    @Override
    public ReaderConfigurator payloadMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return payloadMappingStrategy(config);
    }

    @Override
    public boolean closeHeaderFiles() {
        if (closeHeaderFiles == null) {
            closeHeaderFiles = defaults.closeHeaderFiles();
        }
        return closeHeaderFiles;
    }

    @Override
    public ReaderConfigurator closeHeaderFiles(final boolean closeHeaderFiles) {
        this.closeHeaderFiles = closeHeaderFiles;
        return this;
    }

    @Override
    public boolean closePayloadFiles() {
        if (closePayloadFiles == null) {
            closePayloadFiles = defaults.closePayloadFiles();
        }
        return closePayloadFiles;
    }

    @Override
    public ReaderConfigurator closePayloadFiles(final boolean closePayloadFiles) {
        this.closePayloadFiles = closePayloadFiles;
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
                "|closeHeaderFiles=" + closeHeaderFiles +
                "|closePayloadFiles=" + closePayloadFiles;
    }
}
