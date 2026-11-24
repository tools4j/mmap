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
import org.tools4j.mmap.core.base.Closeable;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.queue.api.Move;
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.queue.api.Queue;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for closing {@link QueueImpl} and the appenders and readers created through it.
 */
class QueueCloseTest {

    private static final int ROUNDS = 50;
    private static final long MAX_WAIT_MILLIS = 30_000;

    @TempDir
    Path tempDir;

    private final RecordingErrorListener errorListener = new RecordingErrorListener();

    @Test
    void closingElementsIndividuallyUnregistersThem() {
        try (final QueueImpl queue = (QueueImpl) Queue.open(queueDir())) {
            final List<Closeable> elements = createAllElements(queue);
            assertEquals(elements.size(), queue.openElements());
            for (int i = 0; i < elements.size(); i++) {
                elements.get(i).close();
                assertEquals(elements.size() - i - 1, queue.openElements());
                elements.get(i).close();//idempotent
                assertEquals(elements.size() - i - 1, queue.openElements());
            }
        }
    }

    @Test
    void closingQueueClosesOpenElementsOfClosingThreadWithoutWarning() {
        final QueueImpl queue = (QueueImpl) Queue.open(queueDir(), OpenMode.CREATE, config());
        final List<Closeable> elements = createAllElements(queue);
        elements.get(0).close();
        queue.close();
        assertEquals(0, queue.openElements());
        for (final Closeable element : elements) {
            assertTrue(element.isClosed(), element::toString);
        }
        assertThrows(IllegalStateException.class, queue::createPoller);
        assertEquals(List.of(), errorListener.warnings);
        assertEquals(List.of(), errorListener.errors);
    }

    @Test
    void closingQueueWarnsOfOpenElementsOfOtherThreadsBeforeClosingAny() throws Exception {
        final QueueImpl queue = (QueueImpl) Queue.open(queueDir(), OpenMode.CREATE, config());
        final List<Closeable> elements = new ArrayList<>();
        final Thread creator = new Thread(() -> elements.addAll(createAllElements(queue)), "element-creator");
        creator.start();
        creator.join(MAX_WAIT_MILLIS);
        assertFalse(creator.isAlive());
        elements.get(0).close();
        final List<Closeable> openElements = elements.subList(1, elements.size());
        errorListener.onWarning = () -> openElements.forEach(
                element -> assertFalse(element.isClosed(), element::toString));
        queue.close();
        assertEquals(openElements.size(), errorListener.warnings.size(), errorListener.warnings::toString);
        for (int i = 0; i < openElements.size(); i++) {
            final String warning = errorListener.warnings.get(i);
            final String elementType = openElements.get(i).getClass().getSimpleName();
            assertTrue(warning.startsWith("Closing " + elementType + ":"), warning);
            assertTrue(warning.endsWith("created by thread element-creator that should have closed it"), warning);
        }
        openElements.forEach(element -> assertTrue(element.isClosed(), element::toString));
        assertEquals(List.of(), errorListener.errors);
    }

    @Test
    void closingReadOnlyQueueWithoutIdPoolReportsNoError() {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE).close();
        final QueueImpl queue = (QueueImpl) Queue.open(dir, OpenMode.READ_ONLY, config());
        queue.createPoller();
        queue.close();
        assertTrue(queue.isClosed());
        assertEquals(List.of(), errorListener.errors);
    }

    @Test
    void closingQueueUnmapsAllItsElementsMapped() {
        final long bytes = Mappings.mappedBytes();
        final long count = Mappings.mappingCount();
        final Queue queue = Queue.open(queueDir(), OpenMode.CREATE, config());
        createAllElements(queue);
        queue.createAppender().append(new byte[]{1, 2, 3});
        assertEquals(Poller.ENTRY_POLLED, queue.createPoller().poll((index, buffer, offset, length) -> Move.NEXT));
        assertTrue(Mappings.mappingCount() > count, "mapped while open");
        queue.close();
        assertEquals(bytes, Mappings.mappedBytes(), "mapped bytes");
        assertEquals(count, Mappings.mappingCount(), "mapping count");
    }

    @Test
    void closingQueueReleasesIdsOfOpenAppenders() {
        final File dir = queueDir();
        final Queue queue = Queue.open(dir);
        queue.createAppender();
        queue.createAppender();
        queue.close();
        try (final AppenderIdPool idPool = new AppenderIdPool(new QueueFiles(dir).idPoolFile())) {
            assertEquals(0, idPool.acquired());
        }
    }

    @Test
    void concurrentCloseAndCreateLeavesNoOpenElement() throws Exception {
        final File dir = queueDir();
        Queue.open(dir, OpenMode.CREATE).close();
        final long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        for (int round = 0; round < ROUNDS; round++) {
            //pollers of the creator thread still open, which the queue warns of
            final QueueImpl queue = (QueueImpl) Queue.open(dir, OpenMode.READ_ONLY, config());
            final java.util.Queue<Poller> pollers = new ConcurrentLinkedQueue<>();
            final AtomicReference<Throwable> error = new AtomicReference<>();
            final CountDownLatch started = new CountDownLatch(1);
            final Thread creator = new Thread(() -> {
                started.countDown();
                try {
                    while (true) {
                        pollers.add(queue.createPoller());
                    }
                } catch (final IllegalStateException e) {
                    //queue closed, expected
                } catch (final Throwable t) {
                    error.set(t);
                }
            }, "creator-" + round);
            creator.start();
            assertTrue(started.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
            queue.close();
            creator.join(Math.max(1, deadline - System.currentTimeMillis()));
            assertFalse(creator.isAlive(), "creator still running in round " + round);
            assertNull(error.get());
            assertEquals(0, queue.openElements(), "open elements in round " + round);
            for (final Poller poller : pollers) {
                assertTrue(poller.isClosed(), "poller not closed in round " + round);
            }
        }
        assertEquals(List.of(), errorListener.errors);
    }

    private QueueConfig config() {
        return QueueConfig.configure().errorListener(errorListener);
    }

    private File queueDir() {
        return tempDir.resolve("queue").toFile();
    }

    private static List<Closeable> createAllElements(final Queue queue) {
        return List.of(
                queue.createAppender(),
                queue.createPoller(),
                queue.createEntryReader(),
                queue.createEntryIterator(),
                queue.createIndexReader());
    }

    /** Records errors and warnings, running a check on each warning */
    private static final class RecordingErrorListener implements ErrorListener {
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> warnings = new CopyOnWriteArrayList<>();
        volatile Runnable onWarning = () -> {};

        @Override
        public void onError(final Describable source, final String message, final Throwable error) {
            errors.add(message + ": " + error);
        }

        @Override
        public void onWarning(final Describable source, final CharSequence message) {
            onWarning.run();
            warnings.add(message.toString());
        }
    }
}
