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
package org.tools4j.mmap.mapping.api;

import org.tools4j.mmap.mapping.impl.MinLengthFileInitialiser;

import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * File initialiser invoked when a file is opened for mapping, before it is mapped, for instance to ensure or validate
 * a minimum file length, or to write or validate a file header.
 */
@FunctionalInterface
public interface FileInitialiser {

    /**
     * Initialises the file channel, or validates the file if it is opened for reading only.
     *
     * @param fileName    file name for reference
     * @param fileChannel file channel to initialise
     * @param accessMode  the access mode the file is opened with
     * @throws IOException thrown when file channel could not be initialised.
     */
    void init(String fileName, FileChannel fileChannel, AccessMode accessMode) throws IOException;

    /**
     * Returns a file initialiser that does nothing.
     *
     * @return a no-op file initialiser
     */
    static FileInitialiser none() {
        return MinLengthFileInitialiser.NONE;
    }

    /**
     * Returns a file initialiser that ensures the file is at least the given length, with behaviour depending on the
     * access mode the file is opened with:
     * <ul>
     *     <li>{@link AccessMode#READ_ONLY READ_ONLY}: throws an {@link IllegalArgumentException} if the file is
     *         shorter than {@code length}</li>
     *     <li>{@link AccessMode#READ_WRITE READ_WRITE}: appends zero bytes if the file is shorter than
     *         {@code length}, leaving existing content untouched</li>
     * </ul>
     * File modifications are performed under a file lock.
     *
     * @param length the minimum file length in bytes, or zero for no minimum length
     * @return a file initialiser ensuring the given minimum length
     * @throws IllegalArgumentException if length is negative
     */
    static FileInitialiser minLength(final long length) {
        return length == 0 ? none() : new MinLengthFileInitialiser(length);
    }
}
