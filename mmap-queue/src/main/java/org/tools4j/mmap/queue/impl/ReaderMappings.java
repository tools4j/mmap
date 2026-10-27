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

import org.agrona.collections.Int2ObjectHashMap;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;
import org.tools4j.mmap.mapping.api.Closeable;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfig;

import java.io.File;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.headerMappingConfig;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.payloadMappingConfig;

/**
 * Header and payload mappings for pollers and readers of queues.
 */
interface ReaderMappings extends Closeable {
    /**
     * Returns the header region mapping.
     *
     * @return header region
     */
    ElasticMapping header();

    /**
     * Returns the payload region mapping for the given appender.
     *
     * @param appenderId appender id
     * @return payload region
     */
    ElasticMapping payload(int appenderId);

    /**
     * Factory method for reader mappings.
     *
     * @param queueFiles   the queue files
     * @param queueConfig  supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param readerConfig configuration for reader mappings
     * @return a new reader mappings instance
     */
    static ReaderMappings create(final QueueFiles queueFiles,
                                 final Supplier<? extends QueueConfig> queueConfig,
                                 final ReaderConfig readerConfig) {
        requireNonNull(queueFiles);
        requireNonNull(queueConfig);
        final ReaderConfig readerCfg = readerConfig.toImmutableReaderConfig();

        return new ReaderMappings() {
            final ElasticMapping header = headerMapping(queueFiles, queueConfig,
                    queueCfg -> headerMappingConfig(queueCfg, readerCfg));
            final Int2ObjectHashMap<ElasticMapping> payloadMappings = new Int2ObjectHashMap<>();
            final IntFunction<ElasticMapping> payloadMappingFactory = appenderId -> Mappings.elasticMapping(
                    queueFiles.payloadFile(appenderId), OpenMode.READ_ONLY_LAZY,
                    payloadMappingConfig(existingQueueConfig(queueConfig), readerCfg));

            @Override
            public ElasticMapping header() {
                return header;
            }

            @Override
            public ElasticMapping payload(final int appenderId) {
                return payloadMappings.computeIfAbsent(appenderId, payloadMappingFactory);
            }

            @Override
            public boolean isClosed() {
                return header.isClosed();
            }

            @Override
            public void close() {
                if (!isClosed()) {
                    header.close();
                    payloadMappings.forEachInt((appenderId, mapping) -> mapping.close());
                    payloadMappings.clear();
                }
            }

            @Override
            public String toString() {
                return "ReaderMappings" +
                        ":queue=" + queueFiles.queueName() +
                        "|closed=" + isClosed();
            }
        };
    }

    /**
     * Creates a read-only header mapping; if the queue does not exist yet, the header file is mapped once it does.
     *
     * @param queueFiles   the queue files
     * @param queueConfig  supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param headerConfig function returning the header mapping config given the queue configuration in effect
     * @return a new header mapping
     */
    static ElasticMapping headerMapping(final QueueFiles queueFiles,
                                        final Supplier<? extends QueueConfig> queueConfig,
                                        final Function<? super QueueConfig, ? extends MappingConfig> headerConfig) {
        final File headerFile = queueFiles.headerFile();
        final FileInitialiser fileInitialiser = FileInitialiser.minLength(Headers.HEADER_LENGTH);
        final QueueConfig queueCfg = queueConfig.get();
        if (queueCfg != null) {
            return Mappings.elasticMapping(headerFile, OpenMode.READ_ONLY_LAZY, fileInitialiser,
                    headerConfig.apply(queueCfg));
        }
        return new LazyElasticMapping(() -> {
            final QueueConfig cfg = queueConfig.get();
            return cfg == null ? null : Mappings.elasticMapping(headerFile, OpenMode.READ_ONLY_LAZY, fileInitialiser,
                    headerConfig.apply(cfg));
        });
    }

    private static QueueConfig existingQueueConfig(final Supplier<? extends QueueConfig> queueConfig) {
        //NOTE: payload is mapped only after reading a header, hence the queue exists
        final QueueConfig queueCfg = queueConfig.get();
        assert queueCfg != null : "queue must exist";
        return queueCfg;
    }

}
