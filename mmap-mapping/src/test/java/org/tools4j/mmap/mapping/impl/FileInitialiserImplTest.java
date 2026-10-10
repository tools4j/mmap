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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link FileInitialiserImpl}.
 */
class FileInitialiserImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readOnlyThrowsIfFileTooShort() throws IOException {
        final Path file = fileWithBytes(1, 2, 3, 4);
        try (final RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            final FileChannel channel = raf.getChannel();
            assertThrows(IllegalArgumentException.class, () -> init(AccessMode.READ_ONLY, 0, 8, channel));
            assertDoesNotThrow(() -> init(AccessMode.READ_ONLY, 0, 4, channel));
            assertDoesNotThrow(() -> init(AccessMode.READ_ONLY, 0, 0, channel));
        }
    }

    @Test
    void readWriteAppendsZeroBytesKeepingContent() throws IOException {
        final Path file = fileWithBytes(1, 2, 3, 4);
        try (final RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            init(AccessMode.READ_WRITE, 2, 8, raf.getChannel());
        }
        assertArrayEquals(new byte[]{1, 2, 3, 4, 0, 0, 0, 0}, Files.readAllBytes(file));
    }

    @Test
    void readWriteClearOverwritesRangeWithZeroBytes() throws IOException {
        final Path file = fileWithBytes(1, 2, 3, 4);
        try (final RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            init(AccessMode.READ_WRITE_CLEAR, 2, 6, raf.getChannel());
        }
        assertArrayEquals(new byte[]{1, 2, 0, 0, 0, 0}, Files.readAllBytes(file));
    }

    @Test
    void zeroEndIsNoOp() throws IOException {
        final Path file = fileWithBytes(1, 2, 3, 4);
        for (final AccessMode mode : AccessMode.values()) {
            try (final RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                init(mode, 0, 0, raf.getChannel());
                assertEquals(4, raf.getChannel().size());
                final ByteBuffer bytes = ByteBuffer.allocate(4);
                raf.getChannel().read(bytes, 0);
                assertArrayEquals(new byte[]{1, 2, 3, 4}, bytes.array());
            }
        }
    }

    private Path fileWithBytes(final int... bytes) throws IOException {
        final byte[] content = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            content[i] = (byte) bytes[i];
        }
        return Files.write(tempDir.resolve("init.dat"), content);
    }

    private static void init(final AccessMode mode,
                             final long start,
                             final long end,
                             final FileChannel channel) throws IOException {
        final FileInitialiser initialiser = FileInitialiser.zeroBytes(mode, start, end);
        initialiser.init("init.dat", channel);
    }
}
