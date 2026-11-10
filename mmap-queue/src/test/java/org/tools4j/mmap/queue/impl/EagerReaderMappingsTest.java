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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.IndexReaderConfig;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link EagerReaderMappings} created through {@link ReaderMappings} for open modes other than
 * {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}.
 */
class EagerReaderMappingsTest {

    private static final QueueConfig CONFIG = QueueConfig.configure()
            .lifecycleListener(LifecycleListener.NO_OP)
            .errorListener(ErrorListener.NO_OP)
            .toImmutableQueueConfig();

    @TempDir
    Path tempDir;

    private File queueFile() {
        return tempDir.resolve("queue").toFile();
    }

    private ReaderMappings readerMappings(final OpenMode openMode) {
        return ReaderMappings.create(new QueueFiles(queueFile()), openMode, () -> CONFIG,
                ReaderConfig.configurePoller());
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, mode = EnumSource.Mode.EXCLUDE, names = "READ_ONLY_LAZY")
    void eagerMappingsAreCreatedForOpenModesOtherThanReadOnlyLazy(final OpenMode openMode) {
        try (final ReaderMappings mappings = readerMappings(openMode)) {
            assertInstanceOf(EagerReaderMappings.class, mappings);
        }
    }

    @Test
    void eagerMappingsRequireQueueToExist() {
        assertThrows(IllegalStateException.class, () -> ReaderMappings.create(new QueueFiles(queueFile()),
                OpenMode.READ_ONLY, () -> null, ReaderConfig.configurePoller()));
    }

    @Test
    void headerExistsRightAwayAndMovesOnceHeaderFileExists() {
        try (final Queue queue = Queue.open(queueFile(), OpenMode.CREATE, CONFIG);
             final ReaderMappings mappings = readerMappings(OpenMode.READ_ONLY)) {
            final ElasticMapping header = mappings.header();
            assertSame(header, mappings.header());
            assertFalse(header.moveTo(0), "header file does not exist yet");
            try (final Appender ignored = queue.createAppender()) {
                assertTrue(header.moveTo(0));
            }
        }
    }

    @Test
    void payloadMappingIsCreatedOncePerAppender() {
        try (final Queue queue = Queue.open(queueFile(), OpenMode.CREATE, CONFIG);
             final Appender appender0 = queue.createAppender();
             final Appender appender1 = queue.createAppender();
             final ReaderMappings mappings = readerMappings(OpenMode.READ_ONLY)) {
            appender0.append(new byte[8]);
            appender1.append(new byte[8]);
            final ElasticMapping payload = mappings.payload(0);
            assertSame(payload, mappings.payload(0));
            assertNotSame(payload, mappings.payload(1));
        }
    }

    @Test
    void payloadMappingRequiresPayloadFileToExist() {
        try (final ReaderMappings mappings = readerMappings(OpenMode.READ_ONLY)) {
            assertThrows(IllegalArgumentException.class, () -> mappings.payload(0));
        }
    }

    @Test
    void closingClosesHeaderAndPayloadMappings() {
        try (final Queue queue = Queue.open(queueFile(), OpenMode.CREATE, CONFIG);
             final Appender appender = queue.createAppender()) {
            appender.append(new byte[8]);
            final ReaderMappings mappings = readerMappings(OpenMode.READ_ONLY);
            final ElasticMapping header = mappings.header();
            final ElasticMapping payload = mappings.payload(0);
            mappings.close();
            assertTrue(mappings.isClosed());
            assertTrue(header.isClosed());
            assertTrue(payload.isClosed());
            mappings.close();
            assertTrue(mappings.isClosed());
        }
    }

    @Test
    void indexReaderMappingsHaveNoPayload() {
        try (final ReaderMappings mappings = ReaderMappings.create(new QueueFiles(queueFile()), OpenMode.READ_ONLY,
                () -> CONFIG, IndexReaderConfig.configure())) {
            assertInstanceOf(EagerReaderMappings.class, mappings);
            assertThrows(UnsupportedOperationException.class, () -> mappings.payload(0));
        }
    }
}
