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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link Poller} and {@link PollerImpl}.
 */
class PollerTest {

    private Path tempDir;
    private File queueDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(PollerTest.class.getSimpleName());
        tempDir.toFile().deleteOnExit();
        queueDir = new File(tempDir.toFile(), "testQ");
    }

    @AfterEach
    void tearDown() {
        try {
            FileUtil.deleteRecursively(tempDir.toFile());
        } catch (final IOException e) {
            System.err.println("Deleting temp files failed: tempDir=" + tempDir + ", e=" + e);
        }
    }

    @Test
    void seekNegativeIndexThrows() {
        try (final Queue queue = Queue.open(queueDir);
             final Poller poller = queue.createPoller()) {
            assertThatThrownBy(() -> poller.seek(Index.NULL)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> poller.seek(-5)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> poller.seek(Move.FIRST)).isInstanceOf(IllegalArgumentException.class);
            assertThat(poller.nextIndex()).isEqualTo(Index.FIRST);
        }
    }

    @Test
    void seekNonNegativeIndexSetsNextIndex() {
        try (final Queue queue = Queue.open(queueDir);
             final Poller poller = queue.createPoller()) {
            poller.seek(5);
            assertThat(poller.nextIndex()).isEqualTo(5);
            poller.seek(Index.LAST);
            assertThat(poller.nextIndex()).isEqualTo(Index.LAST);
            poller.seek(Index.END);
            assertThat(poller.nextIndex()).isEqualTo(Index.END);
            poller.seek(Index.FIRST);
            assertThat(poller.nextIndex()).isEqualTo(Index.FIRST);
        }
    }

    @Test
    void pollReturnsPendingOpenForMissingHeaderFile() {
        try (final Queue queue = Queue.open(queueDir);
             final Poller poller = queue.createPoller()) {
            assertThat(poller.poll((index, buffer, offset, length) -> Move.NEXT)).isEqualTo(Poller.PENDING_OPEN);
            assertThat(poller.poll((index, buffer, offset, length) -> Move.NEXT)).isEqualTo(Poller.PENDING_OPEN);
        }
    }

    @Test
    void pollReturnsPendingOpenForEmptyHeaderFile() throws IOException {
        pollReturnsPendingOpenForHeaderFileOfLength(0);
    }

    @Test
    void pollReturnsPendingOpenForTruncatedHeaderFile() throws IOException {
        pollReturnsPendingOpenForHeaderFileOfLength(Long.BYTES / 2);
    }

    @Test
    void pollReturnsPendingOpenForHeaderFileShorterThanRegion() throws IOException {
        pollReturnsPendingOpenForHeaderFileOfLength(1024);
    }

    @Test
    void pollReturnsHandlerErrorAndRedeliversEntry() {
        try (final Queue queue = Queue.open(queueDir);
             final Appender appender = queue.createAppender();
             final Poller poller = queue.createPoller()) {
            appender.append(new byte[]{1});
            appender.append(new byte[]{2});
            final List<Long> polled = new ArrayList<>();
            final int[] failures = {2};
            final EntryHandler handler = (index, buffer, offset, length) -> {
                polled.add(index);
                if (failures[0]-- > 0) {
                    throw new RuntimeException("test handler failure for entry " + index);
                }
                return Move.NEXT;
            };

            assertThat(poller.poll(handler)).isEqualTo(Poller.HANDLER_ERROR);
            assertThat(poller.currentIndex()).isEqualTo(0);
            assertThat(poller.nextIndex()).isEqualTo(0);
            assertThat(poller.poll(handler)).isEqualTo(Poller.HANDLER_ERROR);
            assertThat(poller.poll(handler)).isEqualTo(Poller.ENTRY_POLLED);
            assertThat(poller.poll(handler)).isEqualTo(Poller.ENTRY_POLLED);
            assertThat(poller.poll(handler)).isEqualTo(Poller.PENDING_NEXT);
            assertThat(polled).containsExactly(0L, 0L, 0L, 1L);
        }
    }

    @Test
    void pollReturnsUnknownErrorIfPayloadCannotBeMapped() throws IOException {
        try (final Queue queue = Queue.open(queueDir)) {
            try (final Appender appender = queue.createAppender()) {
                appender.append(new byte[]{1});
            }
            //header for entry 1 referencing an appender whose payload file does not exist
            writeHeader(1, Headers.header(Headers.MAX_APPENDER_ID, 0));
            try (final Poller poller = queue.createPoller()) {
                final List<Long> polled = new ArrayList<>();
                final EntryHandler handler = (index, buffer, offset, length) -> {
                    polled.add(index);
                    return Move.NEXT;
                };

                assertThat(poller.poll(handler)).isEqualTo(Poller.ENTRY_POLLED);
                assertThat(poller.poll(handler)).isEqualTo(Poller.UNKNOWN_ERROR);
                assertThat(poller.currentIndex()).isEqualTo(1);
                assertThat(poller.nextIndex()).isEqualTo(1);
                assertThat(poller.poll(handler)).isEqualTo(Poller.UNKNOWN_ERROR);
                assertThat(polled).containsExactly(0L);

                //skipping the corrupt entry resumes polling
                poller.seek(2);
                assertThat(poller.poll(handler)).isEqualTo(Poller.PENDING_NEXT);
                assertThat(polled).containsExactly(0L);
            }
        }
    }

    @Test
    void handlerMovesSetNextIndex() {
        try (final Queue queue = Queue.open(queueDir);
             final Appender appender = queue.createAppender();
             final Poller poller = queue.createPoller()) {
            for (int i = 0; i < 5; i++) {
                appender.append(new byte[]{(byte) i});
            }
            assertThat(pollWithMove(poller, Move.NONE)).isEqualTo(0);
            assertThat(poller.nextIndex()).isEqualTo(0);
            assertThat(pollWithMove(poller, 3)).isEqualTo(0);
            assertThat(poller.nextIndex()).isEqualTo(3);
            assertThat(pollWithMove(poller, Move.PREVIOUS)).isEqualTo(3);
            assertThat(pollWithMove(poller, Move.FIRST)).isEqualTo(2);
            assertThat(poller.nextIndex()).isEqualTo(Index.FIRST);
            assertThat(pollWithMove(poller, Move.PREVIOUS)).isEqualTo(0);
            assertThat(poller.nextIndex()).isEqualTo(Index.NULL);
            assertThat(poller.poll(failingHandler())).isEqualTo(Poller.BEFORE_FIRST);

            poller.seekFirst();
            assertThat(pollWithMove(poller, Move.LAST)).isEqualTo(0);
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(4);
            assertThat(poller.poll(failingHandler())).isEqualTo(Poller.PENDING_NEXT);
        }
    }

    @Test
    void handlerMoveToEndOrOverflowingMovePollsEntriesAppendedLater() {
        try (final Queue queue = Queue.open(queueDir);
             final Appender appender = queue.createAppender();
             final Poller poller = queue.createPoller()) {
            for (int i = 0; i < 5; i++) {
                appender.append(new byte[]{(byte) i});
            }
            assertThat(pollWithMove(poller, Move.END)).isEqualTo(0);
            assertThat(pollUntilPolled(poller, Move.NEXT, 64)).isEqualTo(Poller.PENDING_NEXT);
            appender.append(new byte[]{5});
            assertThat(pollWithMove(poller, Move.PREVIOUS)).isEqualTo(5);
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(4);

            //a forward move overflowing the index range moves to the end
            assertThat(pollWithMove(poller, Index.MAX)).isEqualTo(5);
            assertThat(poller.nextIndex()).isEqualTo(Index.END);
            assertThat(pollUntilPolled(poller, Move.NEXT, 64)).isEqualTo(Poller.PENDING_NEXT);
            appender.append(new byte[]{6});
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(6);
        }
    }

    @Test
    void seekFirstRestartsPollingAtFirstEntry() {
        try (final Queue queue = Queue.open(queueDir);
             final Appender appender = queue.createAppender();
             final Poller poller = queue.createPoller()) {
            appender.append(new byte[]{0});
            appender.append(new byte[]{1});
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(0);
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(1);
            poller.seekFirst();
            assertThat(poller.nextIndex()).isEqualTo(Index.FIRST);
            assertThat(pollWithMove(poller, Move.NEXT)).isEqualTo(0);
        }
    }

    @Test
    void closedPollerReturnsClosedAndRejectsSeeking() {
        try (final Queue queue = Queue.open(queueDir);
             final Appender appender = queue.createAppender()) {
            appender.append(new byte[]{0});
            final Poller poller = queue.createPoller();
            poller.close();
            assertThat(poller.isClosed()).isTrue();
            assertThat(poller.poll(failingHandler())).isEqualTo(Poller.CLOSED);
            assertThatThrownBy(poller::seekFirst).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(poller::seekLast).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(poller::seekEnd).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> poller.seek(0)).isInstanceOf(IllegalStateException.class);
            poller.close();
            assertThat(poller.isClosed()).isTrue();
        }
    }

    /** Polls until an entry is polled, which the handler moves by the given move; returns the polled index */
    private static long pollWithMove(final Poller poller, final long move) {
        final long[] polledIndex = {Index.NULL};
        final int result = pollUntilPolled(poller, (index, buffer, offset, length) -> {
            assertThat(buffer.getByte(offset)).isEqualTo((byte) index);
            polledIndex[0] = index;
            return move;
        }, 64);
        assertThat(result).isEqualTo(Poller.ENTRY_POLLED);
        return polledIndex[0];
    }

    /** Polls until an entry is polled or attempts are exhausted; returns the last poll result */
    private static int pollUntilPolled(final Poller poller, final long move, final int attempts) {
        return pollUntilPolled(poller, (index, buffer, offset, length) -> move, attempts);
    }

    private static int pollUntilPolled(final Poller poller, final EntryHandler handler, final int attempts) {
        int result = Poller.PENDING_NEXT;
        for (int i = 0; i < attempts && result != Poller.ENTRY_POLLED; i++) {
            result = poller.poll(handler);
        }
        return result;
    }

    private static EntryHandler failingHandler() {
        return (index, buffer, offset, length) -> {
            throw new AssertionError("unexpected entry " + index);
        };
    }

    private void writeHeader(final long index, final long header) throws IOException {
        final File headerFile = new QueueFiles(queueDir).headerFile();
        final File rolledHeaderFile = new File(queueDir, headerFile.getName().replace(".mmq", "_0.mmq"));
        final File file = rolledHeaderFile.exists() ? rolledHeaderFile : headerFile;
        assertThat(file).exists();
        final ByteBuffer bytes = ByteBuffer.allocate(Headers.HEADER_LENGTH).order(ByteOrder.nativeOrder());
        bytes.putLong(0, header);
        try (final RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.getChannel().write(bytes, Headers.headerPositionForIndex(index));
        }
    }

    private void pollReturnsPendingOpenForHeaderFileOfLength(final int length) throws IOException {
        try (final Queue queue = Queue.open(queueDir)) {
            final File headerFile = new QueueFiles(queueDir).headerFile();
            //write both the plain and the first rolled header file, so the test works with or without file rolling
            final String rolledName = headerFile.getName().replace(".mmq", "_0.mmq");
            for (final File file : new File[]{headerFile, new File(queueDir, rolledName)}) {
                Files.write(file.toPath(), new byte[length]);
            }
            try (final Poller poller = queue.createPoller()) {
                assertThat(poller.poll((index, buffer, offset, len) -> Move.NEXT)).isEqualTo(Poller.PENDING_OPEN);
                assertThat(poller.poll((index, buffer, offset, len) -> Move.NEXT)).isEqualTo(Poller.PENDING_OPEN);
            }
        }
    }
}
