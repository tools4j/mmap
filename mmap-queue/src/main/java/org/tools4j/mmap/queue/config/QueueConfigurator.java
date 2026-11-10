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
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;
import org.tools4j.mmap.queue.impl.QueueConfiguratorImpl;

import java.util.function.Consumer;

/**
 * Configurator to build a {@link QueueConfig}; values not set explicitly are taken from the defaults the configurator
 * was created with. Settings for appenders and readers, such as mapping strategies, can be set for all of them at once
 * or through the nested configuration of each type, for instance {@link #pollerConfig(Consumer)}.
 */
public interface QueueConfigurator extends QueueConfig {
    /**
     * Sets the maximum number of appenders of this queue instance, see {@link QueueConfig#maxAppenders()}.
     *
     * @param maxAppenders the maximum number of appenders, between 1 and 256
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is not between 1 and 256
     */
    QueueConfigurator maxAppenders(int maxAppenders);

    /**
     * Sets the minimum header file size in bytes, zero for region-sized increments, see
     * {@link QueueLayoutConfig#minHeaderFileSize()}. It is part of the queue layout, used only when the queue is
     * created; an existing queue uses its stored layout.
     *
     * @param minHeaderFileSize the minimum header file size in bytes, zero for region-sized increments
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is neither zero nor a power of two and a multiple of the OS page
     *                                  size
     */
    QueueConfigurator minHeaderFileSize(long minHeaderFileSize);

    /**
     * Sets the maximum header file size in bytes, see {@link QueueLayoutConfig#maxHeaderFileSize()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param maxHeaderFileSize the maximum header file size in bytes
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is not a power of two or not a multiple of the OS page size
     */
    QueueConfigurator maxHeaderFileSize(long maxHeaderFileSize);

    /**
     * Sets the minimum payload file size in bytes, zero for region-sized increments, see
     * {@link QueueLayoutConfig#minPayloadFileSize()}. It is part of the queue layout, used only when the queue is
     * created; an existing queue uses its stored layout.
     *
     * @param minPayloadFileSize the minimum payload file size in bytes, zero for region-sized increments
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is neither zero nor a power of two and a multiple of the OS page
     *                                  size
     */
    QueueConfigurator minPayloadFileSize(long minPayloadFileSize);

    /**
     * Sets the maximum payload file size in bytes, see {@link QueueLayoutConfig#maxPayloadFileSize()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param maxPayloadFileSize the maximum payload file size in bytes
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is not a power of two or not a multiple of the OS page size
     */
    QueueConfigurator maxPayloadFileSize(long maxPayloadFileSize);

    /**
     * Sets the header region size in bytes, see {@link QueueLayoutConfig#headerRegionSize()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param headerRegionSize the header region size in bytes
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is not a power of two or not a multiple of the OS page size
     */
    QueueConfigurator headerRegionSize(int headerRegionSize);

    /**
     * Sets the payload region size in bytes, see {@link QueueLayoutConfig#payloadRegionSize()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param payloadRegionSize the payload region size in bytes
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is not a power of two or not a multiple of the OS page size
     */
    QueueConfigurator payloadRegionSize(int payloadRegionSize);

    /**
     * Sets the flag whether the header file is expanded on demand, see {@link QueueLayoutConfig#expandHeaderFile()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param expandHeaderFile the flag whether the header file is expanded on demand
     * @return this configurator for method chaining
     */
    QueueConfigurator expandHeaderFile(boolean expandHeaderFile);

    /**
     * Sets the flag whether payload files are expanded on demand, see {@link QueueLayoutConfig#expandPayloadFiles()}.
     * It is part of the queue layout, used only when the queue is created; an existing queue uses its stored layout.
     *
     * @param expandPayloadFiles the flag whether payload files are expanded on demand
     * @return this configurator for method chaining
     */
    QueueConfigurator expandPayloadFiles(boolean expandPayloadFiles);

