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
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link LazyReaderMappings} created through {@link ReaderMappings} for
 * {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}.
 */
class LazyReaderMappingsTest {

    private static final QueueConfig CONFIG = QueueConfig.configure()
            .lifecycleListener(LifecycleListener.NO_OP)
            .errorListener(ErrorListener.NO_OP)
            .toImmutableQueueConfig();

    @TempDir
    Path tempDir;

    /** Queue configuration in effect, null until the queue exists */
    private final AtomicReference<QueueConfig> queueConfig = new AtomicReference<>();
    private final AtomicInteger configCalls = new AtomicInteger();

    private File queueFile() {
        return tempDir.resolve("queue").toFile();
    }

    private ReaderMappings readerMappings() {
        return ReaderMappings.create(new QueueFiles(queueFile()), OpenMode.READ_ONLY_LAZY, () -> {
            configCalls.incrementAndGet();
            return queueConfig.get();
        }, ReaderConfig.configurePoller());
    }

    @Test
    void headerIsNullUntilQueueExists() {
        try (final ReaderMappings mappings = readerMappings()) {
            assertInstanceOf(LazyReaderMappings.class, mappings);
            assertNull(mappings.header());
            assertNull(mappings.header());
            assertEquals(2, configCalls.get());
            assertFalse(mappings.isClosed());
        }
    }

    @Test
    void headerIsCreatedOnceQueueExistsAndMovesOnceHeaderFileExists() {
        try (final ReaderMappings mappings = readerMappings()) {
            assertNull(mappings.header());
            try (final Queue queue = Queue.open(queueFile(), OpenMode.CREATE, CONFIG)) {
                queueConfig.set(CONFIG);
                final ElasticMapping header = mappings.header();
                assertNotNull(header);
                assertFalse(header.moveTo(0), "header file does not exist yet");

                queueConfig.set(null);//not used once header exists
                assertSame(header, mappings.header());
                assertEquals(2, configCalls.get());

                try (final Appender ignored = queue.createAppender()) {
                    assertTrue(header.moveTo(0));
                }
            }
        }
    }

    @Test
    void closingBeforeQueueExistsPreventsCreatingHeader() {
        final ReaderMappings mappings = readerMappings();
        queueConfig.set(CONFIG);
        mappings.close();
        assertTrue(mappings.isClosed());
        assertNull(mappings.header());
        assertEquals(0, configCalls.get());
        mappings.close();
        assertTrue(mappings.isClosed());
    }

    @Test
    void closingClosesHeaderAndPayloadMappings() {
        try (final Queue queue = Queue.open(queueFile(), OpenMode.CREATE, CONFIG);
             final Appender appender = queue.createAppender()) {
            appender.append(new byte[8]);
            queueConfig.set(CONFIG);
            final ReaderMappings mappings = readerMappings();
            final ElasticMapping header = mappings.header();
            final ElasticMapping payload = mappings.payload(0);
            mappings.close();
            assertTrue(mappings.isClosed());
            assertTrue(header.isClosed());
            assertTrue(payload.isClosed());
            assertSame(header, mappings.header());
        }
    }
}
