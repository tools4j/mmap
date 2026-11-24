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
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link Appender} and {@link AppenderImpl}.
 */
class AppenderTest {

    private static final long MAX_WAIT_MILLIS = 10_000;
    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    /** Payload overlap size of the small config, as large as its region size */
    private static final int PAYLOAD_OVERLAP_SIZE = REGION_SIZE;
    private static final int SAFE_ENTRY_LENGTH = PAYLOAD_OVERLAP_SIZE + Integer.BYTES;
    private static final int MAX_ENTRY_LENGTH = REGION_SIZE + PAYLOAD_OVERLAP_SIZE - Integer.BYTES;
    /** Entries of a queue spanning three header regions */
    private static final int LONG_QUEUE_ENTRIES = 3 * REGION_SIZE / Headers.HEADER_LENGTH + 7;

    @TempDir
    Path tempDir;

    @Test
    void appenderReusingIdAppendsAfterEntriesOfPreviousOwner() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            try (final Appender appender = queue.createAppender()) {
                append(appender, "first");
                append(appender, "second entry");
            }
            //the new appender reuses the released ID, and must not overwrite the previous owner's last entry
            try (final Appender appender = queue.createAppender()) {
                append(appender, "third");
            }
        }
        try (final Queue queue = Queue.open(dir)) {
            try (final Appender appender = queue.createAppender()) {
                append(appender, "fourth");
            }
            assertEquals(List.of("first", "second entry", "third", "fourth"), pollAll(queue.createPoller()));
        }
    }

    @ParameterizedTest(name = "rollPayloadFiles={0}")
    @ValueSource(booleans = {true, false})
    void appenderReusingIdWithLastEntryAtEndOfLongQueueAppendsAfterIt(final boolean rollPayloadFiles) {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, longQueueConfig(rollPayloadFiles))) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                appendLongQueue(appender, expected, 0, LONG_QUEUE_ENTRIES);
            }
            try (final Appender appender = queue.createAppender()) {
                assertEquals(LONG_QUEUE_ENTRIES, appender.append(bytes(expected, "resumed")));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @ParameterizedTest(name = "rollPayloadFiles={0}")
    @ValueSource(booleans = {true, false})
    void appenderReusingIdWithLastEntryInMiddleOfLongQueueAppendsAfterIt(final boolean rollPayloadFiles) {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, longQueueConfig(rollPayloadFiles))) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender0 = queue.createAppender()) {
                try (final Appender appender1 = queue.createAppender()) {
                    appendLongQueue(appender0, expected, 0, LONG_QUEUE_ENTRIES / 2);
                    append(appender1, filler(100, 1));
                    expected.add(filler(100, 1));
                }
                appendLongQueue(appender0, expected, LONG_QUEUE_ENTRIES / 2 + 1, LONG_QUEUE_ENTRIES);
                //reuses the ID of appender 1, whose only entry is in the middle of the queue
                try (final Appender appender = queue.createAppender()) {
                    assertEquals(LONG_QUEUE_ENTRIES, appender.append(bytes(expected, "resumed")));
                }
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @ParameterizedTest(name = "rollPayloadFiles={0}")
    @ValueSource(booleans = {true, false})
    void appenderWithNewIdJoiningLongQueueAppendsAtEnd(final boolean rollPayloadFiles) {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, longQueueConfig(rollPayloadFiles))) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender0 = queue.createAppender()) {
                appendLongQueue(appender0, expected, 0, LONG_QUEUE_ENTRIES);
                //new ID without payload file
                try (final Appender appender = queue.createAppender()) {
                    assertEquals(LONG_QUEUE_ENTRIES, appender.append(bytes(expected, "joined")));
                }
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void appenderReusingIdWithOnlyAbortedEntryAppendsAtEnd() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, longQueueConfig(true))) {
            final List<String> expected = new ArrayList<>();
            final Appender aborting = queue.createAppender();
            try (final Appender appender1 = queue.createAppender()) {
                abortAppending(aborting, "aborted");
                aborting.close();
                assertTrue(new QueueFiles(dir).firstPayloadFile(0, true).exists(), "payload file of aborting ID");
                appendLongQueue(appender1, expected, 0, LONG_QUEUE_ENTRIES);
                //reuses the ID of the aborting appender, which has a payload file but no entries
                try (final Appender appender = queue.createAppender()) {
                    assertEquals(LONG_QUEUE_ENTRIES, appender.append(bytes(expected, "reused")));
                }
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void sequentialThenConcurrentThenSequentialAppendersWriteAllRecordsInOrder() throws Exception {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            appendSequentially(queue, 1, 3);
            appendConcurrently(queue, 4, 6);
            appendSequentially(queue, 7, 9);
            assertEquals(records(1, 9), pollAll(queue.createPoller()));
        }
    }

    @Test
    void sequentialThenConcurrentThenSequentialAppendersWithQueueReopenedBetweenPhases() throws Exception {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            appendSequentially(queue, 1, 3);
        }
        try (final Queue queue = Queue.open(dir)) {
            appendConcurrently(queue, 4, 6);
        }
        try (final Queue queue = Queue.open(dir)) {
            appendSequentially(queue, 7, 9);
        }
        try (final Queue queue = Queue.open(dir)) {
            assertEquals(records(1, 9), pollAll(queue.createPoller()));
        }
    }

    @Test
    void reusingMiddleIdWhileOtherAppendersStayOpen() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            final Appender a = queue.createAppender();
            final Appender b = queue.createAppender();
            final Appender c = queue.createAppender();
            append(a, "a1");
            append(b, "b1");
            append(c, "c1");
            b.close();
            //d reuses the ID released by b, and resumes after b's last entry
            final Appender d = queue.createAppender();
            append(d, "d1");
            append(a, "a2");
            append(c, "c2");
            append(d, "d2");
            assertEquals(List.of("a1", "b1", "c1", "d1", "a2", "c2", "d2"), pollAll(queue.createPoller()));
        }
        final String[] payloadFiles = dir.list((d, name) -> name.contains("_dat_"));
        assertEquals(3, payloadFiles == null ? 0 : payloadFiles.length, () -> "payload files: " +
                Arrays.toString(payloadFiles));
    }

    @ParameterizedTest(name = "lastEntryLength={0}")
    @MethodSource("regionBoundaryLengths")
    void resumingAppenderAppendsAfterLastEntryAtRegionEnd(final int lastEntryLength) {
        assertResumeAfterEntryOfLength(longQueueConfig(false), lastEntryLength);
    }

    static IntStream regionBoundaryLengths() {
        //leaving 8 bytes (the next entry crosses the region end), 4 bytes (padded to the region end), none, and the
        //safe and max entry lengths (ending in the next region)
        return IntStream.of(REGION_SIZE - 12, REGION_SIZE - 8, REGION_SIZE - 4, SAFE_ENTRY_LENGTH, MAX_ENTRY_LENGTH);
    }

    @ParameterizedTest(name = "lastEntryLength={0}")
    @ValueSource(ints = {0, 1, 3, 4, 5, 7, 8, 9})
    void resumingAppenderAppendsAfterLastEntryOfAnyLength(final int lastEntryLength) {
        assertResumeAfterEntryOfLength(smallConfig(false), lastEntryLength);
    }

    @Test
    void resumingAppenderAppendsAfterLastEntryInRolledPayloadFile() {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = smallConfig(true);
        final int length = REGION_SIZE / 2;
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            try (final Appender appender = queue.createAppender()) {
                for (int i = 0; i < 5; i++) {
                    expected.add(append(appender, filler(length, i)));
                }
            }
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, filler(length, 5)));
                expected.add(append(appender, "last"));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
        final long previousOwnersLastPosition = payloadPositions(dir, config, 5).get(4);
        assertTrue(previousOwnersLastPosition >= config.maxPayloadFileSize(),
                () -> "previous owner's last entry at " + previousOwnersLastPosition + " is in the first payload file");
    }

    @Test
    void resumingAppenderIgnoresAbortedAppendOfPreviousOwner() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            try (final Appender appender = queue.createAppender()) {
                abortAppending(appender, "aborted before any entry");
            }
            try (final Appender appender = queue.createAppender()) {
                append(appender, "first");
                abortAppending(appender, "aborted after first entry");
            }
            try (final Appender appender = queue.createAppender()) {
                append(appender, "second");
            }
            assertEquals(List.of("first", "second"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void concurrentAppendersRacingForIndicesAppendAllEntriesInOrderPerAppender() throws Exception {
        final int appenders = 4;
        final int entriesPerAppender = 500;
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            final CyclicBarrier start = new CyclicBarrier(appenders);
            final AtomicReference<Throwable> error = new AtomicReference<>();
            final List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < appenders; i++) {
                final int appenderNo = i;
                final Thread thread = new Thread(() -> {
                    try (final Appender appender = queue.createAppender()) {
                        start.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                        for (int j = 0; j < entriesPerAppender; j++) {
                            append(appender, appenderNo + ":" + j);
                        }
                    } catch (final Throwable t) {
                        error.compareAndSet(null, t);
                    }
                }, "appender-" + appenderNo);
                threads.add(thread);
                thread.start();
            }
            joinAll(threads);
            assertNull(error.get(), () -> "appender failed: " + error.get());

            final List<String> entries = pollAll(queue.createPoller());
            assertEquals(appenders * entriesPerAppender, entries.size());
            final int[] nextExpected = new int[appenders];
            for (final String entry : entries) {
                final int separator = entry.indexOf(':');
                final int appenderNo = Integer.parseInt(entry.substring(0, separator));
                assertEquals(nextExpected[appenderNo]++, Integer.parseInt(entry.substring(separator + 1)),
                        () -> "entry out of order: " + entry);
            }
            assertEquals(appenders * entriesPerAppender, queue.createIndexReader().size());
        }
    }

    @ParameterizedTest(name = "asyncMapping={0}, cacheSize={1}, deferUnmapping={2}")
    @CsvSource({
            "false, 1, true",   //no region cache
            "false, 4, false",  //previous region unmapped when moving to the next one
            "false, 4, true",
            "true, 2, true",    //previous region evicted by map-ahead regions
            "true, 4, false",
            "true, 4, true"
    })
    void entriesBeyondSafeLengthStartAtNextRegionOnlyIfNotFitting(final boolean asyncMapping,
                                                                  final int cacheSize,
                                                                  final boolean deferUnmapping) {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = payloadRegionCacheConfig(asyncMapping, cacheSize, deferUnmapping);
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, fillerLeaving128BytesInRegion()));
                //safe length crossing the region end, at R - 128
                try (final AppendingContext context = appender.appending()) {
                    assertEquals(SAFE_ENTRY_LENGTH, context.buffer().capacity());
                    final String entry = filler(SAFE_ENTRY_LENGTH, 1);
                    context.buffer().putTextLatin1(0, entry);
                    context.commit(entry.length());
                    expected.add(entry);
                }
                //at 2R - 120, 120 bytes before the end of the next region's overlap, fitting
                expected.add(append(appender, filler(REGION_SIZE + 100, 2)));
                //at 3R - 16, not fitting, starting at 3R
                expected.add(append(appender, filler(REGION_SIZE + 100, 3)));
                //at 4R + 104, not fitting, starting at 5R
                expected.add(append(appender, filler(MAX_ENTRY_LENGTH, 4)));
                expected.add(append(appender, "last"));
                assertThrows(IllegalArgumentException.class, () -> appender.append(new byte[MAX_ENTRY_LENGTH + 1]));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
        assertEquals(List.of(0L, REGION_SIZE - 128L, 2L * REGION_SIZE - 120, 3L * REGION_SIZE, 5L * REGION_SIZE,
                7L * REGION_SIZE), payloadPositions(dir, config, expected.size()));
    }

    /** The payload positions of the entries from the header file */
    private static List<Long> payloadPositions(final File dir, final QueueConfig config, final int entries) {
        final List<Long> positions = new ArrayList<>();
        try (final DynamicMapping header = Mappings.dynamicMapping(new QueueFiles(dir).headerFile(),
                OpenMode.READ_ONLY, QueueMappingConfigs.headerMappingConfig(config, config.pollerConfig()))) {
            for (int index = 0; index < entries; index++) {
                positions.add(Headers.payloadPosition(Headers.moveAndGetHeader(header, index)));
            }
        }
        return positions;
    }

    static IntStream payloadOverlapSizes() {
        return IntStream.of(4096, 4 * REGION_SIZE);
    }

    @ParameterizedTest(name = "payloadOverlapSize={0}")
    @MethodSource("payloadOverlapSizes")
    void entriesOfSafeLengthCrossRegionAndRolledFileEnds(final int payloadOverlapSize) {
        final File dir = tempDir.resolve("queue").toFile();
        final int regionSize = 4 * REGION_SIZE;
        final long maxFileSize = 2L * regionSize;
        final QueueConfig config = QueueConfig.configure(smallConfig(true))
                .payloadRegionSize(regionSize)
                .payloadOverlapSize(payloadOverlapSize)
                .maxPayloadFileSize(maxFileSize)
                .toImmutableQueueConfig();
        final int safeEntryLength = payloadOverlapSize + Integer.BYTES;
        //spanning three payload files
        final int entries = (int) (2 * maxFileSize / safeEntryLength) + 2;
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            assertEquals(safeEntryLength, queue.layout().safeEntryLength());
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                for (int i = 0; i < entries; i++) {
                    expected.add(append(appender, filler(safeEntryLength, i)));
                }
            }
            assertEquals(expected, pollAll(queue.createPoller()));
            final EntryReader reader = queue.createEntryReader();
            for (int index = 0; index < entries; index++) {
                try (final ReadingContext entry = reader.reading(index)) {
                    assertEquals(expected.get(index), text(entry.buffer()), "entry reader, index " + index);
                }
            }
            final List<String> iterated = new ArrayList<>();
            try (final IterableContext context = queue.createEntryIterator().readingFromFirst()) {
                for (final Entry entry : context) {
                    iterated.add(text(entry.buffer()));
                }
            }
            assertEquals(expected, iterated, "entry iterator");
        }
        //the first two files are full, with the overlap tail of their last region
        for (int fileIndex = 0; fileIndex < 2; fileIndex++) {
            final File payloadFile = new File(dir, "queue_dat_0_" + fileIndex + ".mmq");
            assertEquals(maxFileSize + payloadOverlapSize, payloadFile.length(), payloadFile.getName());
        }
        //no entry started at a region start, none was skipped
        final List<Long> positions = payloadPositions(dir, config, entries);
        for (int index = 1; index < entries; index++) {
            assertEquals(Headers.nextPayloadPosition(positions.get(index - 1), Integer.BYTES + safeEntryLength),
                    positions.get(index), "index " + index);
        }
    }

    @ParameterizedTest(name = "prefillFiles={0}")
    @ValueSource(booleans = {true, false})
    void entriesSpanningSeveralFileExtensionsAreReadBack(final boolean prefillFiles) {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = QueueConfig.configure(longQueueConfig(false))
                .expandHeaderFile(true)
                .appenderConfig(appender -> appender.prefillFiles(prefillFiles))
                .toImmutableQueueConfig();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                appendLongQueue(appender, expected, 0, LONG_QUEUE_ENTRIES);
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @ParameterizedTest(name = "prefillFiles={0}")
    @ValueSource(booleans = {true, false})
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "allocated size measured with du")
    void prefilledFilesAreAllocatedAndOthersSparse(final boolean prefillFiles) throws Exception {
        final File dir = tempDir.resolve("queue").toFile();
        //files much longer than the few entries written, so that only prefilling allocates most of their space; sync
        //mapping, as pre-touching regions mapped ahead would allocate them as well
        final QueueConfig config = QueueConfig.configure(smallConfig(false))
                .mappingStrategy(MappingStrategy.SyncMappingStrategy)
                .expandHeaderFile(true)
                .minHeaderFileSize(8L * REGION_SIZE)
                .maxHeaderFileSize(16L * REGION_SIZE)
                .minPayloadFileSize(8L * REGION_SIZE)
                .maxPayloadFileSize(16L * REGION_SIZE)
                .appenderConfig(appender -> appender.prefillFiles(prefillFiles))
                .toImmutableQueueConfig();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config);
             final Appender appender = queue.createAppender()) {
            append(appender, "first");
            append(appender, "second");
        }
        for (final File file : queueFiles(dir, "_hdr", "_dat")) {
            final long allocated = allocatedBytes(file);
            final String message = file.getName() + " allocated " + allocated + " of " + file.length() + " bytes";
            final long overlapSize = file.getName().contains("_dat") ? PAYLOAD_OVERLAP_SIZE : 0;
            assertEquals(8L * REGION_SIZE + overlapSize, file.length(), message);
            if (prefillFiles) {
                assertTrue(allocated >= file.length(), message);
            } else if (OS.current() == OS.LINUX) {//APFS on macOS allocates the whole mapped file once it is closed
                assertTrue(allocated < file.length() / 2, message);
            }
        }
    }

    private static List<File> queueFiles(final File dir, final String... nameParts) {
        final List<File> files = new ArrayList<>();
        for (final File file : requireNonNull(dir.listFiles())) {
            if (Arrays.stream(nameParts).anyMatch(file.getName()::contains)) {
                files.add(file);
            }
        }
        assertEquals(nameParts.length, files.size(), "files " + files);
        return files;
    }

    private static long allocatedBytes(final File file) throws Exception {
        final Process process = new ProcessBuilder("du", "-k", file.getPath()).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
        assertEquals(0, process.waitFor(), "du exit code");
        return 1024L * Long.parseLong(output.split("\\s+")[0]);
    }

    @Test
    void appendingLastEntryOfFullHeaderFileReturnsItsIndexAndNextAppendThrows() {
        final QueueConfig config = QueueConfig.configure(smallConfig(false))
                .maxHeaderFileSize(REGION_SIZE)
                .rollHeaderFile(false)
                .toImmutableQueueConfig();
        final int capacity = REGION_SIZE / Headers.HEADER_LENGTH;
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile(), OpenMode.CREATE, config);
             final Appender appender = queue.createAppender()) {
            //the last append fails to pre-position the header beyond the full file, after committing its entry
            for (int i = 0; i < capacity; i++) {
                assertEquals(i, appender.append(new byte[]{(byte) i}));
            }
            assertThrows(IllegalArgumentException.class, () -> appender.append(new byte[]{1}));
            assertThrows(IllegalArgumentException.class, () -> appender.append(new byte[]{1}));

            try (final Poller poller = queue.createPoller()) {
                final AtomicLong polled = new AtomicLong();
                while (poller.poll((index, buffer, offset, length) -> {
                    assertEquals(polled.getAndIncrement(), index);
                    assertEquals((byte) index, buffer.getByte(offset));
                    return Move.NEXT;
                }) == Poller.ENTRY_POLLED) {
                    //poll all
                }
                assertEquals(capacity, polled.get());
            }
        }
    }

    @Test
    void creatingAppenderOnLongQueueCreatesNoHeaderFileNorExtendsOne() {
        final File dir = tempDir.resolve("queue").toFile();
        //one header region per rolled file, and sync mapping, so that no file is created ahead asynchronously
        final QueueConfig config = QueueConfig.configure(longQueueConfig(false))
                .maxHeaderFileSize(REGION_SIZE)
                .rollHeaderFile(true)
                .appenderConfig(appender -> appender.headerMappingStrategy(MappingStrategy.SyncMappingStrategy))
                .toImmutableQueueConfig();
        //last index in the fifth header file, just beyond a power of two: galloping used to probe up to the eighth
        final int entries = 4 * REGION_SIZE / Headers.HEADER_LENGTH + 1;
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config);
             final Appender appender = queue.createAppender()) {
            appendLongQueue(appender, expected, 0, entries);
        }
        final List<String> headerFiles = headerFilesWithLengths(dir);
        try (final Queue queue = Queue.open(dir, OpenMode.READ_WRITE, config);
             final Appender ignored = queue.createAppender()) {
            assertEquals(headerFiles, headerFilesWithLengths(dir));
        }
    }

    @Test
    void creatingAppenderOnNonRollingQueueDoesNotExtendHeaderFile() {
        final File dir = tempDir.resolve("queue").toFile();
        //header file growing by one region, and sync mapping, so that no region is mapped ahead asynchronously
        final QueueConfig config = QueueConfig.configure(longQueueConfig(false))
                .maxHeaderFileSize(4L * REGION_SIZE)
                .rollHeaderFile(false)
                .appenderConfig(appender -> appender.headerMappingStrategy(MappingStrategy.SyncMappingStrategy))
                .toImmutableQueueConfig();
        //last index in the third header region, just beyond a power of two: galloping used to probe the fourth
        final int entries = 2 * REGION_SIZE / Headers.HEADER_LENGTH + 1;
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            try (final Appender appender = queue.createAppender()) {
                appendLongQueue(appender, expected, 0, entries);
            }
            final List<String> headerFiles = headerFilesWithLengths(dir);
            try (final Appender appender = queue.createAppender()) {
                assertEquals(headerFiles, headerFilesWithLengths(dir));
                assertEquals(entries, appender.append(bytes(expected, "appended")));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void idleAppenderCatchesUpWithEntriesOfAnotherAppenderAcrossHeaderFiles() {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = QueueConfig.configure(longQueueConfig(false))
                .maxHeaderFileSize(REGION_SIZE)
                .rollHeaderFile(true)
                .toImmutableQueueConfig();
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config);
             final Appender idle = queue.createAppender();
             final Appender busy = queue.createAppender()) {
            assertEquals(0, idle.append(bytes(expected, "idle-0")));
            appendLongQueue(busy, expected, 1, LONG_QUEUE_ENTRIES);
            assertEquals(LONG_QUEUE_ENTRIES, idle.append(bytes(expected, "idle-1")));
            assertEquals(LONG_QUEUE_ENTRIES + 1, busy.append(bytes(expected, "busy")));
            assertEquals(LONG_QUEUE_ENTRIES + 2, idle.append(bytes(expected, "idle-2")));
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void appenderMovesHeaderBackToEndIndexAfterCatchUpSearchThrew() {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = smallConfig(false);
        final QueueFiles files = new QueueFiles(dir);
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config);
             final AppenderIdPool idPool = new AppenderIdPool(files.idPoolFile());
             final FailingProbeMappings mappings = new FailingProbeMappings(
                     AppenderMappings.create(files, idPool, config, config.appenderConfig()));
             final Appender failing = new AppenderImpl("queue", mappings, config, () -> {}, LifecycleListener.NO_OP);
             final Appender other = queue.createAppender()) {
            assertEquals(0, failing.append(bytes(expected, "failing-0")));
            appendLongQueue(other, expected, 1, 6);

            //the catch-up search from index 1 throws after probing an empty slot beyond the end index 6
            mappings.failProbesOnceEmptySlotProbed();
            assertThrows(UncheckedIOException.class, () -> failing.append(new byte[]{1}));
            assertTrue(Headers.indexForHeaderPosition(mappings.header().position()) > 6);

            //claiming that slot would leave a gap in the headers, ending the queue for readers
            mappings.stopFailingProbes();
            assertEquals(6, failing.append(bytes(expected, "failing-6")));
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    /** Appender mappings whose header mapping fails probes on request, delegating to the given mappings otherwise */
    private static final class FailingProbeMappings implements AppenderMappings {
        final AppenderMappings mappings;
        final DynamicMutableMapping header;
        boolean failProbes;
        boolean emptySlotProbed;

        FailingProbeMappings(final AppenderMappings mappings) {
            this.mappings = requireNonNull(mappings);
            final DynamicMutableMapping realHeader = mappings.header();
            this.header = mock(DynamicMutableMapping.class, delegatesTo(realHeader));
            doAnswer(invocation -> {
                if (failProbes && emptySlotProbed) {
                    throw new UncheckedIOException(new IOException("Injected probe failure"));
                }
                final boolean moved = realHeader.moveToExisting(invocation.getArgument(0), invocation.getArgument(1));
                emptySlotProbed = moved && realHeader.buffer().getLongAcquire(0) == Headers.NULL_HEADER;
                return moved;
            }).when(header).moveToExisting(anyLong(), anyInt());
        }

        void failProbesOnceEmptySlotProbed() {
            failProbes = true;
            emptySlotProbed = false;
        }

        void stopFailingProbes() {
            failProbes = false;
        }

        @Override
        public int appenderId() {
            return mappings.appenderId();
        }

        @Override
        public DynamicMutableMapping header() {
            return header;
        }

        @Override
        public DynamicMutableMapping payload() {
            return mappings.payload();
        }

        @Override
        public boolean payloadFileExisted() {
            return mappings.payloadFileExisted();
        }

        @Override
        public boolean isClosed() {
            return mappings.isClosed();
        }

        @Override
        public void close() {
            mappings.close();
        }
    }

    private static List<String> headerFilesWithLengths(final File dir) {
        final List<String> files = new ArrayList<>();
        for (final File file : requireNonNull(dir.listFiles())) {
            if (file.getName().contains("_hdr")) {
                files.add(file.getName() + ":" + file.length());
            }
        }
        files.sort(null);
        return files;
    }

    @Test
    void appendOverloadsAppendTheGivenBytes() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final byte[] bytes = "xxabcdexx".getBytes(StandardCharsets.US_ASCII);
            assertEquals(0, appender.append(bytes));
            assertEquals(1, appender.append(bytes, 2, 5));
            assertEquals(2, appender.append(ByteBuffer.wrap(bytes, 2, 5)));
            assertEquals(3, appender.append(ByteBuffer.wrap(bytes, 2, 5), 3));
            assertEquals(4, appender.append(ByteBuffer.wrap(bytes), 2, 5));
            final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
            buffer.wrap(bytes);
            assertEquals(5, appender.append(buffer, 2, 5));
            assertEquals(List.of("xxabcdexx", "abcde", "abcde", "abc", "abcde", "abcde"),
                    pollAll(queue.createPoller()));
        }
    }

    @Test
    void invalidCommitLengthThrowsAndKeepsContextOpen() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext context = appender.appending(4);
            context.buffer().putTextLatin1(0, "kept");
            assertThrows(IllegalArgumentException.class, () -> context.commit(-1));
            assertThrows(IllegalArgumentException.class, () -> context.commit(5));
            assertFalse(context.isClosed());
            assertEquals("kept", text(context.buffer()));
            context.commit(4);
            assertEquals(List.of("kept"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void committedContextIsClosedRejectingCommitAndIgnoringAbort() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext context = appender.appending(4);
            context.buffer().putTextLatin1(0, "once");
            assertEquals(0, context.commit(4));
            assertTrue(context.isClosed());
            assertEquals(0, context.buffer().capacity());
            assertThrows(IllegalStateException.class, () -> context.commit(4));
            assertThrows(IllegalStateException.class, () -> context.commit(-1));
            context.abort();
            context.close();
            assertEquals(List.of("once"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void abortedContextIsClosedRejectingCommitAndIgnoringAbort() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext context = appender.appending(4);
            context.buffer().putTextLatin1(0, "lost");
            context.abort();
            assertTrue(context.isClosed());
            assertEquals(0, context.buffer().capacity());
            context.abort();
            context.close();
            assertThrows(IllegalStateException.class, () -> context.commit(4));
            append(appender, "ok");
            assertEquals(List.of("ok"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void closingAppenderAbortsOpenContext() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            final Appender appender = queue.createAppender();
            final AppendingContext context = appender.appending(4);
            context.buffer().putTextLatin1(0, "lost");
            appender.close();
            assertTrue(context.isClosed());
            assertEquals(0, context.buffer().capacity());
            assertThrows(IllegalStateException.class, () -> context.commit(4));
            assertEquals(0, queue.createIndexReader().size());
        }
    }

    @Test
    void appendingWithUnclosedContextAbortsItAndThrows() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext context = appender.appending(4);
            context.buffer().putTextLatin1(0, "lost");
            assertThrows(IllegalStateException.class, () -> appender.appending(4));
            assertTrue(context.isClosed());
            append(appender, "ok");
            assertEquals(List.of("ok"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void capacityExceedingMaxEntrySizeIsRejected() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, smallConfig(false));
             final Appender appender = queue.createAppender()) {
            assertThrows(IllegalArgumentException.class, () -> appender.appending(MAX_ENTRY_LENGTH + 1));
            assertThrows(IllegalArgumentException.class, () -> appender.append(new byte[MAX_ENTRY_LENGTH + 1]));
            try (final AppendingContext context = appender.appending()) {
                assertThrows(IllegalArgumentException.class, () -> context.commit(SAFE_ENTRY_LENGTH + 1));
            }
            append(appender, filler(MAX_ENTRY_LENGTH, 0));
            assertEquals(1, queue.createIndexReader().size());
        }
    }

    @Test
    void closedAppenderRejectsAppending() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir)) {
            final Appender appender = queue.createAppender();
            appender.close();
            assertTrue(appender.isClosed());
            assertThrows(IllegalStateException.class, () -> appender.appending(4));
            assertThrows(IllegalStateException.class, () -> appender.append(new byte[]{1}));
            appender.close();
            assertTrue(appender.isClosed());
        }
    }

    /** Filler entry that leaves 128 bytes in its payload region, including the next length prefix */
    private static String fillerLeaving128BytesInRegion() {
        return filler(REGION_SIZE - 128 - Integer.BYTES, 0);
    }

    private static QueueConfig payloadRegionCacheConfig(final boolean asyncMapping,
                                                        final int cacheSize,
                                                        final boolean deferUnmapping) {
        return QueueConfig.configure(smallConfig(true))
                .appenderConfig(appender -> appender.payloadMappingStrategy(strategy -> strategy
                        .asyncMapping(asyncMapping)
                        .asyncUnmapping(false)
                        .cacheSize(cacheSize)
                        .deferUnmapping(deferUnmapping)))
                .toImmutableQueueConfig();
    }

    /**
     * An appender appends an entry of the given length and closes; a new appender reusing the ID appends two more
     * entries, and all three must be read back intact.
     */
    private void assertResumeAfterEntryOfLength(final QueueConfig config, final int lastEntryLength) {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, filler(lastEntryLength, 0)));
            }
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, filler(16, 1)));
                expected.add(append(appender, "last"));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    private static QueueConfig smallConfig(final boolean rollPayloadFiles) {
        return QueueConfig.configure()
                .headerRegionSize(REGION_SIZE)
                .payloadRegionSize(REGION_SIZE)
                .payloadOverlapSize(PAYLOAD_OVERLAP_SIZE)
                .minHeaderFileSize(0)
                .maxHeaderFileSize(16L * REGION_SIZE)
                .minPayloadFileSize(0)
                .maxPayloadFileSize(2L * REGION_SIZE)
                .rollPayloadFiles(rollPayloadFiles)
                .toImmutableQueueConfig();
    }

    /** Config for long queues spanning several header regions and payload regions (or files if rolled) */
    private static QueueConfig longQueueConfig(final boolean rollPayloadFiles) {
        return QueueConfig.configure(smallConfig(rollPayloadFiles))
                .maxPayloadFileSize(rollPayloadFiles ? 2L * REGION_SIZE : 64L * REGION_SIZE)
                .toImmutableQueueConfig();
    }

    /** Appends entries with the given indices, adding them to the expected entries */
    private static void appendLongQueue(final Appender appender,
                                        final List<String> expected,
                                        final int fromIndex,
                                        final int toIndex) {
        for (int index = fromIndex; index < toIndex; index++) {
            assertEquals(index, appender.append(bytes(expected, record(index))));
        }
    }

    /** Adds the entry to the expected entries and returns its bytes */
    private static byte[] bytes(final List<String> expected, final String entry) {
        expected.add(entry);
        return entry.getBytes(StandardCharsets.US_ASCII);
    }

    private static void abortAppending(final Appender appender, final String entry) {
        final AppendingContext context = appender.appending(entry.length());
        context.buffer().putTextLatin1(0, entry);
        context.abort();
    }

    private static String text(final MemoryBuffer buffer) {
        return buffer.getTextLatin1(0, buffer.capacity());
    }

    private static String filler(final int length, final int seed) {
        final char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('a' + (seed + i) % 26);
        }
        return new String(chars);
    }

    private static void joinAll(final List<Thread> threads) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        for (final Thread thread : threads) {
            thread.join(Math.max(1, deadline - System.currentTimeMillis()));
            assertFalse(thread.isAlive(), thread.getName() + " still running");
        }
    }

    /** Each record is appended by a new appender, which is closed again before the next one is opened */
    private static void appendSequentially(final Queue queue, final int first, final int last) {
        for (int record = first; record <= last; record++) {
            try (final Appender appender = queue.createAppender()) {
                append(appender, record(record));
            }
        }
    }

    /**
     * One appender per record, all open at the same time in their own threads, appending one record each in turn;
     * the appenders are closed only when all records have been appended.
     */
    private static void appendConcurrently(final Queue queue, final int first, final int last) throws Exception {
        final int appenders = last - first + 1;
        final CountDownLatch allOpen = new CountDownLatch(appenders);
        final CountDownLatch[] turns = new CountDownLatch[appenders + 1];
        for (int i = 0; i < turns.length; i++) {
            turns[i] = new CountDownLatch(1);
        }
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < appenders; i++) {
            final int turn = i;
            final Thread thread = new Thread(() -> {
                try (final Appender appender = queue.createAppender()) {
                    allOpen.countDown();
                    await(turns[turn]);
                    append(appender, record(first + turn));
                    turns[turn + 1].countDown();
                    await(turns[appenders]);//all appended, close now
                } catch (final Throwable t) {
                    error.compareAndSet(null, t);
                }
            }, "appender-" + (first + turn));
            threads.add(thread);
            thread.start();
        }
        await(allOpen);
        turns[0].countDown();
        joinAll(threads);
        assertNull(error.get(), () -> "appender failed: " + error.get());
    }

    private static void await(final CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS), "timeout waiting for latch");
    }

    private static String record(final int record) {
        return "record-" + record;
    }

    private static List<String> records(final int first, final int last) {
        final List<String> records = new ArrayList<>();
        for (int record = first; record <= last; record++) {
            records.add(record(record));
        }
        return records;
    }

    private static String append(final Appender appender, final String entry) {
        appender.append(entry.getBytes(StandardCharsets.US_ASCII));
        return entry;
    }

    private static List<String> pollAll(final Poller poller) {
        final List<String> entries = new ArrayList<>();
        while (poller.poll((index, buffer, offset, length) -> {
            entries.add(buffer.getTextLatin1(offset, length));
            return Move.NEXT;
        }) > 0) {
            //poll until no more entries
        }
        return entries;
    }
}