    /**
     * Sets the flag whether the header file is rolled at its maximum size, see
     * {@link QueueLayoutConfig#rollHeaderFile()}. It is part of the queue layout, used only when the queue is created;
     * an existing queue uses its stored layout.
     *
     * @param rollHeaderFile the flag whether the header file is rolled at its maximum size
     * @return this configurator for method chaining
     */
    QueueConfigurator rollHeaderFile(boolean rollHeaderFile);

    /**
     * Sets the flag whether payload files are rolled at their maximum size, see
     * {@link QueueLayoutConfig#rollPayloadFiles()}. It is part of the queue layout, used only when the queue is
     * created; an existing queue uses its stored layout.
     *
     * @param rollPayloadFiles the flag whether payload files are rolled at their maximum size
     * @return this configurator for method chaining
     */
    QueueConfigurator rollPayloadFiles(boolean rollPayloadFiles);

    /**
     * Sets the maximum number of header files kept open at once by all appenders and readers if the header file is
     * rolled.
     *
     * @param maxOpenHeaderFiles the maximum number of header files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    QueueConfigurator maxOpenHeaderFiles(int maxOpenHeaderFiles);

    /**
     * Sets the maximum number of payload files kept open at once by appenders and readers except index readers if
     * payload files are rolled.
     *
     * @param maxOpenPayloadFiles the maximum number of payload files kept open, or zero to reset it to the default
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    QueueConfigurator maxOpenPayloadFiles(int maxOpenPayloadFiles);

    /**
     * Sets the number of header files created ahead by appenders if the header file is rolled.
     *
     * @param headerFilesToCreateAhead the number of header files to create ahead, zero for none
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    QueueConfigurator headerFilesToCreateAhead(int headerFilesToCreateAhead);

    /**
     * Sets the number of payload files created ahead by appenders if payload files are rolled.
     *
     * @param payloadFilesToCreateAhead the number of payload files to create ahead, zero for none
     * @return this configurator for method chaining
     * @throws IllegalArgumentException if the value is negative
     */
    QueueConfigurator payloadFilesToCreateAhead(int payloadFilesToCreateAhead);

    /**
     * Sets the mapping strategy for the header and payload files of all appenders and readers (index readers only have
     * a header file) to the given predefined strategy.
     *
     * @param mappingStrategy the mapping strategy
     * @return this configurator for method chaining
     */
    QueueConfigurator mappingStrategy(MappingStrategy mappingStrategy);

    /**
     * Sets the mapping strategy configuration for the header and payload files of all appenders and readers (index
     * readers only have a header file).
     *
     * @param mappingStrategyConfig the mapping strategy configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator mappingStrategy(MappingStrategyConfig mappingStrategyConfig);

    /**
     * Configures the mapping strategy for the header and payload files of all appenders and readers (index readers only
     * have a header file), usually provided in lambda-format; the consumer is applied to each of these strategies, each
     * time with a configurator starting from the current strategy.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategies
     * @return this configurator for method chaining
     */
    QueueConfigurator mappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the header file of all appenders and readers to the given predefined strategy.
     *
     * @param mappingStrategy the mapping strategy
     * @return this configurator for method chaining
     */
    QueueConfigurator headerMappingStrategy(MappingStrategy mappingStrategy);

    /**
     * Sets the mapping strategy configuration for the header file of all appenders and readers.
     *
     * @param mappingStrategyConfig the mapping strategy configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator headerMappingStrategy(MappingStrategyConfig mappingStrategyConfig);

    /**
     * Configures the mapping strategy for the header file of all appenders and readers, usually provided in
     * lambda-format; the consumer is applied to each of these strategies, each time with a configurator starting
     * from the current strategy.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategies
     * @return this configurator for method chaining
     */
    QueueConfigurator headerMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the mapping strategy for the payload files of appenders and readers except index readers to the given
     * predefined strategy.
     *
     * @param mappingStrategy the mapping strategy
     * @return this configurator for method chaining
     */
    QueueConfigurator payloadMappingStrategy(MappingStrategy mappingStrategy);

