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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.FileInitialiser;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

/**
 * File initialiser that ensures a minimum file length by appending or overwriting zero bytes, depending on the
 * access mode.
 *
 * @see FileInitialiser#zeroBytes(AccessMode, long, long)
 */
public final class FileInitialiserImpl implements FileInitialiser {
    private final AccessMode accessMode;
    private final long start;
    private final long end;

    public FileInitialiserImpl(final AccessMode accessMode, final long start, final long end) {
        requireNonNull(accessMode);
        validateNonNegative("start", start);
        validateNonNegative("end", end);
        this.accessMode = accessMode;
        this.start = start;
        this.end = end;
    }

    @Override
    public void init(final String fileName, final FileChannel fileChannel) throws IOException {
        if (end <= 0) {
            return;
        }
        switch (accessMode) {
            case READ_ONLY -> validateMinSize(fileName, fileChannel);
            case READ_WRITE -> appendZeroBytes(fileChannel);
            case READ_WRITE_CLEAR -> overwriteWithZeroBytes(fileChannel);
        }
    }

    private void validateMinSize(final String fileName, final FileChannel fileChannel) throws IOException {
        final long size = fileChannel.size();
        if (size < end) {
            throw new IllegalArgumentException("Invalid file, expected file of size at least " + end +
                    " but found only " + size + ": " + fileName);
        }
    }

    private void appendZeroBytes(final FileChannel fileChannel) throws IOException {
        if (fileChannel.size() >= end) {
            return;
        }
        final FileLock lock = FileLocks.acquireLock(fileChannel);
        try {
            final long position = fileChannel.size();
            final long count = end - position;
            if (count > 0) {
                fileChannel.transferFrom(InitialBytes.ZERO, position, count);
                fileChannel.force(true);
            }
        } finally {
            lock.release();
        }
    }

    private void overwriteWithZeroBytes(final FileChannel fileChannel) throws IOException {
        final FileLock lock = FileLocks.acquireLock(fileChannel);
        try {
            final long position = Math.min(start, fileChannel.size());
            fileChannel.transferFrom(InitialBytes.ZERO, position, end - position);
            fileChannel.force(true);
        } finally {
            lock.release();
        }
    }

    @Override
    public String toString() {
        return "FileInitialiserImpl:accessMode=" + accessMode + "|start=" + start + "|end=" + end;
    }
}
