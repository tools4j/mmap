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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link ReadOnlyFileMapper}.
 */
class ReadOnlyFileMapperTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final FileInitialiser INITIALISER = FileInitialiser.none();

    @TempDir
    Path tempDir;

    private Path fileWithRegions(final int regions) throws IOException {
        final byte[] bytes = new byte[regions * REGION_SIZE];
        for (int region = 0; region < regions; region++) {
            bytes[region * REGION_SIZE] = (byte) (region + 1);
        }
        return Files.write(tempDir.resolve("readonly.map"), bytes);
    }

    private static byte firstByte(final FileMapper mapper, final int region) {
        final long position = (long) region * REGION_SIZE;
        final long address = mapper.map(position, REGION_SIZE);
        assertNotEquals(NULL_ADDRESS, address, "region " + region);
        try {
            return new UnsafeBuffer(address, REGION_SIZE).getByte(0);
        } finally {
            mapper.unmap(position, address, REGION_SIZE);
        }
    }

    @Test
    void mapsExistingRegions() throws IOException {
        final Path path = fileWithRegions(2);
        try (final FileMapper mapper = new ReadOnlyFileMapper(path.toString(), INITIALISER)) {
            assertEquals(AccessMode.READ_ONLY, mapper.accessMode());
            assertEquals(1, firstByte(mapper, 0));
            assertEquals(2, firstByte(mapper, 1));
        }
    }

    @Test
    void mappingBeyondFileEndFailsUntilFileHasGrown() throws IOException {
        final Path path = fileWithRegions(1);
        try (final FileMapper mapper = new ReadOnlyFileMapper(path.toFile(), INITIALISER)) {
            assertEquals(1, firstByte(mapper, 0));
            assertEquals(NULL_ADDRESS, mapper.map(REGION_SIZE, REGION_SIZE));
            final byte[] secondRegion = new byte[REGION_SIZE];
            secondRegion[0] = 42;
            Files.write(path, secondRegion, StandardOpenOption.APPEND);
            assertEquals(42, firstByte(mapper, 1));
        }
    }

    @Test
    void missingOrEmptyFileIsNotMapped() throws IOException {
        final File missing = tempDir.resolve("missing.map").toFile();
        try (final FileMapper mapper = new ReadOnlyFileMapper(missing, INITIALISER)) {
            assertEquals(NULL_ADDRESS, mapper.map(0, REGION_SIZE));
            assertFalse(missing.exists(), "read-only mapper does not create files");
        }
        final File empty = Files.createFile(tempDir.resolve("empty.map")).toFile();
        try (final FileMapper mapper = new ReadOnlyFileMapper(empty, INITIALISER)) {
            assertEquals(NULL_ADDRESS, mapper.map(0, REGION_SIZE));
        }
    }

    @Test
    void closedMapperRejectsMapAndUnmap() throws IOException {
        final FileMapper mapper = new ReadOnlyFileMapper(fileWithRegions(1).toFile(), INITIALISER);
        final long address = mapper.map(0, REGION_SIZE);
        mapper.close();
        assertTrue(mapper.isClosed());
        assertTrue(mapper.toString().endsWith("|closed=true"), mapper.toString());
        mapper.close();
        assertThrows(IllegalStateException.class, () -> mapper.map(0, REGION_SIZE));
        assertThrows(IllegalStateException.class, () -> mapper.unmap(0, address, REGION_SIZE));
        FileChannels.unmap(address, REGION_SIZE);
    }
}
