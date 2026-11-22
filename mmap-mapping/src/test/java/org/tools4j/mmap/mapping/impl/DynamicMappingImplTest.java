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
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link DynamicMappingImpl} length handling, in particular {@link DynamicMapping#STRETCH_LENGTH}, with
 * the default overlap.
 */
class DynamicMappingImplTest {

    private DynamicMapping mapping;
    private int regionSize;
    private int overlapSize;
    private int mappingSize;

    @BeforeEach
    void init(@TempDir final Path tempDir) {
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
}
