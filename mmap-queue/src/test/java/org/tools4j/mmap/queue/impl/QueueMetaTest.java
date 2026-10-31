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
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link QueueMetaFile} and {@link QueueLayoutConfigImpl}: the stored queue layout overrides the
 * configuration of later appenders and readers.
 */
class QueueMetaTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int ENTRY_LENGTH = 100;
    private static final int ENTRIES = 5000;

    /** Settings differing from the defaults, with small rolled files so that file indices depend on them */
    private static final QueueConfig CREATOR_CONFIG = QueueConfig.configure()
            .headerRegionSize(REGION_SIZE)
            .payloadRegionSize(REGION_SIZE)
            .minHeaderFileSize(0)
            .maxHeaderFileSize(REGION_SIZE)
            .minPayloadFileSize(0)
            .maxPayloadFileSize(4L * REGION_SIZE)
            .rollHeaderFile(true)
            .rollPayloadFiles(true)
            .toImmutableQueueConfig();

    @TempDir
    Path tempDir;

    @Test
    void metaFileStartsWithMagicAndVersion() throws IOException {
        Queue.open(queueDir(), OpenMode.CREATE).close();
        final ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(metaFile().toPath()))
                .order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(QueueMetaFile.LENGTH, bytes.capacity());
        assertEquals("t4jmmapq", new String(Arrays.copyOf(bytes.array(), 8), StandardCharsets.US_ASCII));
        assertEquals(QueueMetaFile.VERSION, bytes.getInt(8));
    }

    @Test
    void metaFileRoundTripsCreatorSettings() {
        Queue.open(queueDir(), OpenMode.CREATE, CREATOR_CONFIG).close();
        assertEquals(QueueLayoutConfigImpl.of(CREATOR_CONFIG), QueueMetaFile.read(metaFile()));
        assertNull(QueueLayoutConfigs.diff(CREATOR_CONFIG, CREATOR_CONFIG));
    }

    @Test
    void layoutToStringListsAllValues() {
        assertEquals("QueueLayoutConfigImpl:headerRegionSize=" + REGION_SIZE +
                        "|payloadRegionSize=" + REGION_SIZE +
                        "|minHeaderFileSize=0" +
                        "|maxHeaderFileSize=" + REGION_SIZE +
                        "|minPayloadFileSize=0" +
                        "|maxPayloadFileSize=" + 4L * REGION_SIZE +
                        "|expandHeaderFile=" + CREATOR_CONFIG.expandHeaderFile() +
                        "|expandPayloadFiles=" + CREATOR_CONFIG.expandPayloadFiles() +
                        "|rollHeaderFile=true" +
                        "|rollPayloadFiles=true",
                QueueLayoutConfigImpl.of(CREATOR_CONFIG).toString());
    }

    @Test
    void metaSettingsOverrideConfigOfLaterAppendersAndReaders() {
        final File dir = queueDir();
        appendEntries(dir, OpenMode.CREATE, CREATOR_CONFIG, 0, ENTRIES);
        appendEntries(dir, OpenMode.READ_WRITE, QueueConfig.getDefault(), ENTRIES, 2 * ENTRIES);
        try (final Queue queue = Queue.open(dir, OpenMode.READ_ONLY)) {
            assertEquals(expectedEntries(0, 2 * ENTRIES), pollAll(queue.createPoller()));
            assertEquals(2 * ENTRIES, queue.createIndexReader().size());
        }
    }

    @Test
    void maxAppendersLimitsQueueInstanceAndIsNotPartOfLayout() {
        final File dir = queueDir();
        final QueueConfig singleAppenderConfig = QueueConfig.configure().maxAppenders(1).toImmutableQueueConfig();
        try (final Queue single = Queue.open(dir, OpenMode.CREATE, singleAppenderConfig);
             final Queue multi = Queue.open(dir, OpenMode.READ_WRITE)) {
            assertEquals(QueueLayoutConfigImpl.of(QueueConfig.getDefault()), QueueMetaFile.read(metaFile()));
            single.createAppender();
            assertThrows(IllegalStateException.class, single::createAppender);
            multi.createAppender();
            multi.createAppender();
        }
    }

    @Test
    void lazyReaderUsesMetaSettingsOfQueueCreatedLater() {
        final File dir = queueDir();
        try (final Queue queue = Queue.open(dir, OpenMode.READ_ONLY_LAZY)) {
            final Poller poller = queue.createPoller();
            assertEquals(List.of(), pollAll(poller));
            assertEquals(0, queue.createIndexReader().size());
            appendEntries(dir, OpenMode.CREATE, CREATOR_CONFIG, 0, ENTRIES);
            assertEquals(expectedEntries(0, ENTRIES), pollAll(poller));
            assertEquals(ENTRIES, queue.createIndexReader().size());
        }
    }

    @Test
    void recreatingQueueRewritesMetaFile() {
        final File dir = queueDir();
        appendEntries(dir, OpenMode.CREATE, CREATOR_CONFIG, 0, 1);
        appendEntries(dir, OpenMode.RECREATE, QueueConfig.getDefault(), 1, 2);
        assertEquals(QueueLayoutConfigImpl.of(QueueConfig.getDefault()), QueueMetaFile.read(metaFile()));
        try (final Queue queue = Queue.open(dir, OpenMode.READ_ONLY)) {
            assertEquals(expectedEntries(1, 2), pollAll(queue.createPoller()));
        }
    }

    @Test
    void incompleteMetaFileIsTreatedAsMissingQueue() throws IOException {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE, CREATOR_CONFIG).close();
        writeMetaBytes(0, new byte[8]);
        assertNull(QueueMetaFile.read(metaFile()));
        assertThrows(IllegalArgumentException.class, () -> Queue.open(dir, OpenMode.READ_ONLY));
        assertThrows(IllegalArgumentException.class, () -> Queue.open(dir, OpenMode.READ_WRITE));
        Queue.open(dir, OpenMode.OPEN_OR_CREATE).close();
        assertEquals(QueueLayoutConfigImpl.of(QueueConfig.getDefault()), QueueMetaFile.read(metaFile()));
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"READ_ONLY", "READ_ONLY_LAZY", "READ_WRITE", "OPEN_OR_CREATE"})
    void foreignMetaFileIsRejected(final OpenMode openMode) throws IOException {
        Queue.open(queueDir(), OpenMode.CREATE).close();
        writeMetaBytes(0, "t4jmmapx".getBytes(StandardCharsets.US_ASCII));
        assertThrows(IllegalStateException.class, () -> Queue.open(queueDir(), openMode));
    }

    @ParameterizedTest
    @EnumSource(value = OpenMode.class, names = {"READ_ONLY", "READ_ONLY_LAZY", "READ_WRITE", "OPEN_OR_CREATE"})
    void unsupportedVersionIsRejected(final OpenMode openMode) throws IOException {
        Queue.open(queueDir(), OpenMode.CREATE).close();
        writeMetaBytes(8, ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0, QueueMetaFile.VERSION + 1).array());
        final IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> Queue.open(queueDir(), openMode));
        assertNotNull(exception.getMessage());
    }

    private File queueDir() {
        return tempDir.resolve("queue").toFile();
    }

    private File metaFile() {
        return new QueueFiles(queueDir()).metaFile();
    }

    private void writeMetaBytes(final long position, final byte[] bytes) throws IOException {
        try (final RandomAccessFile raf = new RandomAccessFile(metaFile(), "rw")) {
            raf.getChannel().write(ByteBuffer.wrap(bytes), position);
        }
    }

    private static void appendEntries(final File dir,
                                      final OpenMode openMode,
                                      final QueueConfig config,
                                      final int from,
                                      final int to) {
        try (final Queue queue = Queue.open(dir, openMode, config)) {
            final Appender appender = queue.createAppender();
            for (int i = from; i < to; i++) {
                appender.append(entry(i).getBytes(StandardCharsets.US_ASCII));
            }
        }
    }

    private static String entry(final int i) {
        return String.format("%0" + ENTRY_LENGTH + "d", i);
    }

    private static List<String> expectedEntries(final int from, final int to) {
        final List<String> entries = new ArrayList<>();
        for (int i = from; i < to; i++) {
            entries.add(entry(i));
        }
        return entries;
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
