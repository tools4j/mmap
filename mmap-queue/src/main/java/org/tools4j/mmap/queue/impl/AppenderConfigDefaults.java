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
import org.tools4j.mmap.region.config.MappingStrategy;

import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderHeaderMappingStrategy;
import static org.tools4j.mmap.queue.config.QueueConfigurations.defaultAppenderPayloadMappingStrategy;

public enum AppenderConfigDefaults implements AppenderConfig {
    APPENDER_CONFIG_DEFAULTS;

    @Override
    public MappingStrategy headerMappingStrategy() {
        return defaultAppenderHeaderMappingStrategy();
    }

    @Override
    public MappingStrategy payloadMappingStrategy() {
        return defaultAppenderPayloadMappingStrategy();
    }

    @Override
    public AppenderConfig toImmutableAppenderConfig() {
        return new AppenderConfigImpl(this);
    }

    @Override
    public String toString() {
        return "AppenderConfigDefaults" +
                ":headerMappingStrategy=" + headerMappingStrategy() +
                "|payloadMappingStrategy=" + payloadMappingStrategy();
    }
}
