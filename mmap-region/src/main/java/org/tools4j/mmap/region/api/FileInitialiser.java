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
package org.tools4j.mmap.region.api;

import org.tools4j.mmap.region.impl.FileInitialiserImpl;

import java.io.IOException;
import java.nio.channels.FileChannel;

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

    /**
     * Returns a file initialiser that ensures the file is at least the given length, equivalent to
     * {@code zeroBytes(mode, 0, length)}.
     *
     * @param mode   the access mode the file is opened with
     * @param length the minimum file length in bytes, or zero for no minimum length
     * @return a file initialiser for the given access mode
     * @see #zeroBytes(AccessMode, long, long)
     */
    static FileInitialiser zeroBytes(final AccessMode mode, final long length) {
        return zeroBytes(mode, 0L, length);
    }

    /**
     * Returns a file initialiser that ensures the file is at least {@code end} bytes long, with behaviour depending
     * on the access mode:
     * <ul>
     *     <li>{@link AccessMode#READ_ONLY READ_ONLY}: fails if the file is shorter than {@code end}</li>
     *     <li>{@link AccessMode#READ_WRITE READ_WRITE}: appends zero bytes if the file is shorter than {@code end},
     *         leaving existing content untouched</li>
     *     <li>{@link AccessMode#READ_WRITE_CLEAR READ_WRITE_CLEAR}: overwrites the range {@code [start, end)} with zero
     *         bytes</li>
     * </ul>
     * File modifications are performed under a file lock. An {@code end} value of zero results in a no-op
     * initialiser.
     *
     * @param mode  the access mode the file is opened with
     * @param start the start position (inclusive) of the range to clear, only used for {@code READ_WRITE_CLEAR}
     * @param end   the end position (exclusive), also the minimum file length in bytes
     * @return a file initialiser for the given access mode
     */
    static FileInitialiser zeroBytes(final AccessMode mode, final long start, final long end) {
        return new FileInitialiserImpl(mode, start, end);
    }
}
