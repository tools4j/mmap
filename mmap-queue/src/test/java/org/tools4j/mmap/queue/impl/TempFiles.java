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
package org.tools4j.mmap.queue.impl;

import java.io.File;
import java.io.IOException;

/**
 * Temporary files and directories of tests, in the temp directory or in shared memory if available.
 */
enum TempFiles {
    ;
    static final File IO_TMPDIR = new File(System.getProperty("java.io.tmpdir"));
    static final File SHARED_MEM_DIR = new File("/dev/shm");

    static File tmpDirFile(final String name) {
        return new File(IO_TMPDIR, name);
    }

    static File sharedMemDir(final String name) {
        return SHARED_MEM_DIR.isDirectory() ? new File(SHARED_MEM_DIR, name) : tmpDirFile(name);
    }

    static void deleteTmpDirFilesMatching(final String namePrefix) throws IOException {
        deleteFilesMatching(IO_TMPDIR, namePrefix);
    }
    static void deleteFilesMatching(final File dir, final String namePrefix) throws IOException {
        for (final File file : dir.listFiles((d, n) -> n.startsWith(namePrefix))) {
            deleteRecursively(file);
        }
    }

    static void deleteRecursively(final File file) throws IOException {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                deleteRecursively(sub);
            }
        }
        if (!file.delete()) {
            throw new IOException("could not delete: " + file.getAbsolutePath());
        }
    }
}
