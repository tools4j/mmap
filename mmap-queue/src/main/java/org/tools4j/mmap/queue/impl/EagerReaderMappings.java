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

import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.util.function.Function;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;

/**
 * Reader mappings of a queue that exists: the header mapping is created right away, and payload mappings on demand.
 * Moving the header mapping fails as long as the header file does not exist yet, that is, before the first appender
 * is created. Payload files must exist when their mapping is created, which is the case after reading a header.
 * <p>
 * Like the mappings, this class is not thread-safe.
 */
final class EagerReaderMappings implements ReaderMappings {
    private static final FileInitialiser HEADER_FILE_INITIALISER = FileInitialiser.minLength(Headers.HEADER_LENGTH);
    private final QueueFiles queueFiles;
    private final ElasticMapping header;
    /** Payload mappings by appender ID, created on first access */
    private final ElasticMapping[] payloadMappings = new ElasticMapping[Headers.MAX_APPENDERS];
    private final IntFunction<ElasticMapping> payloadMappingFactory;

    /**
     * Constructor with queue configuration in effect and mapping configuration functions.
     *
     * @param queueFiles    the queue files
     * @param queueConfig   the queue configuration in effect
     * @param headerConfig  returns the header mapping configuration given the queue configuration in effect
     * @param payloadConfig returns the payload mapping configuration given the queue configuration in effect
     */
    EagerReaderMappings(final QueueFiles queueFiles,
                        final QueueConfig queueConfig,
                        final Function<? super QueueConfig, ? extends MappingConfig> headerConfig,
                        final Function<? super QueueConfig, ? extends MappingConfig> payloadConfig) {
        requireNonNull(queueConfig);
        requireNonNull(payloadConfig);
        this.queueFiles = requireNonNull(queueFiles);
        //NOTES: header  : may not exist yet if no data has been appended yet, hence opening in lazy mode
        //       payload : mapped only after reading a header, and appenders write the payload before the header,
        //                 hence the payload file must exist
        this.header = Mappings.elasticMapping(queueFiles.headerFile(), OpenMode.READ_ONLY_LAZY,
                HEADER_FILE_INITIALISER, headerConfig.apply(queueConfig));
        this.payloadMappingFactory = appenderId -> Mappings.elasticMapping(queueFiles.payloadFile(appenderId),
                OpenMode.READ_ONLY, payloadConfig.apply(queueConfig));
    }

    @Override
    public ElasticMapping header() {
        return header;
    }

    @Override
    public ElasticMapping payload(final int appenderId) {
        final ElasticMapping mapping = payloadMappings[appenderId];
        return mapping != null ? mapping : newPayloadMapping(appenderId);
    }

    private ElasticMapping newPayloadMapping(final int appenderId) {
        final ElasticMapping mapping = payloadMappingFactory.apply(appenderId);
        payloadMappings[appenderId] = mapping;
        return mapping;
    }

    @Override
    public boolean isClosed() {
        return header.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            header.close();
            for (int appenderId = 0; appenderId < payloadMappings.length; appenderId++) {
                final ElasticMapping mapping = payloadMappings[appenderId];
                if (mapping != null) {
                    mapping.close();
                    payloadMappings[appenderId] = null;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "EagerReaderMappings" +
                ":queue=" + queueFiles.queueName() +
                "|closed=" + isClosed();
    }
}
