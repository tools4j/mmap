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
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;
import org.tools4j.mmap.queue.config.QueueConfigurator;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for lifecycle and error events of {@link QueueImpl}, its appenders and readers.
 */
class QueueListenerTest {
    @TempDir
    Path tempDir;

    private final List<String> events = new ArrayList<>();

    private final LifecycleListener lifecycleListener = new LifecycleListener() {
        @Override
        public void onCreated(final Describable resource) {
            events.add("Created " + resource.getClass().getSimpleName());
        }

        @Override
        public void onOpened(final Describable resource) {
            events.add("Opened " + resource.getClass().getSimpleName());
        }

        @Override
        public void onClosed(final Describable resource) {
            events.add("Closed " + resource.getClass().getSimpleName());
        }
    };

    private final ErrorListener errorListener = new ErrorListener() {
        @Override
        public void onError(final Describable source, final String message, final Throwable error) {
            events.add("Error " + source.getClass().getSimpleName() + ": " + message + ": " + error.getMessage());
        }

        @Override
        public void onWarning(final Describable source, final CharSequence message) {
            events.add("Warning " + source.getClass().getSimpleName() + ": " + message);
        }
    };

    private QueueConfigurator config() {
        return QueueConfig.configure()
                .lifecycleListener(lifecycleListener)
                .errorListener(errorListener);
    }

    private long count(final String event) {
        return events.stream().filter(event::equals).count();
    }

    @Test
    void queueAppenderAndPollerEventsAreReported() {
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile(), OpenMode.CREATE, config())) {
            try (final Appender appender = queue.createAppender()) {
                appender.append(new byte[]{1});
            }
            try (final Poller poller = queue.createPoller()) {
                for (int i = 0; i < 3; i++) {
                    assertThat(poller.poll((index, buffer, offset, length) -> {
                        throw new IllegalStateException("handler failure");
                    })).isEqualTo(Poller.HANDLER_ERROR);
                }
            }
        }

        assertThat(events).containsSubsequence(
                "Opened IdPool256", "Opened QueueImpl",
                "Opened AppenderImpl", "Closed AppenderImpl",
                "Opened PollerImpl", "Closed PollerImpl",
                "Closed IdPool256", "Closed QueueImpl");
        assertThat(count("Error PollerImpl: Unexpected exception thrown by entry handler: handler failure"))
                .as("every error is reported to the listener").isEqualTo(3);
        assertThat(count("Opened ReadOnlyFileMapper")).isEqualTo(count("Closed ReadOnlyFileMapper")).isPositive();
    }

    @Test
    void differentLayoutIsReportedAsWarning() {
        final File dir = tempDir.resolve("queue").toFile();
        Queue.open(dir, OpenMode.CREATE, QueueConfig.getDefault()).close();

        try (final Queue ignored = Queue.open(dir, OpenMode.OPEN_OR_CREATE,
                config().headerRegionSize(2 * QueueConfig.getDefault().headerRegionSize()))) {
            assertThat(events).anyMatch(event ->
                    event.startsWith("Warning QueueImpl: Queue was created with a different layout, using: "));
        }
    }

    @Test
    void payloadFileRollsAreReported() {
        final int regionSize = (int) REGION_SIZE_GRANULARITY;
        final QueueConfig config = config()
                .payloadRegionSize(regionSize)
                .minPayloadFileSize(0)
                .maxPayloadFileSize(regionSize)
                .rollPayloadFiles(true)
                .payloadFilesToCreateAhead(0)
                .toImmutableQueueConfig();
        try (final Queue queue = Queue.open(tempDir.resolve("queue").toFile(), OpenMode.CREATE, config);
             final Appender appender = queue.createAppender()) {
            final byte[] entry = new byte[regionSize / 2];
            for (int i = 0; i < 6; i++) {
                appender.append(entry);
            }
        }

        assertThat(events.stream().filter(event -> event.startsWith("Created ")).count())
                .as("one created event per payload file, plus ID pool and header file").isGreaterThanOrEqualTo(5);
    }
}
