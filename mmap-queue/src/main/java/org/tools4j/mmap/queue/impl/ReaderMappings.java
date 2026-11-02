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

import org.tools4j.mmap.mapping.api.Closeable;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.headerMappingConfig;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.payloadMappingConfig;

/**
 * Header and payload mappings for pollers and readers of queues.
 */
interface ReaderMappings extends Closeable {
    /**
     * Returns the header region mapping, or null if the queue does not exist yet. Moving the header mapping fails
     * while the header file does not exist yet.
     *
     * @return header region, or null if the queue does not exist yet
     */
    ElasticMapping header();

    /**
     * Returns the payload region mapping for the given appender. Must only be invoked after reading a header with a
     * non-null {@link #header()} mapping.
     *
     * @param appenderId appender id
     * @return payload region
     */
    ElasticMapping payload(int appenderId);

    /**
     * Factory method for reader mappings of pollers, entry readers and entry iterators.
     *
     * @param queueFiles   the queue files
     * @param openMode     the open mode of the queue, lazy mappings are created for
     *                     {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}, and eager mappings otherwise
     * @param queueConfig  supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param readerConfig configuration for reader mappings
     * @return a new reader mappings instance
     */
    static ReaderMappings create(final QueueFiles queueFiles,
                                 final OpenMode openMode,
                                 final Supplier<? extends QueueConfig> queueConfig,
                                 final ReaderConfig readerConfig) {
        final ReaderConfig readerCfg = readerConfig.toImmutableReaderConfig();
        return create(queueFiles, openMode, queueConfig,
                queueCfg -> headerMappingConfig(queueCfg, readerCfg),
                queueCfg -> payloadMappingConfig(queueCfg, readerCfg));
    }

    /**
     * Factory method for reader mappings of index readers, which have only a header mapping.
     *
     * @param queueFiles        the queue files
     * @param openMode          the open mode of the queue, lazy mappings are created for
     *                          {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}, and eager mappings otherwise
     * @param queueConfig       supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param indexReaderConfig configuration for index reader mappings
     * @return a new reader mappings instance without payload mappings
     */
    static ReaderMappings create(final QueueFiles queueFiles,
                                 final OpenMode openMode,
                                 final Supplier<? extends QueueConfig> queueConfig,
                                 final IndexReaderConfig indexReaderConfig) {
        final IndexReaderConfig indexReaderCfg = indexReaderConfig.toImmutableIndexReaderConfig();
        return create(queueFiles, openMode, queueConfig,
                queueCfg -> headerMappingConfig(queueCfg, indexReaderCfg),
                queueCfg -> {
                    throw new UnsupportedOperationException("Index readers have no payload mappings");
                });
    }

    private static ReaderMappings create(final QueueFiles queueFiles,
                                         final OpenMode openMode,
                                         final Supplier<? extends QueueConfig> queueConfig,
                                         final Function<? super QueueConfig, ? extends MappingConfig> headerConfig,
                                         final Function<? super QueueConfig, ? extends MappingConfig> payloadConfig) {
        requireNonNull(queueFiles);
        requireNonNull(openMode);
        requireNonNull(queueConfig);
        requireNonNull(headerConfig);
        requireNonNull(payloadConfig);
        if (openMode == OpenMode.READ_ONLY_LAZY) {
            return new LazyReaderMappings(queueConfig,
                    queueCfg -> new EagerReaderMappings(queueFiles, queueCfg, headerConfig, payloadConfig));
        }
        final QueueConfig queueCfg = queueConfig.get();
        if (queueCfg == null) {
            throw new IllegalStateException("Queue " + queueFiles.queueName() + " does not exist in open mode " +
                    openMode);
        }
        return new EagerReaderMappings(queueFiles, queueCfg, headerConfig, payloadConfig);
    }
}
