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

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static org.tools4j.mmap.mapping.impl.Constraints.validateNonNegative;

/**
 * File initialiser that ensures a minimum file length, by appending zero bytes for read/write access, or by validating
 * the file length for read-only access. A minimum length of zero results in a no-op initialiser.
 *
 * @see FileInitialiser#minLength(long)
 */
public final class MinLengthFileInitialiser implements FileInitialiser {
    public static final MinLengthFileInitialiser NONE = new MinLengthFileInitialiser(0);

    private final long length;

    public MinLengthFileInitialiser(final long length) {
        validateNonNegative("length", length);
        this.length = length;
    }

    @Override
    public void init(final String fileName, final FileChannel fileChannel, final AccessMode accessMode)
            throws IOException {
        if (length <= 0) {
            return;
        }
        switch (accessMode) {
            case READ_ONLY -> validateMinSize(fileName, fileChannel);
            case READ_WRITE -> appendZeroBytes(fileChannel);
        }
    }

    private void validateMinSize(final String fileName, final FileChannel fileChannel) throws IOException {
        final long size = fileChannel.size();
        if (size < length) {
            throw new IllegalArgumentException("Invalid file, expected file of size at least " + length +
                    " but found only " + size + ": " + fileName);
        }
    }

    private void appendZeroBytes(final FileChannel fileChannel) throws IOException {
        if (fileChannel.size() >= length) {
            return;
        }
        final FileLock lock = FileLocks.acquireLock(fileChannel);
        try {
            final long position = fileChannel.size();
            final long count = length - position;
            if (count > 0) {
                fileChannel.transferFrom(InitialBytes.ZERO, position, count);
                fileChannel.force(true);
            }
        } finally {
            lock.release();
        }
    }

    @Override
    public String toString() {
        return "MinLengthFileInitialiser:length=" + length;
    }
}
