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

import org.agrona.collections.Int2ObjectHashMap;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;

/**
 * Header and payload mappings for pollers and readers of queues.
 */
interface ReaderMappings extends AutoCloseable {
    /**
     * @return header region
     */
    OffsetMapping header();

    /**
     * @param appenderId appender id
     * @return payload region
     */
    OffsetMapping payload(int appenderId);

    boolean isClosed();

    @Override
    void close();

    /**
     * Factory method for reader mappings.
     *
     * @param queueFiles    the queue files
     * @param headerConfig  configuration for header file and region mappers
     * @param payloadConfig configuration for payload file and region mappers
     * @return a new reader mappings instance
     */
    static ReaderMappings create(final QueueFiles queueFiles,
                                 final MappingConfig headerConfig,
                                 final MappingConfig payloadConfig) {
        requireNonNull(queueFiles);
        requireNonNull(headerConfig);
        requireNonNull(payloadConfig);

        return new ReaderMappings() {
            final MappingConfig headerCfg = headerConfig.toImmutableMappingConfig();
            final MappingConfig payloadCfg = payloadConfig.toImmutableMappingConfig();
            final OffsetMapping header = Mappings.offsetMapping(queueFiles.headerFile(), AccessMode.READ_ONLY,
                    FileInitialiser.zeroBytes(AccessMode.READ_ONLY, Headers.HEADER_LENGTH), headerCfg);
            final Int2ObjectHashMap<OffsetMapping> payloadMappings = new Int2ObjectHashMap<>();
            final IntFunction<OffsetMapping> payloadMappingFactory = appenderId -> Mappings.offsetMapping(
                queueFiles.payloadFile(appenderId), AccessMode.READ_ONLY, payloadCfg);

            @Override
            public OffsetMapping header() {
                return header;
            }

            @Override
            public OffsetMapping payload(final int appenderId) {
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
                return "ReadMappings" +
                        ":queue=" + queueFiles.queueName() +
                        "|closed=" + isClosed();
            }
        };
    }

}
