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
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.region.config.MappingStrategyConfig;
import org.tools4j.mmap.region.config.MappingStrategyConfigurator;

import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderHeaderFilesToCreateAhead;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderPayloadFilesToCreateAhead;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderPayloadMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxOpenAppenderHeaderFiles;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultMaxOpenAppenderPayloadFiles;
import static org.tools4j.mmap.queue.impl.AppenderConfigDefaults.APPENDER_CONFIG_DEFAULTS;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;

public class AppenderConfiguratorImpl implements AppenderConfigurator {
    private final AppenderConfig defaults;
    private MappingStrategyConfig headerMappingStrategy;
    private MappingStrategyConfig payloadMappingStrategy;
    private int maxOpenHeaderFiles;
    private int maxOpenPayloadFiles;
    private int headerFilesToCreateAhead;
    private int payloadFilesToCreateAhead;

    public AppenderConfiguratorImpl() {
        this(APPENDER_CONFIG_DEFAULTS);
    }

    public AppenderConfiguratorImpl(final AppenderConfig defaults) {
        this.defaults = requireNonNull(defaults);
    }

    @Override
    public AppenderConfigurator reset() {
        headerMappingStrategy = null;
        payloadMappingStrategy = null;
        maxOpenHeaderFiles = 0;
        maxOpenPayloadFiles = 0;
        headerFilesToCreateAhead = 0;
        payloadFilesToCreateAhead = 0;
        return this;
    }

    @Override
    public AppenderConfigurator mappingStrategy(final MappingStrategy strategy) {
        return headerMappingStrategy(strategy).payloadMappingStrategy(strategy);
    }

    @Override
    public AppenderConfigurator mappingStrategy(final MappingStrategyConfig config) {
        return headerMappingStrategy(config).payloadMappingStrategy(config);
    }

    @Override
    public AppenderConfigurator mappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        return headerMappingStrategy(configurator).payloadMappingStrategy(configurator);
    }

    @Override
    public MappingStrategyConfig headerMappingStrategy() {
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaults.headerMappingStrategy();
        }
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaultAppenderHeaderMappingStrategy();
        }
        return headerMappingStrategy;
    }

    @Override
    public AppenderConfigurator headerMappingStrategy(final MappingStrategy strategy) {
        return headerMappingStrategy(strategy.mappingStrategyConfig());
    }

    @Override
    public AppenderConfigurator headerMappingStrategy(final MappingStrategyConfig config) {
        this.headerMappingStrategy = requireNonNull(config);
        return this;
    }

    @Override
    public AppenderConfigurator headerMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = headerMappingStrategy != null ?
                MappingStrategyConfigurator.configure(headerMappingStrategy) : MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return headerMappingStrategy(config);
    }

    @Override
    public MappingStrategyConfig payloadMappingStrategy() {
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaults.payloadMappingStrategy();
        }
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaultAppenderPayloadMappingStrategy();
        }
        return payloadMappingStrategy;
    }

    @Override
    public AppenderConfigurator payloadMappingStrategy(final MappingStrategy strategy) {
        return payloadMappingStrategy(strategy.mappingStrategyConfig());
    }

    @Override
    public AppenderConfigurator payloadMappingStrategy(final MappingStrategyConfig config) {
        this.payloadMappingStrategy = requireNonNull(config);
        return this;
    }

    @Override
    public AppenderConfigurator payloadMappingStrategy(final Consumer<? super MappingStrategyConfigurator> configurator) {
        final MappingStrategyConfigurator config = payloadMappingStrategy != null ?
                MappingStrategyConfigurator.configure(payloadMappingStrategy) : MappingStrategyConfigurator.configure();
        configurator.accept(config);
        return payloadMappingStrategy(config);
    }

    @Override
    public int maxOpenHeaderFiles() {
        if (maxOpenHeaderFiles <= 0) {
            maxOpenHeaderFiles = defaults.maxOpenHeaderFiles();
        }
        if  (maxOpenHeaderFiles <= 0) {
            maxOpenHeaderFiles = defaultMaxOpenAppenderHeaderFiles();
        }
        return maxOpenHeaderFiles;
    }

    @Override
    public AppenderConfigurator maxOpenHeaderFiles(final int maxOpenHeaderFiles) {
        this.maxOpenHeaderFiles = maxOpenPayloadFiles;
        return this;
    }

    @Override
    public int maxOpenPayloadFiles() {
        if (maxOpenPayloadFiles <= 0) {
            maxOpenPayloadFiles = defaults.maxOpenPayloadFiles();
        }
        if (maxOpenPayloadFiles <= 0) {
            maxOpenPayloadFiles = defaultMaxOpenAppenderPayloadFiles();
        }
        return maxOpenPayloadFiles;
    }

    @Override
    public AppenderConfigurator maxOpenPayloadFiles(final int maxOpenPayloadFiles) {
        this.maxOpenPayloadFiles = maxOpenPayloadFiles;
        return this;
    }

    @Override
    public int headerFilesToCreateAhead() {
        if (headerFilesToCreateAhead < 0) {
            headerFilesToCreateAhead = defaults.headerFilesToCreateAhead();
        }
        if (headerFilesToCreateAhead < 0) {
            headerFilesToCreateAhead = defaultAppenderHeaderFilesToCreateAhead();
        }
        return headerFilesToCreateAhead;
    }

    @Override
    public AppenderConfigurator headerFilesToCreateAhead(final int headerFilesToCreateAhead) {
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
            payloadFilesToCreateAhead = defaultAppenderPayloadFilesToCreateAhead();
        }
        return payloadFilesToCreateAhead;
    }

    @Override
    public AppenderConfigurator payloadFilesToCreateAhead(final int payloadFilesToCreateAhead) {
        validateFilesToCreateAhead(payloadFilesToCreateAhead);
        this.payloadFilesToCreateAhead = payloadFilesToCreateAhead;
        return this;
    }

    @Override
    public AppenderConfig toImmutableAppenderConfig() {
        return new AppenderConfigImpl(this);
    }

    @Override
    public String toString() {
        return "AppenderConfiguratorImpl" +
                ":headerMappingStrategy=" + headerMappingStrategy +
                "|payloadMappingStrategy=" + payloadMappingStrategy +
                "|maxOpenHeaderFiles=" + maxOpenHeaderFiles +
                "|maxOpenPayloadFiles=" + maxOpenPayloadFiles +
                "|headerFilesToCreateAhead=" + headerFilesToCreateAhead +
                "|payloadFilesToCreateAhead=" + payloadFilesToCreateAhead;
    }
}
