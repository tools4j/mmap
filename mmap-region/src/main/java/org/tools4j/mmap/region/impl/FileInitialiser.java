/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.region.api.AccessMode;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * File initialiser used to initialise a filechannel when a new file is
 * created for mapping.
 */
@FunctionalInterface
public interface FileInitialiser {

    /**
     * Initialise file channel
     *
     * @param fileName file name for reference
     * @param fileChannel file channel to initialise
     * @throws IOException thrown when file channel could not be initialised.
     */
    void init(String fileName, FileChannel fileChannel) throws IOException;

    static FileInitialiser zeroBytes(final AccessMode mode, final int length) {
        switch (mode) {
            case READ_ONLY:
                return (fileName, fileChannel) -> {
                    if (fileChannel.size() < length) {
                        throw new IllegalArgumentException("Invalid file, expected length " + length + " but found " +
                                fileChannel.size() + ": " + fileName);
                    }
                };
            case READ_WRITE:
                return (fileName, fileChannel) -> {
                    if (fileChannel.size() < length) {
                        final FileLock lock = FileLocks.acquireLock(fileChannel);
                        final long offset = fileChannel.size();
                        final long count = length - offset;
                        try {
                            if (count > 0) { //allow file init once-only
                                fileChannel.transferFrom(InitialBytes.ZERO, offset, count);
                                fileChannel.force(true);
                            }
                        } finally {
                            lock.release();
                        }
                    }
                };
            case READ_WRITE_CLEAR:
                return (fileName, fileChannel) -> {
                    final FileLock lock = FileLocks.acquireLock(fileChannel);
                    try {
                        fileChannel.transferFrom(InitialBytes.ZERO, 0L, length);
                        fileChannel.force(true);
                    } finally {
                        lock.release();
                    }
                };
            default:
                throw new IllegalArgumentException("Map mode not supported: " + mode);
        }
    }
}
