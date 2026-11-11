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

import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;

/**
 * Queue configuration, consisting of the {@link QueueLayoutConfig queue layout}, which is fixed when the queue is
 * created, of the {@link #maxAppenders() max appenders} of whoever opens the queue, and of default settings applied
 * to queue appenders and readers if no others are provided.
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
    /**
     * Returns the maximum number of appenders of this queue instance, that is, the appender IDs it acquires are below
     * this value. It is not part of the queue layout: queue instances opened with different max appenders can append
     * to the same queue concurrently, but none of them has exclusive access, not even with a single appender.
     *
     * @return the maximum number of appenders, between 1 and 256
     */
    int maxAppenders();

    /**
     * Returns the configuration used for appenders created without an explicit configuration.
     *
     * @return the default appender configuration of this queue
     */
    AppenderConfig appenderConfig();

    /**
     * Returns the configuration used for pollers created without an explicit configuration.
     *
     * @return the default poller configuration of this queue
     */
    ReaderConfig pollerConfig();

    /**
     * Returns the configuration used for entry readers created without an explicit configuration.
     *
     * @return the default entry reader configuration of this queue
     */
    ReaderConfig entryReaderConfig();

    /**
     * Returns the configuration used for entry iterators created without an explicit configuration.
     *
     * @return the default entry iterator configuration of this queue
     */
    ReaderConfig entryIteratorConfig();

    /**
     * Returns the configuration used for index readers created without an explicit configuration.
     *
     * @return the default index reader configuration of this queue
     */
    IndexReaderConfig indexReaderConfig();

    /**
     * Returns the listener for lifecycle events of the queue, its appenders and readers, and of the files and other
     * resources they use. It is not part of the queue layout. The default is defined by system property, see
     * {@link QueueConfigurations#defaultLifecycleListener()}.
     *
     * @return the lifecycle listener
     */
    LifecycleListener lifecycleListener();

    /**
     * Returns the listener for errors that cannot be thrown to the caller, such as exceptions thrown by entry handlers
     * of pollers, and for warnings. It is not part of the queue layout. The default is defined by system property,
     * see {@link org.tools4j.mmap.core.config.CoreConfigurations#defaultErrorListener()}.
     *
     * @return the error listener
     */
    ErrorListener errorListener();

    /**
     * Returns an immutable snapshot of this configuration, or this configuration itself if it is immutable already.
     *
     * @return an immutable queue configuration with the values of this configuration
     */
    QueueConfig toImmutableQueueConfig();

    /**
     * Returns a new configurator to build a queue configuration, with values not set explicitly taken from the
     * {@link #getDefault() default} configuration.
     *
     * @return a new queue configurator
     */
    static QueueConfigurator configure() {
        return QueueConfigurator.configure();
    }

    /**
     * Returns a new configurator to build a queue configuration, with values not set explicitly taken from the given
     * defaults.
     *
     * @param defaults the configuration providing values not set explicitly
     * @return a new queue configurator
     */
    static QueueConfigurator configure(final QueueConfig defaults) {
        return QueueConfigurator.configure(defaults);
    }

    /**
     * Returns the default queue configuration, with values defined through system properties.
     *
     * @return the default queue configuration
     * @see QueueConfigurations
     */
    static QueueConfig getDefault() {
        return QueueConfigurations.defaultQueueConfig();
    }
}
