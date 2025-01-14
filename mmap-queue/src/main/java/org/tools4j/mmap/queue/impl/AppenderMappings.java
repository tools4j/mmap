/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mappings;
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.FileInitialiser;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.headerMappingConfig;
import static org.tools4j.mmap.queue.impl.QueueMappingConfigs.payloadMappingConfig;

/**
 * Header and payload mapping for queues appenders.
 */
interface AppenderMappings extends AutoCloseable {
    /**
     * @return the appender ID
     */
    int appenderId();
    /**
     * @return header region
     */
    OffsetMapping header();

    /**
     * @return payload region
     */
    OffsetMapping payload();

    boolean isClosed();

    @Override
    void close();

    /**
     * Factory method for appender mappings.
     *
     * @param queueFiles        the queue files
     * @param appenderIdPool    the appender ID pool
     * @param queueConfig       the queue configuration settings
     * @param appenderConfig    configuration for appender mappings
     * @return a new appender mappings instance
     */
    static AppenderMappings create(final QueueFiles queueFiles,
                                   final AppenderIdPool appenderIdPool,
                                   final QueueConfig queueConfig,
                                   final AppenderConfig appenderConfig) {
        requireNonNull(queueFiles);
        requireNonNull(queueConfig);
        requireNonNull(appenderIdPool);
        final QueueConfig queueCfg = queueConfig.toImmutableQueueConfig();
        final AppenderConfig appenderCfg = appenderConfig.toImmutableAppenderConfig();

        return new AppenderMappings() {
            final int appenderId = appenderIdPool.acquire();
            final MappingConfig headerCfg = headerMappingConfig(queueCfg, appenderCfg);
            final MappingConfig payloadCfg = payloadMappingConfig(queueCfg, appenderCfg);
            final OffsetMapping header = Mappings.offsetMapping(queueFiles.headerFile(), AccessMode.READ_WRITE,
                    FileInitialiser.zeroBytes(AccessMode.READ_WRITE, Headers.HEADER_LENGTH), headerCfg);
            final OffsetMapping payload = Mappings.offsetMapping(queueFiles.payloadFile(appenderId),
                    AccessMode.READ_WRITE, payloadCfg);

            @Override
            public int appenderId() {
                return appenderId;
            }

            @Override
            public OffsetMapping header() {
                return header;
            }

            @Override
            public OffsetMapping payload() {
                return payload;
            }

            @Override
            public boolean isClosed() {
                return header.isClosed();
            }

            @Override
            public void close() {
                if (!isClosed()) {
                    header.close();
                    payload.close();
                    appenderIdPool.release(appenderId);
                }
            }

            @Override
            public String toString() {
                return "AppenderMappings" +
                        ":queue=" + queueFiles.queueName() +
                        "|appenderId=" + appenderId +
                        "|closed=" + isClosed();
            }
        };
    }

}
