/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.ExpandableDirectByteBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.util.FileUtil;
import org.tools4j.mmap.region.config.MappingStrategy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueueTest {

    private static final int MAX_POLL_INVOCATIONS = 256;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory(QueueTest.class.getSimpleName());
        tempDir.toFile().deleteOnExit();
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
    void testSync() {
        test(QueueConfig.configure().mappingStrategy(MappingStrategy.defaultSyncMappingStrategy()));
    }

    @Test
    void testAsync() {
        test(QueueConfig.configure().mappingStrategy(MappingStrategy.defaultAheadMappingStrategy()));
    }

    private void test(final QueueConfig config) {
        try (final Queue queue = Queue.create(new File(tempDir.toFile(), "testQ"), config)) {
            try (final Appender appender = queue.createAppender();
                 final Poller poller1 = queue.createPoller();
                 final Poller poller2 = queue.createPoller()) {
                long expectedIndex = -1;
                long actualIndex;

                //when
                final String testString1 = "qwerfwvrgtw3243rcvgfwrvwer";
                actualIndex = append(appender, testString1);

                //then
                assertThat(actualIndex).isEqualTo(++expectedIndex);
                assertThat(get(poller1, actualIndex)).isEqualTo(testString1);
                assertThat(get(poller2, actualIndex)).isEqualTo(testString1);

                //when
                final String testString2 = "1234";
                actualIndex = append(appender, testString2);

                //then
                assertThat(actualIndex).isEqualTo(++expectedIndex);
                assertThat(get(poller1, actualIndex)).isEqualTo(testString2);
                assertThat(get(poller2, actualIndex)).isEqualTo(testString2);

                //when
                actualIndex = appendZeroLengthEntry(appender);

                //then
                assertThat(actualIndex).isEqualTo(++expectedIndex);
                assertThat(getLength(poller1, actualIndex)).isEqualTo(0);
                assertThat(getLength(poller2, actualIndex)).isEqualTo(0);

                //when
                final String testString3 = "rvwer";
                actualIndex = append(appender, testString3);

                //then
                assertThat(actualIndex).isEqualTo(++expectedIndex);
                assertThat(get(poller1, actualIndex)).isEqualTo(testString3);
                assertThat(get(poller2, actualIndex)).isEqualTo(testString3);

                //when
                poller1.seekNext(0);
                poller2.seekNext(1);

                //then
                assertThat(get(poller1, 0)).isEqualTo(testString1);
                assertThat(get(poller2, 1)).isEqualTo(testString2);

                //when
                poller1.seekLast();
                poller2.seekEnd();

                //then
                assertThat(poller1.nextIndex()).isEqualTo(Index.LAST);
                assertThat(poller2.nextIndex()).isEqualTo(Index.END);

                //when + then
                assertThat(get(poller1, 3)).isEqualTo(testString3);
                assertThatThrownBy(() -> get(poller2, 3)).message()
                        .isEqualTo("Entry 3 not polled after " + MAX_POLL_INVOCATIONS + " invocations");
                assertThat(poller2.nextIndex()).isEqualTo(4);
            }
        }
    }

    private static long append(final Appender appender, final String string) {
        final ExpandableDirectByteBuffer buffer = new ExpandableDirectByteBuffer();
        final int len = buffer.putStringUtf8(0, string);
        return appender.append(buffer, 0, len);
    }

    private static long appendZeroLengthEntry(final Appender appender) {
        try (final AppendingContext context = appender.appending(0)) {
            return context.commit(0);
        }
    }

    private int getLength(final Poller poller, final long index) {
        final int[] lenPtr = {-1};
        poll(poller, index, (idx, buf, off, len) -> {
            lenPtr[0] = len;
            return Move.NEXT;
        });
        return lenPtr[0];
    }

    private String get(final Poller poller, final long expectedIndex) {
        final AtomicReference<String> stringValue = new AtomicReference<>(null);
        poll(poller, expectedIndex, (idx, buf, off, len) -> {
            stringValue.set(buf.getStringUtf8(off));
            return Move.NEXT;
        });
        return stringValue.get();
    }

    private void poll(final Poller poller, final long expectedIndex, final EntryHandler handler) {
        for (int i = 0; i < MAX_POLL_INVOCATIONS; i++) {
            if (poller.poll((idx, buf, off, len) -> {
                assertThat(idx).isEqualTo(expectedIndex);
                return handler.onEntry(idx, buf, off, len);
            }) == Poller.ENTRY_POLLED) {
                return;
            }
        }
        throw new AssertionError("Entry " + expectedIndex + " not polled after " + MAX_POLL_INVOCATIONS
                + " invocations");
    }
}