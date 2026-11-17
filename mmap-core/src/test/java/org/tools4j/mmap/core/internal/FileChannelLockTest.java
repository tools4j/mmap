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
package org.tools4j.mmap.core.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link FileChannelLock}.
 */
class FileChannelLockTest {

    /** Short wait for a lock that is expected not to be released */
    private static final long SHORT_WAIT_MILLIS = 50;
    /** Upper bound only, for a lock that is expected to be acquired */
    private static final long MAX_WAIT_MILLIS = 10_000;

    private File file;
    private RandomAccessFile raf1;
    private RandomAccessFile raf2;
    private RandomAccessFile raf3;
    private FileChannelLock lock1;
    private FileChannelLock lock2;
    private FileChannelLock lock3;

    @BeforeEach
    void open(@TempDir final Path tempDir) throws IOException {
        file = tempDir.resolve("lock.map").toFile();
        raf1 = new RandomAccessFile(file, "rw");
        raf2 = new RandomAccessFile(file, "rw");
        raf3 = new RandomAccessFile(file, "rw");
        lock1 = FileChannelLock.create(raf1.getChannel());
        lock2 = FileChannelLock.create(raf2.getChannel());
        lock3 = FileChannelLock.create(raf3.getChannel());
    }

    @AfterEach
    void close() throws IOException {
        lock1.close();
        lock2.close();
        lock3.close();
        raf1.close();
        raf2.close();
        raf3.close();
    }

    @Test
    void acquiresFreeLockAgainOnceReleased() {
        lock1.lock();
        lock1.unlock();
        lock2.lock(false, 0);
        lock2.unlock();
    }

