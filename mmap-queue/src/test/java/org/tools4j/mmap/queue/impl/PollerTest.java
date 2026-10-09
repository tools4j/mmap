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
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        try (final Queue queue = Queue.create(queueDir);
             final Poller poller = queue.createPoller()) {
            assertThatThrownBy(() -> poller.seek(Index.NULL)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> poller.seek(-5)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> poller.seek(Move.FIRST)).isInstanceOf(IllegalArgumentException.class);
            assertThat(poller.nextIndex()).isEqualTo(Index.FIRST);
        }
    }

    @Test
    void seekNonNegativeIndexSetsNextIndex() {
        try (final Queue queue = Queue.create(queueDir);
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
        try (final Queue queue = Queue.create(queueDir);
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

    private void pollReturnsPendingOpenForHeaderFileOfLength(final int length) throws IOException {
        try (final Queue queue = Queue.create(queueDir)) {
            final File headerFile = new QueueFiles(queueDir, QueueConfig.getDefault().maxAppenders()).headerFile();
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
