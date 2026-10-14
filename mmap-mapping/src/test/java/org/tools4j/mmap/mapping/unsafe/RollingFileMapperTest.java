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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link RollingFileMapper}: rolling a logical position range over multiple files, file naming, creating
 * files ahead and limiting the number of open files.
 */
class RollingFileMapperTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int REGIONS_PER_FILE = 2;
    private static final long MAX_FILE_SIZE = (long) REGIONS_PER_FILE * REGION_SIZE;
    private static final FileInitialiser READ_WRITE_INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0);
    private static final FileInitialiser READ_ONLY_INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_ONLY, 0);

    @TempDir
    Path tempDir;

    private File baseFile(final String name) {
        return tempDir.resolve(name).toFile();
    }

    private static FileMapper readWrite(final File baseFile,
                                        final boolean expandFile,
                                        final int filesToCreateAhead,
                                        final int maxOpenFiles) {
        return RollingFileMapper.forReadWrite(baseFile, AccessMode.READ_WRITE, expandFile, 0, MAX_FILE_SIZE,
                REGION_SIZE, filesToCreateAhead, maxOpenFiles, READ_WRITE_INITIALISER);
    }

    private static FileMapper readOnly(final File baseFile, final int maxOpenFiles) {
        return RollingFileMapper.forReadOnly(baseFile, MAX_FILE_SIZE, REGION_SIZE, maxOpenFiles, READ_ONLY_INITIALISER);
    }

    private static long position(final int region) {
        return (long) region * REGION_SIZE;
    }

    private static void writeRegions(final FileMapper mapper, final int regions) {
        for (int region = 0; region < regions; region++) {
            final long address = mapper.map(position(region), REGION_SIZE);
            assertNotEquals(NULL_ADDRESS, address, "region " + region);
            final UnsafeBuffer buffer = new UnsafeBuffer(address, REGION_SIZE);
            buffer.putLong(0, valueFor(region));
            buffer.putLong(REGION_SIZE - Long.BYTES, ~valueFor(region));
            mapper.unmap(position(region), address, REGION_SIZE);
        }
    }

    private static void assertRegions(final FileMapper mapper, final int regions) {
        for (int region = 0; region < regions; region++) {
            final long address = mapper.map(position(region), REGION_SIZE);
            assertNotEquals(NULL_ADDRESS, address, "region " + region);
            final UnsafeBuffer buffer = new UnsafeBuffer(address, REGION_SIZE);
            assertEquals(valueFor(region), buffer.getLong(0), "region " + region);
            assertEquals(~valueFor(region), buffer.getLong(REGION_SIZE - Long.BYTES), "region " + region);
            mapper.unmap(position(region), address, REGION_SIZE);
        }
    }

    private static long valueFor(final int region) {
        return 0x1234_5678_0000_0000L + region;
    }

    @ParameterizedTest(name = "expandFile={0}")
    @ValueSource(booleans = {false, true})
    void writesRollOverFilesAndCanBeReadBack(final boolean expandFile) {
        final File baseFile = baseFile("data.map");
        try (final FileMapper mapper = readWrite(baseFile, expandFile, 0, 2)) {
            assertEquals(AccessMode.READ_WRITE, mapper.accessMode());
            writeRegions(mapper, 3 * REGIONS_PER_FILE);
        }
        for (int fileIndex = 0; fileIndex < 3; fileIndex++) {
            final File file = baseFile("data_" + fileIndex + ".map");
            assertTrue(file.isFile(), file.toString());
            assertEquals(MAX_FILE_SIZE, file.length(), file.toString());
        }
        assertFalse(baseFile("data_3.map").exists());
        assertFalse(baseFile.exists(), "base file itself is never created");

        try (final FileMapper mapper = readOnly(baseFile, 1)) {
            assertEquals(AccessMode.READ_ONLY, mapper.accessMode());
            assertRegions(mapper, 3 * REGIONS_PER_FILE);
            assertEquals(NULL_ADDRESS, mapper.map(position(3 * REGIONS_PER_FILE), REGION_SIZE),
                    "read-only mapping of file that does not exist");
        }
    }

    @Test
    void expandableFilesGrowOnDemand() {
        final File baseFile = baseFile("grow.map");
        try (final FileMapper mapper = readWrite(baseFile, true, 0, 2)) {
            writeRegions(mapper, 1);
            assertEquals(REGION_SIZE, baseFile("grow_0.map").length());
        }
    }

    @Test
    void fileNameWithoutExtension() {
        final File baseFile = baseFile("data");
        try (final FileMapper mapper = readWrite(baseFile, false, 0, 2)) {
            writeRegions(mapper, REGIONS_PER_FILE + 1);
        }
        assertTrue(baseFile("data_0").isFile());
        assertTrue(baseFile("data_1").isFile());
    }

    @Test
    void filesAreCreatedAhead() {
        final File baseFile = baseFile("ahead.map");
        try (final FileMapper mapper = readWrite(baseFile, false, 2, 3)) {
            writeRegions(mapper, 1);
            assertTrue(baseFile("ahead_0.map").isFile());
            assertTrue(baseFile("ahead_1.map").isFile());
            assertTrue(baseFile("ahead_2.map").isFile());
            assertFalse(baseFile("ahead_3.map").exists());
        }
    }

    @Test
    void filesCreatedAheadRespectMaxOpenFiles() {
        final File baseFile = baseFile("ahead.map");
        try (final FileMapper mapper = readWrite(baseFile, false, 1, 2)) {
            writeRegions(mapper, 4 * REGIONS_PER_FILE);
            assertTrue(mapper.toString().contains("|openFiles=2|"), mapper.toString());
        }
        try (final FileMapper mapper = readOnly(baseFile, 2)) {
            assertRegions(mapper, 4 * REGIONS_PER_FILE);
        }
    }

    @Test
    void leastRecentlyUsedFileIsClosedWhenExceedingMaxOpenFiles() {
        final File baseFile = baseFile("lru.map");
        try (final FileMapper mapper = readWrite(baseFile, false, 0, 2)) {
            writeRegions(mapper, 3 * REGIONS_PER_FILE);
            assertTrue(mapper.toString().contains("|openFiles=2|"), mapper.toString());
            //file 0 was closed and is reopened; data is still there
            assertRegions(mapper, REGIONS_PER_FILE);
            assertTrue(mapper.toString().contains("|openFiles=2|"), mapper.toString());
        }
    }

    @Test
    void negativePositionIsNotMapped() {
        try (final FileMapper mapper = readWrite(baseFile("neg.map"), false, 0, 2)) {
            assertEquals(NULL_ADDRESS, mapper.map(-REGION_SIZE, REGION_SIZE));
        }
    }

    @Test
    void lengthMustMatchRegionSize() {
        try (final FileMapper mapper = readWrite(baseFile("len.map"), false, 0, 2)) {
            assertThrows(IllegalArgumentException.class, () -> mapper.map(0, REGION_SIZE / 2));
            final long address = mapper.map(0, REGION_SIZE);
            assertThrows(IllegalArgumentException.class, () -> mapper.unmap(0, address, REGION_SIZE / 2));
            mapper.unmap(0, address, REGION_SIZE);
        }
    }

    @Test
    void invalidConfigurationIsRejected() {
        final File baseFile = baseFile("invalid.map");
        assertThrows(IllegalArgumentException.class, () -> RollingFileMapper.forReadWrite(baseFile,
                AccessMode.READ_WRITE, false, 0, REGION_SIZE / 2, REGION_SIZE, 0, 2, READ_WRITE_INITIALISER));
        assertThrows(IllegalArgumentException.class, () -> readWrite(baseFile, false, 2, 2),
                "filesToCreateAhead must be smaller than maxOpenFiles");
        assertThrows(IllegalArgumentException.class, () -> readWrite(baseFile, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> readWrite(baseFile, false, -1, 2));
    }

    @Test
    void closedMapperRejectsMappingAndClosesOnlyOnce() {
        final FileMapper mapper = readWrite(baseFile("closed.map"), false, 0, 2);
        writeRegions(mapper, 1);
        assertFalse(mapper.isClosed());
        mapper.close();
        assertTrue(mapper.isClosed());
        assertTrue(mapper.toString().contains("|openFiles=0|closed=true"), mapper.toString());
        mapper.close();
        assertThrows(IllegalStateException.class, () -> mapper.map(0, REGION_SIZE));
    }
}
