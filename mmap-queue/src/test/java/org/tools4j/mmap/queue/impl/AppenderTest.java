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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link Appender} and {@link AppenderImpl}.
 */
class AppenderTest {

    private static final long MAX_WAIT_MILLIS = 10_000;
    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int MAX_ENTRY_LENGTH = REGION_SIZE - Integer.BYTES;

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
    void resumingAppenderMovesToNextRegionIfLastEntryFillsRegion(final int lastEntryLength) {
        assertResumeAfterEntryOfLength(smallConfig(false), lastEntryLength);
    }

    static IntStream regionBoundaryLengths() {
        //leaving 8 bytes (too few for the next entry), 4 bytes (padded to full region) and none (max entry length)
        return IntStream.of(REGION_SIZE - 12, REGION_SIZE - 8, MAX_ENTRY_LENGTH);
    }

    @ParameterizedTest(name = "lastEntryLength={0}")
    @ValueSource(ints = {0, 1, 3, 4, 5, 7, 8, 9})
    void resumingAppenderAppendsAfterLastEntryOfAnyLength(final int lastEntryLength) {
        assertResumeAfterEntryOfLength(smallConfig(false), lastEntryLength);
    }

    @Test
    void resumingAppenderAppendsAfterLastEntryInRolledPayloadFile() {
        final File dir = tempDir.resolve("queue").toFile();
        //one entry per region, two regions per payload file
        final int length = REGION_SIZE / 2;
        final List<String> expected = new ArrayList<>();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, smallConfig(true))) {
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
        //the previous owner's last entry is in region 4, that is, in the third rolled payload file
        final File thirdPayloadFile = new File(dir, dir.getName() + "_dat_0_2.mmq");
        assertTrue(thirdPayloadFile.isFile(), () -> "missing " + thirdPayloadFile + " in " +
                Arrays.toString(dir.list()));
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

    @ParameterizedTest(name = "asyncMapping={0}")
    @ValueSource(booleans = {false, true})
    void ensureCapacityBeyondRegionEndMovesEncodedDataToNextRegion(final boolean asyncMapping) {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, payloadRegionCacheConfig(asyncMapping, 4, true))) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, fillerLeavingBytesInRegion(128)));
                final String head = filler(100, 1);
                final String tail = filler(400, 2);
                try (final AppendingContext context = appender.appending(head.length())) {
                    context.buffer().putStringWithoutLengthAscii(0, head);
                    context.ensureCapacity(head.length() + tail.length());
                    context.buffer().putStringWithoutLengthAscii(head.length(), tail);
                    context.commit(head.length() + tail.length());
                }
                expected.add(head + tail);
                expected.add(append(appender, "last"));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void ensureCapacityWithinRegionKeepsEncodedDataWithoutRegionCache() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, payloadRegionCacheConfig(false, 1, false))) {
            try (final Appender appender = queue.createAppender()) {
                try (final AppendingContext context = appender.appending(4)) {
                    context.buffer().putStringWithoutLengthAscii(0, "head");
                    context.ensureCapacity(2);
                    context.ensureCapacity(8);
                    context.buffer().putStringWithoutLengthAscii(4, "tail");
                    context.commit(8);
                }
            }
            assertEquals(List.of("headtail"), pollAll(queue.createPoller()));
        }
    }

    @ParameterizedTest(name = "asyncMapping={0}, cacheSize={1}, deferUnmapping={2}")
    @CsvSource({
            "false, 1, true",   //no region cache
            "false, 4, false",  //previous region unmapped when moving to the next one
            "true, 2, true",    //previous region evicted by map-ahead regions
            "true, 4, false"    //previous region unmapped when moving to the next one
    })
    void ensureCapacityBeyondRegionEndThrowsIfPreviousRegionIsNotKeptMapped(final boolean asyncMapping,
                                                                            final int cacheSize,
                                                                            final boolean deferUnmapping) {
        final File dir = tempDir.resolve("queue").toFile();
        final QueueConfig config = payloadRegionCacheConfig(asyncMapping, cacheSize, deferUnmapping);
        try (final Queue queue = Queue.open(dir, OpenMode.CREATE, config)) {
            final List<String> expected = new ArrayList<>();
            try (final Appender appender = queue.createAppender()) {
                expected.add(append(appender, fillerLeavingBytesInRegion(128)));
                try (final AppendingContext context = appender.appending(4)) {
                    context.buffer().putStringWithoutLengthAscii(0, "head");
                    assertThrows(IllegalStateException.class, () -> context.ensureCapacity(500));
                    context.commit(4);
                }
                expected.add("head");
                expected.add(append(appender, "last"));
            }
            assertEquals(expected, pollAll(queue.createPoller()));
        }
    }

    @Test
    void ensureCapacityOfClosedContextThrows() {
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile())) {
            try (final Appender appender = queue.createAppender()) {
                final AppendingContext context = appender.appending(4);
                context.abort();
                assertThrows(IllegalStateException.class, () -> context.ensureCapacity(8));
            }
        }
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
            assertEquals(5, appender.append(new UnsafeBuffer(bytes), 2, 5));
            assertEquals(List.of("xxabcdexx", "abcde", "abcde", "abc", "abcde", "abcde"),
                    pollAll(queue.createPoller()));
        }
    }

    @Test
    void invalidCommitLengthThrowsAndClosesContext() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext negative = appender.appending(4);
            assertThrows(IllegalArgumentException.class, () -> negative.commit(-1));
            assertTrue(negative.isClosed());

            final AppendingContext tooLong = appender.appending(4);
            assertThrows(IllegalArgumentException.class, () -> tooLong.commit(5));
            assertTrue(tooLong.isClosed());

            final AppendingContext aborted = appender.appending(4);
            aborted.abort();
            assertThrows(IllegalStateException.class, () -> aborted.commit(4));

            append(appender, "ok");
            assertEquals(List.of("ok"), pollAll(queue.createPoller()));
        }
    }

    @Test
    void appendingWithUnclosedContextAbortsItAndThrows() {
        final File dir = tempDir.resolve("queue").toFile();
        try (final Queue queue = Queue.open(dir);
             final Appender appender = queue.createAppender()) {
            final AppendingContext context = appender.appending(4);
            context.buffer().putStringWithoutLengthAscii(0, "lost");
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
            try (final AppendingContext context = appender.appending(4)) {
                assertThrows(IllegalArgumentException.class, () -> context.ensureCapacity(MAX_ENTRY_LENGTH + 1));
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

    /** Filler entry that leaves the given number of bytes in its payload region, including the next length prefix */
    private static String fillerLeavingBytesInRegion(final int bytes) {
        return filler(REGION_SIZE - bytes - Integer.BYTES, 0);
    }

    private static QueueConfig payloadRegionCacheConfig(final boolean asyncMapping,
                                                        final int cacheSize,
                                                        final boolean deferUnmapping) {
        return QueueConfig.configure(smallConfig(false))
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
                .minHeaderFileSize(0)
                .maxHeaderFileSize(16L * REGION_SIZE)
                .minPayloadFileSize(0)
                .maxPayloadFileSize(2L * REGION_SIZE)
                .rollPayloadFiles(rollPayloadFiles)
                .toImmutableQueueConfig();
    }

    private static void abortAppending(final Appender appender, final String entry) {
        final AppendingContext context = appender.appending(entry.length());
        context.buffer().putStringWithoutLengthAscii(0, entry);
        context.abort();
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
            entries.add(buffer.getStringWithoutLengthAscii(offset, length));
            return Move.NEXT;
        }) > 0) {
            //poll until no more entries
        }
        return entries;
    }
}
