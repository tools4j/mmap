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
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.impl.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;

/**
 * Unit test for {@link FixedSizeFileMapper}.
 */
class FixedSizeFileMapperTest {

    private static final int LENGTH = 2 * (int) Constants.REGION_SIZE_GRANULARITY;

    @TempDir
    Path tempDir;

    @Test
    void readOnlyMappingIgnoresPreTouch() throws IOException {
        final File file = tempDir.resolve("data.map").toFile();
        final ByteBuffer content = ByteBuffer.allocate(LENGTH).order(ByteOrder.nativeOrder());
        for (int offset = 0; offset < LENGTH; offset += Long.BYTES) {
            content.putLong(offset, offset + 1);
        }
        Files.write(file.toPath(), content.array());
        try (final FixedSizeFileMapper mapper = new FixedSizeFileMapper(file, LENGTH, AccessMode.READ_ONLY,
                FileInitialiser.LAZY)) {
            //pre-touching writes, which would crash the JVM on a read-only mapping
            final long address = mapper.map(0, LENGTH, true);
            assertNotEquals(NULL_ADDRESS, address);
            final UnsafeBuffer buffer = new UnsafeBuffer(address, LENGTH);
            for (int offset = 0; offset < LENGTH; offset += Long.BYTES) {
                assertEquals(offset + 1, buffer.getLong(offset), "offset=" + offset);
            }
            mapper.unmap(0, address, LENGTH);
        }
    }
}
