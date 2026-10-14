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
package org.tools4j.mmap.mapping.unsafe;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link FileChannels} mapping modes.
 */
class FileChannelsTest {

    private static final int LENGTH = (int) REGION_SIZE_GRANULARITY;

    @TempDir
    Path tempDir;

    private Path file() throws IOException {
        final byte[] bytes = new byte[LENGTH];
        bytes[0] = 1;
        return Files.write(tempDir.resolve("channels.map"), bytes);
    }

    private static byte writeAndReadThroughMapping(final Path path, final MapMode mode) throws IOException {
        try (final RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw");
             final FileChannel channel = raf.getChannel()) {
            final long address = FileChannels.map(channel, mode, 0, LENGTH);
            try {
                final UnsafeBuffer buffer = new UnsafeBuffer(address, LENGTH);
                buffer.putByte(0, (byte) 2);
                return buffer.getByte(0);
            } finally {
                FileChannels.unmap(address, LENGTH);
            }
        }
    }

    @Test
    void readWriteMappingWritesToFile() throws IOException {
        final Path path = file();
        assertEquals(2, writeAndReadThroughMapping(path, MapMode.READ_WRITE));
        assertEquals(2, Files.readAllBytes(path)[0]);
    }

    @Test
    void privateMappingIsCopyOnWrite() throws IOException {
        final Path path = file();
        assertEquals(2, writeAndReadThroughMapping(path, MapMode.PRIVATE));
        assertEquals(1, Files.readAllBytes(path)[0], "private mapping does not write through to the file");
    }
}
