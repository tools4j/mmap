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
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryIterator;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link EntryIterator} and {@link EntryIteratorImpl}.
 */
class EntryIteratorTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    /** More entries than headers in a header region, so that iteration crosses header and payload regions */
    private static final int ENTRIES = REGION_SIZE / Headers.HEADER_LENGTH + 1000;

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "mappingStrategy={0}")
    @EnumSource(MappingStrategy.class)
    void emptyQueueIteratesNoEntries(final MappingStrategy mappingStrategy) {
        try (final Queue queue = open(mappingStrategy);
             final EntryIterator iterator = queue.createEntryIterator()) {
            assertIterates(iterator.readingFromFirst(), 0);
            assertIterates(iterator.readingFromLast(), 0);
            assertIterates(iterator.readingFromEnd(), 0);
            assertIterates(iterator.readingFromLast().reverse(), 0);
        }
    }

    @ParameterizedTest(name = "mappingStrategy={0}")
    @EnumSource(MappingStrategy.class)
    void iteratesForwardAndInReverse(final MappingStrategy mappingStrategy) {
        try (final Queue queue = open(mappingStrategy);
             final EntryIterator iterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, ENTRIES);
            assertIterates(iterator.readingFromFirst(), 0, range(0, ENTRIES - 1));
            assertIterates(iterator.readingFrom(ENTRIES - 10), ENTRIES - 10, range(ENTRIES - 10, ENTRIES - 1));
            assertIterates(iterator.readingFromLast(), ENTRIES - 1, List.of(ENTRIES - 1L));
            assertIterates(iterator.readingFromEnd(), ENTRIES);
            assertIterates(iterator.readingFromLast().reverse(), ENTRIES - 1, range(ENTRIES - 1, 0));
            assertIterates(iterator.readingFrom(10).reverse(), 10, range(10, 0));
            assertIterates(iterator.readingFrom(10).reverse().reverse(), 10, range(10, ENTRIES - 1));
            assertIterates(iterator.readingFrom(ENTRIES + 5), ENTRIES + 5);
        }
    }

    @Test
    void iteratorReturnsEntriesAppendedAfterHasNextReturnedFalse() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator entryIterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, 2);
            try (final IterableContext context = entryIterator.readingFromFirst()) {
                final Iterator<Entry> iterator = context.iterator();
                assertEquals(List.of(0L, 1L), drain(iterator));
                assertFalse(iterator.hasNext());
                appendEntries(queue, 2, 4);
                assertEquals(List.of(2L, 3L), drain(iterator));
            }
        }
    }

    @Test
    void iteratorFromEndReturnsEntriesAppendedLater() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator entryIterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, 2);
            try (final IterableContext context = entryIterator.readingFromEnd()) {
                assertEquals(2, context.startIndex());
                final Iterator<Entry> iterator = context.iterator();
                assertFalse(iterator.hasNext());
                appendEntries(queue, 2, 3);
                assertEquals(List.of(2L), drain(iterator));
            }
        }
    }

    @Test
    void readingFromLastSeesEntriesAppendedLater() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator iterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, 3);
            assertIterates(iterator.readingFromLast(), 2, List.of(2L));
            appendEntries(queue, 3, ENTRIES);
            assertIterates(iterator.readingFromLast(), ENTRIES - 1, List.of(ENTRIES - 1L));
        }
    }

    @Test
    void nextWithoutEntryThrowsAndNewIteratorRestartsAtStartIndex() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator entryIterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, 2);
            try (final IterableContext context = entryIterator.readingFromFirst()) {
                final Iterator<Entry> iterator = context.iterator();
                assertEquals(List.of(0L, 1L), drain(iterator));
                assertThrows(NoSuchElementException.class, iterator::next);
                assertEquals(List.of(0L, 1L), drain(context.iterator()));
            }
        }
    }

    @Test
    void invalidStartIndexIsRejected() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator iterator = queue.createEntryIterator()) {
            for (final long index : new long[]{Index.NULL, -2, Long.MIN_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> iterator.readingFrom(index), "index=" + index);
            }
            assertIterates(iterator.readingFromFirst(), 0);
        }
    }

    @Test
    void readingWithUnclosedContextThrowsAndClosesContext() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy);
             final EntryIterator iterator = queue.createEntryIterator()) {
            appendEntries(queue, 0, 2);
            final IterableContext context = iterator.readingFromFirst();
            assertThrows(IllegalStateException.class, iterator::readingFromFirst);
            assertTrue(context.isClosed());
            assertEquals(Index.NULL, context.startIndex());
            assertThrows(IllegalStateException.class, context::iterator);
            assertIterates(iterator.readingFromFirst(), 0, List.of(0L, 1L));
        }
    }

    @Test
    void closedIteratorRejectsReading() {
        try (final Queue queue = open(MappingStrategy.SyncMappingStrategy)) {
            final EntryIterator iterator = queue.createEntryIterator();
            iterator.close();
            assertTrue(iterator.isClosed());
            assertThrows(IllegalStateException.class, iterator::readingFromFirst);
            assertThrows(IllegalStateException.class, iterator::readingFromLast);
            assertThrows(IllegalStateException.class, iterator::readingFromEnd);
            assertThrows(IllegalStateException.class, () -> iterator.readingFrom(0));
            iterator.close();
            assertTrue(iterator.isClosed());
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

    private static List<Long> range(final long first, final long last) {
        return (first <= last ? LongStream.rangeClosed(first, last) : LongStream.rangeClosed(last, first)
                .map(index -> first + last - index)).boxed().toList();
    }

    /** Returns the indices of the remaining entries, asserting their content */
    private static List<Long> drain(final Iterator<Entry> iterator) {
        final List<Long> indices = new ArrayList<>();
        while (iterator.hasNext()) {
            final Entry entry = iterator.next();
            assertEquals(entry(entry.index()), entry.buffer().getStringWithoutLengthAscii(0, entry.buffer().capacity()));
            indices.add(entry.index());
        }
        return indices;
    }

    /** Asserts and closes the context */
    private static void assertIterates(final IterableContext context, final long startIndex) {
        assertIterates(context, startIndex, List.of());
    }

    /** Asserts and closes the context */
    private static void assertIterates(final IterableContext context,
                                       final long startIndex,
                                       final List<Long> expectedIndices) {
        try (context) {
            assertEquals(startIndex, context.startIndex());
            assertEquals(expectedIndices, drain(context.iterator()));
        }
    }
}
