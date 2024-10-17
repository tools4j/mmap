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
package org.tools4j.mmap.region.impl;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

enum FileLocks {
    ;
    static final long DEFAULT_MAX_WAIT_MILLIS = 5000;

    static FileLock acquireLock(final FileChannel fileChannel) {
        return acquireLock(fileChannel, DEFAULT_MAX_WAIT_MILLIS);
    }
    static FileLock acquireLock(final FileChannel fileChannel, final long maxWaitMillis) {
        final long endTime = System.currentTimeMillis() + maxWaitMillis;
        long remainingMillis;
        FileLock fileLock = null;
        do {
            try {
                fileLock = fileChannel.tryLock();
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
        } while (fileLock == null && maxWaitMillis > 0);
        if (fileLock != null) {
            return fileLock;
        }
        throw new IllegalStateException("Timeout while waiting for file lock for " + fileChannel);
    }
}
