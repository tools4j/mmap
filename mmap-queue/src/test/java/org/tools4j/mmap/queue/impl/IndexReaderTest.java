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
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.queue.api.Queue;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link IndexReader} and {@link IndexReaderImpl}.
 */
class IndexReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyQueue() {
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile());
             final IndexReader reader = queue.createIndexReader()) {
            assertTrue(reader.isEmpty());
            assertEquals(0, reader.size());
            assertEquals(Index.NULL, reader.firstIndex());
            assertEquals(Index.NULL, reader.lastIndex());
            assertFalse(reader.hasEntry(Index.FIRST));
            assertFalse(reader.hasEntry(Index.NULL));
        }
    }

    @Test
    void queueWithEntriesAppendedWhileReaderIsOpen() {
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile());
             final IndexReader reader = queue.createIndexReader();
             final Appender appender = queue.createAppender()) {
            appender.append(new byte[]{1});
            assertFalse(reader.isEmpty());
            assertEquals(1, reader.size());
            assertEquals(0, reader.firstIndex());
            assertEquals(0, reader.lastIndex());

            for (int i = 1; i < 1000; i++) {
                appender.append(new byte[]{(byte) i});
            }
            assertEquals(1000, reader.size());
            assertEquals(999, reader.lastIndex());
            assertTrue(reader.hasEntry(999));
            assertFalse(reader.hasEntry(1000));
            assertFalse(reader.hasEntry(-5));
        }
    }

    @Test
    void closedReaderRejectsAllOperations() {
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile())) {
            final IndexReader reader = queue.createIndexReader();
            reader.close();
            assertTrue(reader.isClosed());
            assertThrows(IllegalStateException.class, reader::lastIndex);
            assertThrows(IllegalStateException.class, () -> reader.hasEntry(0));
            assertThrows(IllegalStateException.class, reader::firstIndex);
            assertThrows(IllegalStateException.class, reader::size);
            assertThrows(IllegalStateException.class, reader::isEmpty);
            reader.close();
            assertTrue(reader.isClosed());
        }
    }
}
