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
package org.tools4j.mmap.queue.config;


/**
 * Queue configuration, consisting of the {@link QueueLayoutConfig queue layout}, which is fixed when the queue is
 * created, and of default settings applied to queue appenders and readers if no others are provided.
 * <p>
 * The {@link #getDefault() default} configuration is defined through system properties, see
 * {@link QueueConfigurations}. Custom configurations are built with a {@link QueueConfigurator} obtained through
 * {@link #configure()}, or through {@link #configure(QueueConfig)} to start from other defaults, for instance:
 * <pre>{@code
 * QueueConfig config = QueueConfig.configure()
 *         .maxAppenders(1)
 *         .payloadRegionSize(1 << 20)
 *         .pollerConfig(cfg -> cfg.payloadMappingStrategy(MappingStrategy.AsyncMapAheadStrategy));
 * }</pre>
 */
public interface QueueConfig extends QueueLayoutConfig {
    AppenderConfig appenderConfig();
    ReaderConfig pollerConfig();
    ReaderConfig entryReaderConfig();
    ReaderConfig entryIteratorConfig();
    IndexReaderConfig indexReaderConfig();

    QueueConfig toImmutableQueueConfig();

    static QueueConfigurator configure() {
        return QueueConfigurator.configure();
    }

    static QueueConfigurator configure(final QueueConfig defaults) {
        return QueueConfigurator.configure(defaults);
    }

    static QueueConfig getDefault() {
        return QueueConfigurations.defaultQueueConfig();
    }
}
