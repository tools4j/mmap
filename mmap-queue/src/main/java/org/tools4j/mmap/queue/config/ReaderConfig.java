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

import org.agrona.concurrent.IdleStrategy;

import java.util.function.Supplier;

import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

/**
 * Configuration of a queue reader, that is, of a {@link org.tools4j.mmap.queue.api.Poller Poller},
 * {@link org.tools4j.mmap.queue.api.EntryReader EntryReader} or
 * {@link org.tools4j.mmap.queue.api.EntryIterator EntryIterator}, with mapping strategies and file settings for the
 * header file and for the payload files of all appenders.
 * <p>
 * Default configurations, different for each reader type, are defined through system properties, see
 * {@link QueueConfigurations}. Custom configurations are built with a {@link ReaderConfigurator} obtained through
 * {@link #configurePoller()}, {@link #configureEntryReader()} or {@link #configureEntryIterator()}.
 */
public interface ReaderConfig {
    /**
     * Returns the mapping strategy for the header file.
     *
     * @return the header mapping strategy
     */
    MappingStrategyConfig headerMappingStrategy();

    /**
     * Returns the mapping strategy for the payload files of all appenders.
     *
     * @return the payload mapping strategy
     */
    MappingStrategyConfig payloadMappingStrategy();

    /**
     * Returns the maximum number of header files kept open at once if header files are rolled; files are closed on a
     * least-recently-used basis. The value must be no less than the number of threads mapping files, that is, one
     * plus one each for async mapping and async unmapping if used by the header mapping strategy.
     *
     * @return the maximum number of header files kept open, at least one
     */
    int maxOpenHeaderFiles();

    /**
     * Returns the maximum number of payload files kept open at once if payload files are rolled; files are closed on a
     * least-recently-used basis. The value must be no less than the number of threads mapping files, that is, one
     * plus one each for async mapping and async unmapping if used by the payload mapping strategy.
     *
     * @return the maximum number of payload files kept open, at least one
     */
    int maxOpenPayloadFiles();

    /**
     * Returns the supplier of the idle strategy that pollers and entry iterators apply after every attempt to read
     * while the queue does not exist yet. Every poller and entry iterator gets its own idle strategy instance, which is
     * reset once the queue exists. Not used by entry readers.
     *
     * @return the supplier of the idle strategy while the queue does not exist yet
     */
    Supplier<? extends IdleStrategy> pendingQueueIdleStrategy();

    /**
     * Returns the supplier of the idle strategy that pollers and entry iterators apply after every failed attempt to
     * read the first queue entry, until an entry has been read; it is never applied after that, so that polling and
     * iterating latency is not affected. Every poller and entry iterator gets its own idle strategy instance, which
     * is reset once an entry has been read. Not used by entry readers.
     *
     * @return the supplier of the idle strategy until the first entry exists
     */
    Supplier<? extends IdleStrategy> pendingDataIdleStrategy();

    /**
     * Returns an immutable snapshot of this configuration, or this configuration itself if it is immutable already.
     *
     * @return an immutable reader configuration with the values of this configuration
     */
    ReaderConfig toImmutableReaderConfig();

    /**
     * Returns a new configurator to build a poller configuration, with values not set explicitly taken from the
     * {@link #getDefaultPollerConfig() default poller configuration}.
     *
     * @return a new poller configurator
     */
    static ReaderConfigurator configurePoller() {
        return ReaderConfigurator.configurePoller();
    }

    /**
     * Returns a new configurator to build an entry reader configuration, with values not set explicitly taken from
     * the {@link #getDefaultEntryReaderConfig() default entry reader configuration}.
     *
     * @return a new entry reader configurator
     */
    static ReaderConfigurator configureEntryReader() {
        return ReaderConfigurator.configureEntryReader();
    }

    /**
     * Returns a new configurator to build an entry iterator configuration, with values not set explicitly taken from
     * the {@link #getDefaultEntryIteratorConfig() default entry iterator configuration}.
     *
     * @return a new entry iterator configurator
     */
    static ReaderConfigurator configureEntryIterator() {
        return ReaderConfigurator.configureEntryIterator();
    }

    /**
     * Returns the default poller configuration, with values defined through system properties.
     *
     * @return the default poller configuration
     * @see QueueConfigurations
     */
    static ReaderConfig getDefaultPollerConfig() {
        return QueueConfigurations.defaultPollerConfig();
    }

    /**
     * Returns the default entry reader configuration, with values defined through system properties.
     *
     * @return the default entry reader configuration
     * @see QueueConfigurations
     */
    static ReaderConfig getDefaultEntryReaderConfig() {
        return QueueConfigurations.defaultEntryReaderConfig();
    }

    /**
     * Returns the default entry iterator configuration, with values defined through system properties.
     *
     * @return the default entry iterator configuration
     * @see QueueConfigurations
     */
    static ReaderConfig getDefaultEntryIteratorConfig() {
        return QueueConfigurations.defaultEntryIteratorConfig();
    }
}
