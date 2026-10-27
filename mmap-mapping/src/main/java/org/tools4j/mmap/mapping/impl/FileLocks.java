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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/**
 * Acquires file locks, waiting until a lock is available or a timeout occurs. A lock held by another thread of the
 * same JVM is waited for in the same way as one held by another process.
 */
public enum FileLocks {
    ;
    static final long DEFAULT_MAX_WAIT_MILLIS = 5000;

    /**
     * Acquires an exclusive lock for the whole file, which requires a file channel open for writing.
     *
     * @param fileChannel the file channel to lock
     * @return the acquired lock
     * @throws IllegalStateException if the lock could not be acquired in time
     */
    public static FileLock acquireLock(final FileChannel fileChannel) {
        return acquireLock(fileChannel, false, DEFAULT_MAX_WAIT_MILLIS);
    }

    /**
     * Acquires a shared lock for the whole file, which requires a file channel open for reading.
     *
     * @param fileChannel the file channel to lock
     * @return the acquired lock
     * @throws IllegalStateException if the lock could not be acquired in time
     */
    public static FileLock acquireSharedLock(final FileChannel fileChannel) {
        return acquireLock(fileChannel, true, DEFAULT_MAX_WAIT_MILLIS);
    }

    static FileLock acquireLock(final FileChannel fileChannel, final long maxWaitMillis) {
        return acquireLock(fileChannel, false, maxWaitMillis);
    }

    static FileLock acquireLock(final FileChannel fileChannel, final boolean shared, final long maxWaitMillis) {
        final long endTime = System.currentTimeMillis() + maxWaitMillis;
        long remainingMillis = maxWaitMillis;
        FileLock fileLock = null;
        do {
            try {
                fileLock = fileChannel.tryLock(0, Long.MAX_VALUE, shared);
            } catch (final OverlappingFileLockException e) {
                // ignore - sleep and try again
            } catch (final IOException e) {
                throw new IllegalStateException("Unexpected exception while attempting to acquire file lock for " +
                        fileChannel, e);
            }
            if (fileLock == null) {
                remainingMillis = endTime - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    try {
                        Thread.sleep(Math.min(10, remainingMillis));
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException("Interrupted while waiting for file lock for " +
                                fileChannel, e);
                    }
                }
            }
        } while (fileLock == null && remainingMillis > 0);
        if (fileLock != null) {
            return fileLock;
        }
        throw new IllegalStateException("Timeout while waiting for file lock for " + fileChannel);
    }
}
