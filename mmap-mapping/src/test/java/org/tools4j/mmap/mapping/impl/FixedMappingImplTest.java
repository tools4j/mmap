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
import org.tools4j.mmap.mapping.api.FixedMapping;
import org.tools4j.mmap.mapping.api.Mappings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link FixedMappingImpl} creation failures with read-only files.
 */
class FixedMappingImplTest {

    @TempDir
    Path tempDir;

    @Test
    void readOnlyFileTooShortThrowsIllegalArgumentException() throws IOException {
        final File file = Files.write(tempDir.resolve("short.dat"), new byte[4]).toFile();
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Mappings.fixedMapping(file, AccessMode.READ_ONLY, 0, 8));
        assertTrue(e.getMessage().contains("expected file of size at least 8"), e.getMessage());
    }

    @Test
    void readOnlyFileEmptyThrowsIllegalStateException() throws IOException {
        final File file = Files.write(tempDir.resolve("empty.dat"), new byte[0]).toFile();
        assertThrows(IllegalStateException.class, () -> Mappings.fixedMapping(file, AccessMode.READ_ONLY, 0, 8));
    }

    @Test
    void readOnlyFileMissingThrowsIllegalStateException() {
        final File file = tempDir.resolve("missing.dat").toFile();
        assertThrows(IllegalStateException.class, () -> Mappings.fixedMapping(file, AccessMode.READ_ONLY, 0, 8));
    }

    @Test
    void readOnlyFileLongEnoughIsMapped() throws IOException {
        final File file = Files.write(tempDir.resolve("ok.dat"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8}).toFile();
        try (final FixedMapping mapping = Mappings.fixedMapping(file, AccessMode.READ_ONLY, 0, 8)) {
            assertEquals(8, mapping.bytesAvailable());
            assertEquals(8, mapping.buffer().getByte(7));
        }
    }
}
