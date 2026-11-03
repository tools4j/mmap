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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.EntryHandler;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.MappingStrategy;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.ReaderConfig;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Allocation test for {@link Appender} and {@link Poller} of {@link QueueImpl}: appending and polling in steady state,
 * across header and payload regions, allocates no garbage on the appending and polling thread, and neither does
 * polling while waiting for the queue, its header file and its first entry to be created.
 */
class AllocationTest {
    private static final com.sun.management.ThreadMXBean THREAD_MX_BEAN =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 5;
    private static final int ENTRIES_PER_ROUND = 100_000;
    private static final int PENDING_POLLS = 10_000;

    @TempDir
    Path tempDir;

    private final UnsafeBuffer message = new UnsafeBuffer(new byte[64]);
    private long sum;
    private static final EntryHandler HANDLER_NOT_INVOKED = (index, buffer, offset, length) -> {
        throw new AssertionError("No entry expected at index " + index);
    };
    private final EntryHandler handler = (index, buffer, offset, length) -> {
        sum += buffer.getLong(offset);
        return Move.NEXT;
    };

    @Test
    void appendAndPollAllocateNoGarbage() {
        final QueueConfig config = QueueConfig.configure()
                .lifecycleListener(LifecycleListener.NO_OP)
                .errorListener(ErrorListener.NO_OP)
                .toImmutableQueueConfig();
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile(), OpenMode.CREATE, config);
             final Appender appender = queue.createAppender();
             final Poller poller = queue.createPoller()) {
            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                appendAndPoll(appender, poller);
            }
            for (int round = 0; round < MEASURED_ROUNDS; round++) {
                final long before = THREAD_MX_BEAN.getCurrentThreadAllocatedBytes();
                appendAndPoll(appender, poller);
                final long allocated = THREAD_MX_BEAN.getCurrentThreadAllocatedBytes() - before;
                //NOTE: tolerate sporadic bytes, e.g. objects rematerialized when the JIT deoptimizes, while a single
                //      allocation per append or poll would amount to at least 16 bytes per entry
                assertThat(allocated)
                        .as("bytes allocated in round %s for %s entries", round, ENTRIES_PER_ROUND)
                        .isLessThan(ENTRIES_PER_ROUND);
            }
        }
        assertThat(sum).isPositive();
    }

    static Stream<Arguments> pendingPollCases() {
        return Stream.of(MappingStrategy.values())
                .flatMap(strategy -> Stream.of(Arguments.of(strategy, false), Arguments.of(strategy, true)));
    }

    @ParameterizedTest(name = "{0}, rollHeaderFile={1}")
    @MethodSource("pendingPollCases")
    void pendingPollsAllocateNoGarbage(final MappingStrategy strategy, final boolean rollHeaderFile) {
        final QueueConfig config = QueueConfig.configure()
                .rollHeaderFile(rollHeaderFile)
                .lifecycleListener(LifecycleListener.NO_OP)
                .errorListener(ErrorListener.NO_OP)
                .toImmutableQueueConfig();
        final File file = tempDir.resolve("queue").toFile();
        try (final Queue lazyQueue = Queue.open(file, OpenMode.READ_ONLY_LAZY, config);
             final Poller poller = lazyQueue.createPoller(ReaderConfig.configurePoller().mappingStrategy(strategy))) {
            assertPendingPollsAllocateNoGarbage(poller, Poller.PENDING_OPEN, "queue does not exist");
            try (final Queue queue = Queue.open(file, OpenMode.CREATE, config)) {
                assertPendingPollsAllocateNoGarbage(poller, Poller.PENDING_OPEN, "header file does not exist");
                try (final Appender ignored = queue.createAppender()) {
                    assertPendingPollsAllocateNoGarbage(poller, Poller.PENDING_NEXT, "queue is empty");
                }
            }
        }
    }

    private static void assertPendingPollsAllocateNoGarbage(final Poller poller, final int pendingResult,
                                                            final String phase) {
        pendingPolls(poller, pendingResult);//warm up
        final long before = THREAD_MX_BEAN.getCurrentThreadAllocatedBytes();
        final int unexpectedResults = pendingPolls(poller, pendingResult);
        final long allocated = THREAD_MX_BEAN.getCurrentThreadAllocatedBytes() - before;
        assertThat(unexpectedResults).as("unexpected poll results while %s", phase).isZero();
        //NOTE: tolerate sporadic allocations, e.g. objects rematerialized when the JIT deoptimizes (seen up to ~25KB),
        //      while a single allocation per poll would amount to at least 16 bytes per poll
        assertThat(allocated)
                .as("bytes allocated by %s polls while %s", PENDING_POLLS, phase)
                .isLessThan(8L * PENDING_POLLS);
    }

    private static int pendingPolls(final Poller poller, final int pendingResult) {
        int unexpectedResults = 0;
        for (int i = 0; i < PENDING_POLLS; i++) {
            if (poller.poll(HANDLER_NOT_INVOKED) != pendingResult) {
                unexpectedResults++;
            }
        }
        return unexpectedResults;
    }

    private void appendAndPoll(final Appender appender, final Poller poller) {
        for (int i = 0; i < ENTRIES_PER_ROUND; i++) {
            message.putLong(0, i);
            appender.append(message, 0, message.capacity());
            while (poller.poll(handler) != Poller.ENTRY_POLLED) {
                //poll until available
            }
        }
    }
}
