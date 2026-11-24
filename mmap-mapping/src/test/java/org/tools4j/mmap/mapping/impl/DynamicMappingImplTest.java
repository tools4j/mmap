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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurator;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link DynamicMappingImpl} length handling, in particular {@link DynamicMapping#STRETCH_LENGTH}, with
 * the default overlap, and moves to existing regions only.
 */
class DynamicMappingImplTest {

    @TempDir
    Path tempDir;

    private DynamicMapping mapping;
    private int regionSize;
    private int overlapSize;
    private int mappingSize;

    @BeforeEach
    void init() {
        mapping = Mappings.dynamicMutableMapping(tempDir.resolve("dynamic.map").toFile(), OpenMode.OPEN_OR_CREATE);
        regionSize = mapping.regionMetrics().regionSize();
        overlapSize = mapping.regionMetrics().overlapSize();
        mappingSize = mapping.regionMetrics().mappingSize();
        assertTrue(overlapSize > 0, "default overlap");
    }

    @AfterEach
    void close() {
        mapping.close();
    }

    @Test
    void moveToWithStretchLength() {
        assertTrue(mapping.moveTo(100, DynamicMapping.STRETCH_LENGTH));
        assertEquals(100, mapping.position());
        assertEquals(mappingSize - 100, mapping.length());

        assertTrue(mapping.moveTo(200, DynamicMapping.STRETCH_LENGTH));
        assertEquals(mappingSize - 200, mapping.length());

        assertTrue(mapping.moveTo(regionSize + 300L, DynamicMapping.STRETCH_LENGTH));
        assertEquals(regionSize + 300L, mapping.position());
        assertEquals(mappingSize - 300, mapping.length());
    }

    @Test
    void moveByWithStretchLength() {
        assertTrue(mapping.moveTo(100, 10));

        assertTrue(mapping.moveBy(50, DynamicMapping.STRETCH_LENGTH));
        assertEquals(150, mapping.position());
        assertEquals(mappingSize - 150, mapping.length());

        assertTrue(mapping.moveBy(regionSize, DynamicMapping.STRETCH_LENGTH));
        assertEquals(regionSize + 150L, mapping.position());
        assertEquals(mappingSize - 150, mapping.length());
    }

    @Test
    void moveToAndMoveByWithExplicitLength() {
        assertTrue(mapping.moveTo(100, 10));
        assertEquals(10, mapping.length());

        assertTrue(mapping.moveBy(regionSize, 20));
        assertEquals(regionSize + 100L, mapping.position());
        assertEquals(20, mapping.length());
    }

    @Test
    void movesStretchByDefault() {
        assertTrue(mapping.moveTo(100));
        assertEquals(mappingSize - 100, mapping.length());
        assertTrue(mapping.moveTo(200));
        assertEquals(mappingSize - 200, mapping.length());
        assertTrue(mapping.moveBy(regionSize));
        assertEquals(regionSize + 200L, mapping.position());
        assertEquals(mappingSize - 200, mapping.length());
        assertTrue(mapping.moveTo(regionSize - 1L));
        assertEquals(overlapSize + 1, mapping.length());
    }

    @Test
    void movesKeepRequestedLength() {
        assertThrows(IllegalStateException.class, () -> mapping.moveBy(10));
        assertTrue(mapping.moveTo(100, 10));

        assertTrue(mapping.moveBy(50));
        assertEquals(150, mapping.position());
        assertEquals(10, mapping.length());

        assertTrue(mapping.moveTo(regionSize - 10L));
        assertEquals(10, mapping.length());

        assertTrue(mapping.moveTo(regionSize + 300L));
        assertEquals(10, mapping.length(), "kept in another region");

        mapping.length(DynamicMapping.STRETCH_LENGTH);
        assertEquals(mappingSize - 300, mapping.length());
        assertTrue(mapping.moveTo(regionSize + 400L));
        assertEquals(mappingSize - 400, mapping.length(), "max length requested again");
    }

    @Test
    void moveThrowsIfRequestedLengthDoesNotFitAndKeepsMapping() {
        final int length = overlapSize + 10;
        assertTrue(mapping.moveTo(100, length));
        final long address = mapping.address();

        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(regionSize - 5L));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(regionSize - 105L));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(2L * regionSize - 5));
        assertEquals(100, mapping.position());
        assertEquals(length, mapping.length());
        assertEquals(address, mapping.address());

        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(200, mappingSize - 199));
        assertTrue(mapping.moveTo(200));
        assertEquals(length, mapping.length(), "requested length kept after failed move");
        assertTrue(mapping.moveTo(regionSize - 10L));
        assertEquals(length, mapping.length(), "fits into overlap");
    }

    @Test
    void lengthAndLimitSetRequestedLength() {
        assertTrue(mapping.moveTo(100));
        mapping.length(10);
        assertTrue(mapping.moveTo(200));
        assertEquals(10, mapping.length());
        mapping.limit(250);
        assertTrue(mapping.moveTo(300));
        assertEquals(50, mapping.length());
    }

    @Test
    void moveToSamePositionOnlyAdjustsLength() {
        assertTrue(mapping.moveTo(100, 10));
        final long address = mapping.address();
        assertTrue(mapping.moveTo(100, 10));
        assertEquals(10, mapping.length());
        assertTrue(mapping.moveTo(100, 20));
        assertEquals(20, mapping.length());
        assertEquals(address, mapping.address());
    }

    @Test
    void closeIsIdempotent() {
        assertTrue(mapping.moveTo(100, 10));
        mapping.close();
        assertTrue(mapping.isClosed());
        mapping.close();
        assertTrue(mapping.isClosed());
    }

    @Test
    void lengthSetsLengthAtSamePosition() {
        assertTrue(mapping.moveTo(100, 10));
        assertEquals(10, mapping.length());
        mapping.length(20);
        assertEquals(20, mapping.length());
        assertEquals(mapping.length(), mapping.buffer().capacity());
        assertEquals(120, mapping.limit());
        mapping.length(20);
        assertEquals(20, mapping.length());
        mapping.length(DynamicMapping.STRETCH_LENGTH);
        assertEquals(mappingSize - 100, mapping.length());
        mapping.length(0);
        assertEquals(0, mapping.length());
        assertThrows(IllegalArgumentException.class, () -> mapping.length(-2));
        assertThrows(IllegalArgumentException.class, () -> mapping.length(mappingSize - 99));
        assertEquals(100, mapping.position());
    }

    @Test
    void limitSetsLengthAtSamePosition() {
        assertTrue(mapping.moveTo(100, 10));
        mapping.limit(150);
        assertEquals(50, mapping.length());
        assertEquals(mapping.length(), mapping.buffer().capacity());
        mapping.limit(150);
        assertEquals(50, mapping.length());
        mapping.limit(100);
        assertEquals(0, mapping.length());
        mapping.limit(regionSize);
        assertEquals(regionSize - 100, mapping.length());
        mapping.limit(mappingSize);
        assertEquals(mappingSize - 100, mapping.length());
        assertThrows(IllegalArgumentException.class, () -> mapping.limit(99));
        assertThrows(IllegalArgumentException.class, () -> mapping.limit(mappingSize + 1L));
        assertEquals(100, mapping.position());
    }

    @Test
    void invalidLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(100, -2));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(100, mappingSize - 99));
        assertTrue(mapping.moveTo(100, 10));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(10, -2));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(10, mappingSize - 109));
    }

    /** A single expandable file: min file size 2 regions, max file size 8 regions */
    private MappingConfig expandableConfig() {
        return MappingConfigurator.configure()
                .regionSize(regionSize)
                .minFileSize(2L * regionSize)
                .maxFileSize(8L * regionSize)
                .expandFile(true)
                .rollFiles(false);
    }

    @Test
    void moveToExistingNeitherCreatesNorExtendsTheFile() {
        final File file = tempDir.resolve("existing.map").toFile();
        final MappingConfig config = expandableConfig();
        final long lastRegion;
        try (final DynamicMutableMapping writer = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                config)) {
            assertFalse(writer.moveToExisting(0), "file does not exist");
            assertFalse(file.exists(), "file not created");
            assertTrue(writer.moveTo(100));
            final long fileLength = file.length();
            lastRegion = fileLength - writer.regionMetrics().mappingSize();
            assertEquals(regionSize, lastRegion, "min file size of 2 regions plus overlap");

            assertTrue(writer.moveToExisting(lastRegion + 8, 8));
            assertEquals(lastRegion + 8, writer.position());
            assertEquals(8, writer.length());
            assertFalse(writer.moveToExisting(lastRegion + regionSize), "region beyond the file length");
            assertFalse(writer.moveToExisting(8L * regionSize), "region beyond the max file size");
            assertEquals(lastRegion + 8, writer.position(), "mapping stays where it was");
            assertEquals(fileLength, file.length(), "file not extended");
        }
        try (final DynamicMapping reader = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
            assertTrue(reader.moveToExisting(lastRegion));
            assertFalse(reader.moveToExisting(lastRegion + regionSize), "like moveTo for read-only");
            assertFalse(reader.moveTo(lastRegion + regionSize));
        }
    }

    @Test
    void searchesNeitherExtendTheFileNorProbeBeyondIt() {
        final File file = tempDir.resolve("search.map").toFile();
        try (final DynamicMutableMapping writer = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                expandableConfig())) {
            assertTrue(writer.moveTo(0));
            final long fileLength = file.length();
            final long lastRegion = fileLength - writer.regionMetrics().mappingSize();

            assertTrue(writer.binarySearchLast(0, regionSize, m -> true), "every existing position matches");
            assertEquals(lastRegion, writer.position(), "last existing region");
            assertTrue(writer.findLast(0, regionSize, m -> true));
            assertEquals(lastRegion, writer.position(), "last existing region");
            assertEquals(fileLength, file.length(), "file not extended");
        }
    }
}
