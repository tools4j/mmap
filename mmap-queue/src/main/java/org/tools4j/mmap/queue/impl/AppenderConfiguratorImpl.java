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
import org.tools4j.mmap.region.config.MappingStrategy;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderPayloadMappingStrategy;

public class AppenderConfiguratorImpl implements AppenderConfigurator {
    private MappingStrategy headerMappingStrategy;
    private MappingStrategy payloadMappingStrategy;

    @Override
    public AppenderConfigurator reset() {
        headerMappingStrategy = null;
        payloadMappingStrategy = null;
        return this;
    }

    @Override
    public MappingStrategy headerMappingStrategy() {
        if (headerMappingStrategy == null) {
            headerMappingStrategy = defaultAppenderHeaderMappingStrategy();
        }
        return headerMappingStrategy;
    }

    @Override
    public AppenderConfigurator payloadMappingStrategy(final MappingStrategy strategy) {
        this.payloadMappingStrategy = requireNonNull(strategy);
        return this;
    }

    @Override
    public MappingStrategy payloadMappingStrategy() {
        if (payloadMappingStrategy == null) {
            payloadMappingStrategy = defaultAppenderPayloadMappingStrategy();
        }
        return payloadMappingStrategy;
    }

    @Override
    public AppenderConfigurator headerMappingStrategy(final MappingStrategy strategy) {
        this.headerMappingStrategy = requireNonNull(strategy);
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
                "|payloadMappingStrategy=" + payloadMappingStrategy;
    }
}
