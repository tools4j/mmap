/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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

@FunctionalInterface
public interface FileInitialiser {

    void init(String fileName, FileChannel fileChannel) throws IOException;

    static FileInitialiser forMode(final MappedFile.Mode mode) {
        switch (mode) {
            case READ_ONLY:
                return (fileName, fileChannel) -> {
                    if (fileChannel.size() < 8) {
                        throw new IllegalArgumentException("Invalid file format: " + fileName);
                    }
                };
            case READ_WRITE:
                return (fileName, fileChannel) -> {
                    forMode(fileChannel.size() == 0 ? MappedFile.Mode.READ_WRITE_CLEAR : MappedFile.Mode.READ_ONLY)
                            .init(fileName, fileChannel);
                };
            case READ_WRITE_CLEAR:
                return (fileName, fileChannel) -> {
                    final FileLock lock = fileChannel.lock();
                    try {
                        fileChannel.truncate(0);
                        fileChannel.transferFrom(InitialBytes.ZERO, 0, 8);
                        fileChannel.force(true);
                    } finally {
                        lock.release();
                    }
                };
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);

        }
    }
}