    @Test
    void timesOutIfLockIsHeldByAnotherChannel() {
        lock1.lock();
        try {
            final IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> lock2.lock(false, SHORT_WAIT_MILLIS));
            assertTrue(e.getMessage().startsWith("Timeout while waiting for file lock"), e.getMessage());
            assertThrows(IllegalStateException.class, () -> lock2.lock(true, SHORT_WAIT_MILLIS));
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void zeroWaitTriesOnlyOnce() {
        lock1.lock();
        try {
            assertThrows(IllegalStateException.class, () -> lock2.lock(false, 0));
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void sharedLocksAreHeldTogetherAndExcludeExclusiveLock() {
        lock1.lockShared();
        lock2.lock(true, 0);
        assertThrows(IllegalStateException.class, () -> lock3.lock(false, SHORT_WAIT_MILLIS));
        lock1.unlock();
        assertThrows(IllegalStateException.class, () -> lock3.lock(false, SHORT_WAIT_MILLIS));
        lock2.unlock();
        lock3.lock(false, 0);
        lock3.unlock();
    }

    @Test
    void interruptedWaitFailsWithoutWaitingForTimeout() {
        lock1.lock();
        try {
            Thread.currentThread().interrupt();
            final long start = System.nanoTime();
            final IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> lock2.lock(false, MAX_WAIT_MILLIS));
            assertTrue(Thread.interrupted(), "interrupt status kept");
            assertTrue(e.getMessage().startsWith("Interrupted while waiting for file lock"), e.getMessage());
            assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(MAX_WAIT_MILLIS / 2),
                    "waited for the timeout");
        } finally {
            lock1.unlock();
        }
    }

    @Test
    void acquiresLockOnceReleasedByAnotherThread() throws Exception {
        lock1.lock();
        final CountDownLatch started = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread waiter = new Thread(() -> {
            started.countDown();
            try {
                lock2.lock(false, MAX_WAIT_MILLIS);
                lock2.unlock();
            } catch (final Throwable t) {
                error.set(t);
            }
        });
        waiter.start();
        assertTrue(started.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
        lock1.unlock();
        waiter.join(MAX_WAIT_MILLIS);
        assertFalse(waiter.isAlive());
        assertNull(error.get());
    }

    @Test
    void closeWaitsUntilNoChannelHoldsLock() throws Exception {
        lock1.lock();
        final CountDownLatch closed = new CountDownLatch(1);
        final Thread closer = new Thread(() -> {
            try {
                lock2.close();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            closed.countDown();
        });
        closer.start();
        assertFalse(closed.await(100, TimeUnit.MILLISECONDS), "closed while file was locked");
        assertTrue(raf2.getChannel().isOpen());
        lock1.unlock();
        assertTrue(closed.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
        assertTrue(lock2.isClosed());
        assertFalse(raf2.getChannel().isOpen());
        lock1.lock(false, 0);
        lock1.unlock();
    }

    @Test
    void concurrentClosesCloseOnce() throws Exception {
        lock3.close();//leaving lock1 and lock2 sharing the file state
        lock1.lock();
        final Thread closer1 = new Thread(this::closeLock2);
        final Thread closer2 = new Thread(this::closeLock2);
        closer1.start();
        closer2.start();
        awaitParkedOrTerminated(closer1);
        awaitParkedOrTerminated(closer2);
        lock1.unlock();
        closer1.join(MAX_WAIT_MILLIS);
        closer2.join(MAX_WAIT_MILLIS);
        assertFalse(closer1.isAlive() || closer2.isAlive(), "closers terminated");
        assertTrue(lock2.isClosed());
        assertFalse(raf2.getChannel().isOpen());
        //a file state unregistered once per close remains shared by lock1 and a new channel's lock, excluding it
        try (final RandomAccessFile raf4 = new RandomAccessFile(file, "rw");
             final FileChannelLock lock4 = FileChannelLock.create(raf4.getChannel())) {
            lock1.lock(false, 0);
            boolean locked4 = false;
            try {
                lock4.lock(false, 0);
                locked4 = true;
            } catch (final IllegalStateException expected) {
                //held through lock1
            } finally {
                if (locked4) {
                    lock4.unlock();
                }
                lock1.unlock();
            }
            assertFalse(locked4, "locked through a new channel while held through lock1");
        }
    }

    private void closeLock2() {
        try {
            lock2.close();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void awaitParkedOrTerminated(final Thread thread) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + MAX_WAIT_MILLIS;
        Thread.State state;
        while ((state = thread.getState()) != Thread.State.TIMED_WAITING && state != Thread.State.TERMINATED) {
            assertTrue(System.currentTimeMillis() < deadline, "thread state still " + state);
            //noinspection BusyWait
            Thread.sleep(10);
        }
    }

    @Test
    void closedChannelFailsImmediately() throws IOException {
        lock2.close();
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> lock2.lock(false, MAX_WAIT_MILLIS));
        assertTrue(e.getMessage().startsWith("Unexpected exception"), e.getMessage());
        lock1.lock(false, 0);
        lock1.unlock();
    }

    @Test
    void locksOfDifferentFilesAreIndependent(@TempDir final Path tempDir) throws IOException {
        try (final RandomAccessFile other = new RandomAccessFile(tempDir.resolve("other.map").toFile(), "rw");
             final FileChannelLock otherLock = FileChannelLock.create(other.getChannel())) {
            lock1.lock();
            try {
                otherLock.lock(false, 0);
                otherLock.unlock();
            } finally {
                lock1.unlock();
            }
        }
    }

    @Test
    void fileSystemWithoutNativeAllocationIsRememberedForTheFile() throws IOException {
        final AtomicInteger calls = new AtomicInteger();
        final FileChannelLock.Allocation unsupported = (fd, position, length, withoutNativeAccess) -> {
            calls.incrementAndGet();
            return FileAllocation.FILE_SYSTEM_UNSUPPORTED;
        };
        lock1.lock();
        try {
            assertEquals(FileAllocation.FILE_SYSTEM_UNSUPPORTED, lock1.allocate(0, 4096, false, unsupported));
            assertEquals(FileAllocation.SKIPPED, lock1.allocate(0, 4096, false, unsupported));
        } finally {
            lock1.unlock();
        }
        lock2.lock();
        try {
            assertEquals(FileAllocation.SKIPPED, lock2.allocate(0, 4096, true, unsupported), "shared by channels");
        } finally {
            lock2.unlock();
        }
        assertEquals(1, calls.get(), "allocation attempts");
    }

    @Test
    void allocateAllocatesNothing() throws IOException {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();//guaranteed on HotSpot
        final int cycles = 10_000;
        lock1.lock();
        try {
            for (int i = 0; i < cycles; i++) {
                allocate();
            }
            final long before = threads.getCurrentThreadAllocatedBytes();
            for (int i = 0; i < cycles; i++) {
                allocate();
            }
            final long allocated = threads.getCurrentThreadAllocatedBytes() - before;
            //NOTE: tolerate sporadic bytes as in lockAndUnlockAllocateNothing
            assertTrue(allocated < cycles, "allocated " + allocated + " bytes in " + cycles + " cycles");
        } finally {
            lock1.unlock();
        }
        assertEquals(FileChannelsTest.expectedAllocation() == FileAllocation.ALLOCATED ? 4096 : 0, file.length());
    }

    private void allocate() throws IOException {
        final FileAllocation allocation = lock1.allocate(0, 4096, false);
        if (allocation != FileChannelsTest.expectedAllocation()) {
            assertEquals(FileChannelsTest.expectedAllocation(), allocation);
        }
    }

    @Test
    void lockAndUnlockAllocateNothing() {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();//guaranteed on HotSpot
        for (int i = 0; i < 10_000; i++) {
            lockAndUnlock();
        }
        final int cycles = 10_000;
        final long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < cycles; i++) {
            lockAndUnlock();
        }
        final long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        //NOTE: tolerate sporadic bytes, e.g. objects rematerialised when the JIT deoptimizes (392 seen on Java 17),
        //      while a single allocation per cycle would amount to at least 16 bytes per cycle
        assertTrue(allocated < cycles, "allocated " + allocated + " bytes in " + cycles + " cycles");
    }

    private void lockAndUnlock() {
        lock1.lock();
        lock1.unlock();
        lock2.lockShared();
        lock3.lockShared();
        lock3.unlock();
        lock2.unlock();
    }

    @Test
    void excludesAndIsExcludedByLockOfAnotherProcessThroughJdkApi() throws Exception {
        final Process process = new ProcessBuilder(javaCommand(), "-cp", System.getProperty("java.class.path"),
                JdkLockHolder.class.getName(), file.getPath()).redirectErrorStream(true).start();
        try (final BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(),
                StandardCharsets.UTF_8))) {
            lock1.lock();
            process.getOutputStream().write('\n');//child: try lock while we hold it
            process.getOutputStream().flush();
            assertEquals("not locked", output.readLine());
            lock1.unlock();
            process.getOutputStream().write('\n');//child: lock and hold it
            process.getOutputStream().flush();
            assertEquals("locked", output.readLine());
            assertThrows(IllegalStateException.class, () -> lock1.lock(false, SHORT_WAIT_MILLIS));
            assertThrows(IllegalStateException.class, () -> lock2.lock(true, SHORT_WAIT_MILLIS));
            process.getOutputStream().close();//child: release and exit
            assertTrue(process.waitFor(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS));
            assertEquals(0, process.exitValue(), "exit value");
            lock1.lock(false, MAX_WAIT_MILLIS);
            lock1.unlock();
        } finally {
            process.destroyForcibly();
        }
    }

    private static String javaCommand() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /** Locks the file through the JDK API in another process, one step per line read from standard input */
    static final class JdkLockHolder {
        @SuppressWarnings("JavaPrintToLogpoint")
        public static void main(final String... args) throws IOException {
            final BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            try (final RandomAccessFile raf = new RandomAccessFile(args[0], "rw")) {
                final FileChannel channel = raf.getChannel();
                input.readLine();
                final FileLock attempt = channel.tryLock();
                System.out.println(attempt == null ? "not locked" : "locked");
                if (attempt != null) {
                    attempt.release();
                }
                input.readLine();
                final FileLock lock = channel.lock();
                System.out.println("locked");
                System.out.flush();
                input.transferTo(Writer.nullWriter());//hold the lock until standard input is closed
                lock.release();
            }
        }
    }
}
