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
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link EntryReader} and {@link EntryReaderImpl}.
 */
class EntryReaderTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    /** More entries than headers in a header region, so that reads cross header and payload regions */
    private static final int ENTRIES = REGION_SIZE / Headers.HEADER_LENGTH + 1000;

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "mappingStrategy={0}")
    @EnumSource(MappingStrategy.class)
    void emptyQueueHasNoEntries(final MappingStrategy mappingStrategy) {
        try (final Queue queue = open(mappingStrategy);
             final EntryReader reader = queue.createEntryReader()) {
            assertTrue(reader.isEmpty());
            assertEquals(0, reader.size());
            assertEquals(Index.NULL, reader.firstIndex());
            assertEquals(Index.NULL, reader.lastIndex());
            assertFalse(reader.hasEntry(Index.FIRST));
            assertNoEntry(reader.readingFirst());
            assertNoEntry(reader.readingLast());
            assertNoEntry(reader.reading(5));
        }
    }

    @ParameterizedTest(name = "mappingStrategy={0}")
    @EnumSource(MappingStrategy.class)
    void readsEntriesByIndexAndFirstAndLast(final MappingStrategy mappingStrategy) {
        try (final Queue queue = open(mappingStrategy);
             final EntryReader reader = queue.createEntryReader()) {
            appendEntries(queue, 0, ENTRIES);
            assertFalse(reader.isEmpty());
            assertEquals(ENTRIES, reader.size());
            assertEquals(0, reader.firstIndex());
            assertEquals(ENTRIES - 1, reader.lastIndex());
            assertTrue(reader.hasEntry(ENTRIES - 1));
            assertFalse(reader.hasEntry(ENTRIES));
            assertFalse(reader.hasEntry(-1));

            assertEntry(reader.readingFirst(), 0);
            assertEntry(reader.readingLast(), ENTRIES - 1);
            for (int index = ENTRIES - 1; index >= 0; index -= 7) {
                assertEntry(reader.reading(index), index);
            }
            assertNoEntry(reader.reading(ENTRIES));
        }
    }

    @Test
    void readingLastSeesEntriesAppendedLater() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryReader reader = queue.createEntryReader()) {
            appendEntries(queue, 0, 3);
            assertEntry(reader.readingLast(), 2);
            appendEntries(queue, 3, ENTRIES);
            assertEntry(reader.readingLast(), ENTRIES - 1);
            assertEquals(ENTRIES - 1, reader.lastIndex());
        }
    }

    @Test
    void invalidIndexIsRejected() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryReader reader = queue.createEntryReader()) {
            appendEntries(queue, 0, 1);
            for (final long index : new long[]{Index.NULL, -2, Long.MIN_VALUE, Index.END}) {
                assertThrows(IllegalArgumentException.class, () -> reader.reading(index), "index=" + index);
            }
            assertEntry(reader.reading(0), 0);
        }
    }

    @Test
    void readingWithUnclosedContextThrowsAndClosesContext() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryReader reader = queue.createEntryReader()) {
            appendEntries(queue, 0, 2);
            final ReadingContext context = reader.reading(0);
            assertFalse(context.isClosed());
            assertThrows(IllegalStateException.class, () -> reader.reading(1));
            assertTrue(context.isClosed());
            assertNoEntry(context);
            assertEntry(reader.reading(1), 1);
        }
    }

    @Test
    void closedReaderRejectsAllOperations() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy)) {
            appendEntries(queue, 0, 1);
            final EntryReader reader = queue.createEntryReader();
            reader.close();
            assertTrue(reader.isClosed());
            assertThrows(IllegalStateException.class, () -> reader.reading(0));
            assertThrows(IllegalStateException.class, reader::readingFirst);
            assertThrows(IllegalStateException.class, reader::readingLast);
            assertThrows(IllegalStateException.class, reader::lastIndex);
            assertThrows(IllegalStateException.class, () -> reader.hasEntry(0));
            reader.close();
            assertTrue(reader.isClosed());
        }
    }

    private Queue open(final MappingStrategy mappingStrategy) {
        final QueueConfig config = QueueConfig.configure()
                .headerRegionSize(REGION_SIZE)
                .payloadRegionSize(REGION_SIZE)
                .mappingStrategy(mappingStrategy)
                .toImmutableQueueConfig();
        final File dir = tempDir.resolve("queue").toFile();
        return Queue.open(dir, OpenMode.OPEN_OR_CREATE, config);
    }

    private static void appendEntries(final Queue queue, final int from, final int to) {
        try (final Appender appender = queue.createAppender()) {
            for (int index = from; index < to; index++) {
                assertEquals(index, appender.append(entry(index).getBytes(StandardCharsets.US_ASCII)));
            }
        }
    }

    private static String entry(final long index) {
        return "entry-" + index;
    }

    /** Asserts and closes the context */
    private static void assertEntry(final ReadingContext context, final long index) {
        try (context) {
            assertTrue(context.hasEntry(), () -> "no entry at " + index);
            assertEquals(index, context.index());
            assertEquals(entry(index), context.buffer().getStringWithoutLengthAscii(0, context.buffer().capacity()));
        }
    }

    /** Asserts and closes the context */
    private static void assertNoEntry(final ReadingContext context) {
        try (context) {
            assertFalse(context.hasEntry());
            assertEquals(Index.NULL, context.index());
            assertEquals(0, context.buffer().capacity());
        }
    }
}