    /**
     * Sets the mapping strategy configuration for the payload files of appenders and readers except index readers.
     *
     * @param mappingStrategyConfig the mapping strategy configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator payloadMappingStrategy(MappingStrategyConfig mappingStrategyConfig);

    /**
     * Configures the mapping strategy for the payload files of appenders and readers except index readers, usually
     * provided in lambda-format; the consumer is applied to each of these strategies, each time with a configurator
     * starting from the current strategy.
     *
     * @param configurator a consumer for the configurator to customize the mapping strategies
     * @return this configurator for method chaining
     */
    QueueConfigurator payloadMappingStrategy(Consumer<? super MappingStrategyConfigurator> configurator);

    /**
     * Sets the appender configuration, used for appenders created without an explicit configuration.
     *
     * @param config the appender configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator appenderConfig(AppenderConfig config);

    /**
     * Configures the appender configuration, usually provided in lambda-format; the configurator passed to the
     * consumer starts from the current appender configuration of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the appender configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator appenderConfig(Consumer<? super AppenderConfigurator> configurator);

    /**
     * Sets the poller configuration, used for pollers created without an explicit configuration.
     *
     * @param config the poller configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator pollerConfig(ReaderConfig config);

    /**
     * Configures the poller configuration, usually provided in lambda-format; the configurator passed to the
     * consumer starts from the current poller configuration of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the poller configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator pollerConfig(Consumer<? super ReaderConfigurator> configurator);

    /**
     * Sets the entry reader configuration, used for entry readers created without an explicit configuration.
     *
     * @param config the entry reader configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator entryReaderConfig(ReaderConfig config);

    /**
     * Configures the entry reader configuration, usually provided in lambda-format; the configurator passed to the
     * consumer starts from the current entry reader configuration of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the entry reader configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator entryReaderConfig(Consumer<? super ReaderConfigurator> configurator);

    /**
     * Sets the entry iterator configuration, used for entry iterators created without an explicit configuration.
     *
     * @param config the entry iterator configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator entryIteratorConfig(ReaderConfig config);

    /**
     * Configures the entry iterator configuration, usually provided in lambda-format; the configurator passed to the
     * consumer starts from the current entry iterator configuration of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the entry iterator configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator entryIteratorConfig(Consumer<? super ReaderConfigurator> configurator);

    /**
     * Sets the index reader configuration, used for index readers created without an explicit configuration.
     *
     * @param config the index reader configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator indexReaderConfig(IndexReaderConfig config);

    /**
     * Configures the index reader configuration, usually provided in lambda-format; the configurator passed to the
     * consumer starts from the current index reader configuration of this configurator.
     *
     * @param configurator a consumer for the configurator to customize the index reader configuration
     * @return this configurator for method chaining
     */
    QueueConfigurator indexReaderConfig(Consumer<? super IndexReaderConfigurator> configurator);

    /**
     * Sets the listener for lifecycle events of the queue, its appenders and readers, and of the files and other
     * resources they use.
     *
     * @param lifecycleListener the lifecycle listener
     * @return this configurator for method chaining
     */
    QueueConfigurator lifecycleListener(LifecycleListener lifecycleListener);

    /**
     * Sets the listener for errors that cannot be thrown to the caller, and for warnings.
     *
     * @param errorListener the error listener
     * @return this configurator for method chaining
     */
    QueueConfigurator errorListener(ErrorListener errorListener);

    /**
     * Resets all values set on this configurator, so that values are again taken from the defaults this configurator
     * was created with.
     *
     * @return this configurator for method chaining
     */
    QueueConfigurator reset();

    /**
     * Returns a new configurator to build a queue configuration, with values not set explicitly taken from the
     * {@link QueueConfig#getDefault() default} configuration.
     *
     * @return a new queue configurator
     */
    static QueueConfigurator configure() {
        return new QueueConfiguratorImpl();
    }

    /**
     * Returns a new configurator to build a queue configuration, with values not set explicitly taken from the given
     * defaults.
     *
     * @param defaults the configuration providing values not set explicitly
     * @return a new queue configurator
     */
    static QueueConfigurator configure(final QueueConfig defaults) {
        return new QueueConfiguratorImpl(defaults);
    }

}
