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

import java.io.IOException;

/**
 * Allocates file space natively, implemented through FFM on Java 22 or later in {@code FfmFileAllocator}, loaded by
 * name by {@link FileChannels}.
 */
interface FileAllocator {
    /**
     * Allocates the given range of the file, extending the file if the range ends beyond it, so that it reads as zero;
     * allocates nothing on the Java heap if successful.
     *
     * @param fd       the file descriptor number, of a file open for writing
     * @param position the start of the range
     * @param length   the length of the range, positive
     * @return true if allocated, false if the file system does not support it
     * @throws IOException if allocating fails otherwise, for instance if the disk is full
     */
    boolean allocate(int fd, long position, long length) throws IOException;
}
