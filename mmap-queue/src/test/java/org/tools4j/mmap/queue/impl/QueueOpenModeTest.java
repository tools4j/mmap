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
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tools4j.mmap.queue.config.QueueConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Queue#open(File, OpenMode, QueueConfig)} and {@link QueueImpl} with the different
 * {@link OpenMode}s.
 */
class QueueOpenModeTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"READ_ONLY", "READ_WRITE"})
    void openingMissingQueueFails(final OpenMode openMode) {
        final File dir = queueDir();
        assertThrows(IllegalArgumentException.class, () -> Queue.open(dir, openMode));
        assertFalse(dir.exists());
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"CREATE", "OPEN_OR_CREATE", "RECREATE"})
    void creatingMissingQueueCreatesDirectory(final OpenMode openMode) {
        final File dir = queueDir();
        try (final Queue queue = Queue.open(dir, openMode)) {
            assertTrue(dir.isDirectory());
            assertEquals(0, queue.createIndexReader().size());
        }
    }

    @Test
    void creatingExistingQueueFails() {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE).close();
        assertThrows(IllegalArgumentException.class, () -> Queue.open(dir, OpenMode.CREATE));
    }

    @Test
    void creatingQueueInMissingParentFails() {
        final File dir = tempDir.resolve("missing").resolve("queue").toFile();
        assertThrows(IllegalArgumentException.class, () -> Queue.open(dir, OpenMode.OPEN_OR_CREATE));
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"READ_ONLY", "READ_ONLY_LAZY", "READ_WRITE", "OPEN_OR_CREATE"})
    void openingExistingQueueKeepsEntries(final OpenMode openMode) {
        final File dir = queueDir();
        appendTo(dir, OpenMode.CREATE, "first");
        try (final Queue queue = Queue.open(dir, openMode)) {
            assertEquals(List.of("first"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void recreatingExistingQueueDeletesEntries() {
        final File dir = queueDir();
        appendTo(dir, OpenMode.CREATE, "first");
        appendTo(dir, OpenMode.RECREATE, "second");
        try (final Queue queue = Queue.open(dir, OpenMode.READ_ONLY)) {
            assertEquals(List.of("second"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void readOnlyQueueOfEmptyQueueSeesEntriesAppendedLater() {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE).close();
        try (final Queue reader = Queue.open(dir, OpenMode.READ_ONLY)) {
            final Poller poller = reader.createPoller();
            assertEquals(List.of(), pollAll(poller));
            appendTo(dir, OpenMode.READ_WRITE, "first");
            assertEquals(List.of("first"), pollAll(poller));
        }
    }

    @Test
    void readOnlyLazyQueueOfMissingQueueSeesEntriesOnceCreatedAndAppended() {
        final File dir = queueDir();
        try (final Queue reader = Queue.open(dir, OpenMode.READ_ONLY_LAZY)) {
            final Poller poller = reader.createPoller();
            assertEquals(List.of(), pollAll(poller));
            assertFalse(dir.exists());
            appendTo(dir, OpenMode.CREATE, "first");
            assertEquals(List.of("first"), pollAll(poller));
        }
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"READ_ONLY", "READ_ONLY_LAZY"})
    void readOnlyQueueRejectsAppendersAndDoesNotTouchIdPool(final OpenMode openMode) {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE).close();
        final File idPoolFile = new File(dir, dir.getName() + "_ids.mmq");
        assertTrue(idPoolFile.delete());
        try (final Queue queue = Queue.open(dir, openMode)) {
            assertThrows(IllegalStateException.class, queue::createAppender);
        }
        assertFalse(idPoolFile.exists());
    }

    private File queueDir() {
        return tempDir.resolve("queue").toFile();
    }

    private static void appendTo(final File dir, final OpenMode openMode, final String entry) {
        try (final Queue queue = Queue.open(dir, openMode)) {
            final Appender appender = queue.createAppender();
            appender.append(entry.getBytes());
        }
    }

    private static List<String> pollAll(final Poller poller) {
        final List<String> entries = new ArrayList<>();
        while (poller.poll((index, buffer, offset, length) -> {
            entries.add(buffer.getStringWithoutLengthAscii(offset, length));
            return Move.NEXT;
        }) > 0) {
            //poll until no more entries
        }
        return entries;
    }
}
