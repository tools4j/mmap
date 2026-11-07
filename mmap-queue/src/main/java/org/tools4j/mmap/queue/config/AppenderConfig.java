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

import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

/**
 * Configuration of a queue {@link org.tools4j.mmap.queue.api.Appender Appender}, with mapping strategies and file
 * settings for the header file and for the appender's payload files.
 * <p>
 * The {@link #getDefault() default} configuration is defined through system properties, see
 * {@link QueueConfigurations}. Custom configurations are built with an {@link AppenderConfigurator} obtained through
 * {@link #configure()}.
 */
public interface AppenderConfig {
    /**
     * Returns the mapping strategy for the header file.
     *
     * @return the header mapping strategy
     */
    MappingStrategyConfig headerMappingStrategy();

    /**
     * Returns the mapping strategy for the payload files of the appender.
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
     * Returns the number of header files to create ahead if the header file is rolled, that is, before they are
     * actually used for mappings.
     *
     * @return the number of header files to create ahead, zero if none
     */
    int headerFilesToCreateAhead();

    /**
     * Returns the number of payload files to create ahead if payload files are rolled, that is, before they are
     * actually used for mappings.
     *
     * @return the number of payload files to create ahead, zero if none
     */
    int payloadFilesToCreateAhead();

    /**
     * Returns true if new space of header files is prefilled, that is, written before it is mapped. This reserves the
     * disk space, so that writing to the mapping cannot fault on a full disk. Otherwise new space is sparse, and
     * allocated by the file system when first written through the mapping.
     * <p>
     * Prefilling is done by the thread extending the file: the async mapping thread with an async mapping strategy
     * such as {@link MappingStrategy#AsyncMapAheadStrategy}, and otherwise the appending thread itself, which then
     * stalls while the new space is filled. The space filled at once is the
     * {@linkplain QueueLayoutConfig#minHeaderFileSize() min header file size} if the header file is
     * {@linkplain QueueLayoutConfig#expandHeaderFile() expanded}, and otherwise the
     * {@linkplain QueueLayoutConfig#maxHeaderFileSize() max header file size}, filled when a header file is created.
     * With sync mapping strategies, a small file size filled at once is therefore recommended, a few megabytes for
     * instance, since filling takes in the order of milliseconds per ten megabytes.
     *
     * @return true if new header file space is prefilled, false if it is sparse
     */
    boolean prefillHeaderFiles();

    /**
     * Returns true if new space of payload files is prefilled, that is, written before it is mapped. This reserves the
     * disk space, so that writing to the mapping cannot fault on a full disk. Otherwise new space is sparse, and
     * allocated by the file system when first written through the mapping.
     * <p>
     * Prefilling is done by the thread extending the file: the async mapping thread with an async mapping strategy
     * such as {@link MappingStrategy#AsyncMapAheadStrategy}, and otherwise the appending thread itself, which then
     * stalls while the new space is filled. The space filled at once is the
     * {@linkplain QueueLayoutConfig#minPayloadFileSize() min payload file size} if payload files are
     * {@linkplain QueueLayoutConfig#expandPayloadFiles() expanded}, and otherwise the
     * {@linkplain QueueLayoutConfig#maxPayloadFileSize() max payload file size}, filled when a payload file is created.
     * With sync mapping strategies, a small file size filled at once is therefore recommended, a few megabytes for
     * instance, since filling takes in the order of milliseconds per ten megabytes.
     *
     * @return true if new payload file space is prefilled, false if it is sparse
     */
    boolean prefillPayloadFiles();

    /**
     * Returns an immutable snapshot of this configuration, or this configuration itself if it is immutable already.
     *
     * @return an immutable appender configuration with the values of this configuration
     */
    AppenderConfig toImmutableAppenderConfig();

    /**
     * Returns a new configurator to build an appender configuration, with values not set explicitly taken from the
     * {@link #getDefault() default} configuration.
     *
     * @return a new appender configurator
     */
    static AppenderConfigurator configure() {
        return AppenderConfigurator.configure();
    }

    /**
     * Returns the default appender configuration, with values defined through system properties.
     *
     * @return the default appender configuration
     * @see QueueConfigurations
     */
    static AppenderConfig getDefault() {
        return QueueConfigurations.defaultAppenderConfig();
    }
}
