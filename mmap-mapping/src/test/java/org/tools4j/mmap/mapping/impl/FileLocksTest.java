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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link FileLocks}.
 */
class FileLocksTest {

    /** Short wait for a lock that is expected not to be released */
    private static final long SHORT_WAIT_MILLIS = 50;
    /** Upper bound only, for a lock that is expected to be acquired */
    private static final long MAX_WAIT_MILLIS = 10_000;

    private RandomAccessFile raf1;
    private RandomAccessFile raf2;
    private FileChannel channel1;
    private FileChannel channel2;

    @BeforeEach
    void open(@TempDir final Path tempDir) throws IOException {
        raf1 = new RandomAccessFile(tempDir.resolve("lock.map").toFile(), "rw");
        raf2 = new RandomAccessFile(tempDir.resolve("lock.map").toFile(), "rw");
        channel1 = raf1.getChannel();
        channel2 = raf2.getChannel();
    }

    @AfterEach
    void close() throws IOException {
        raf1.close();
        raf2.close();
    }

    @Test
    void acquiresFreeLock() throws IOException {
        final FileLock lock = FileLocks.acquireLock(channel1);
        assertTrue(lock.isValid());
        lock.release();
    }

    @Test
    void timesOutIfLockIsHeld() throws IOException {
        try (final FileLock held = FileLocks.acquireLock(channel1)) {
            final IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> FileLocks.acquireLock(channel2, SHORT_WAIT_MILLIS));
            assertTrue(e.getMessage().startsWith("Timeout while waiting for file lock"), e.getMessage());
            assertTrue(held.isValid());
        }
    }

    @Test
    void zeroWaitTriesOnlyOnce() throws IOException {
        try (final FileLock ignored = FileLocks.acquireLock(channel1)) {
            assertThrows(IllegalStateException.class, () -> FileLocks.acquireLock(channel2, 0));
        }
    }

    @Test
    void acquiresLockOnceReleased() throws Exception {
        final FileLock held = FileLocks.acquireLock(channel1);
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicReference<Object> result = new AtomicReference<>();
        final Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                result.set(FileLocks.acquireLock(channel2, MAX_WAIT_MILLIS));
            } catch (final Throwable t) {
                result.set(t);
            }
        });
        waiter.start();
        assertTrue(started.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
        held.release();
        waiter.join(MAX_WAIT_MILLIS);
        assertFalse(waiter.isAlive());
        final FileLock acquired = (FileLock) result.get();
        assertNotNull(acquired);
        assertEquals(channel2, acquired.channel());
        acquired.release();
    }

    @Test
    void closedChannelFailsImmediately() throws IOException {
        channel2.close();
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> FileLocks.acquireLock(channel2, MAX_WAIT_MILLIS));
        assertTrue(e.getMessage().startsWith("Unexpected exception"), e.getMessage());
    }
}
