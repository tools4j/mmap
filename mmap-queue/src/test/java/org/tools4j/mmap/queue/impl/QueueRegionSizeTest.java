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
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.nio.file.Path;
import org.tools4j.mmap.queue.config.QueueConfigurator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for the queue region sizes of {@link QueueConfigurator} and {@link QueueConfiguratorImpl}.
 */
class QueueRegionSizeTest {

    private static final int REGION_SIZE = 4 * (int) REGION_SIZE_GRANULARITY;

    @TempDir
    Path tempDir;

    @Test
    void regionSizesDefaultToMappingDefault() {
        final QueueConfig config = QueueConfig.configure();
        assertEquals(MappingConfigurations.defaultRegionSize(), config.headerRegionSize());
        assertEquals(MappingConfigurations.defaultRegionSize(), config.payloadRegionSize());
        assertEquals(config.headerRegionSize(), config.toImmutableQueueConfig().headerRegionSize());
        assertEquals(config.payloadRegionSize(), config.toImmutableQueueConfig().payloadRegionSize());
    }

    @Test
    void invalidRegionSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> QueueConfig.configure().headerRegionSize(REGION_SIZE + 1));
        assertThrows(IllegalArgumentException.class, () -> QueueConfig.configure().payloadRegionSize(0));
    }

    @Test
    void payloadRegionSizeLimitsEntrySize() {
        final QueueConfig config = QueueConfig.configure().headerRegionSize(REGION_SIZE).payloadRegionSize(REGION_SIZE);
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile(), OpenMode.CREATE, config)) {
            final Appender appender = queue.createAppender();
            final int maxEntrySize = REGION_SIZE - Integer.BYTES;
            try (final AppendingContext context = appender.appending(maxEntrySize)) {
                context.commit(maxEntrySize);
            }
            assertThrows(IllegalArgumentException.class, () -> appender.appending(maxEntrySize + 1));
            assertEquals(1, queue.createIndexReader().size());
        }
    }
}
