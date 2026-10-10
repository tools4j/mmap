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
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.AdaptiveMapping;
import org.tools4j.mmap.mapping.api.Mappings;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link AdaptiveMappingImpl} length handling, in particular the -1 "to end of region" length.
 */
class AdaptiveMappingImplTest {

    private AdaptiveMapping mapping;
    private int regionSize;

    @BeforeEach
    void init(@TempDir final Path tempDir) {
        mapping = Mappings.adaptiveMapping(tempDir.resolve("adaptive.map").toFile(), AccessMode.READ_WRITE);
        regionSize = mapping.regionSize();
    }

    @AfterEach
    void close() {
        mapping.close();
    }

    @Test
    void moveToWithLengthToEndOfRegion() {
        assertTrue(mapping.moveTo(100, -1));
        assertEquals(100, mapping.position());
        assertEquals(regionSize - 100, mapping.length());

        assertTrue(mapping.moveTo(200, -1));
        assertEquals(regionSize - 200, mapping.length());

        assertTrue(mapping.moveTo(regionSize + 300L, -1));
        assertEquals(regionSize + 300L, mapping.position());
        assertEquals(regionSize - 300, mapping.length());
    }

    @Test
    void moveByWithLengthToEndOfRegion() {
        assertTrue(mapping.moveTo(100, 10));

        assertTrue(mapping.moveBy(50, -1));
        assertEquals(150, mapping.position());
        assertEquals(regionSize - 150, mapping.length());

        assertTrue(mapping.moveBy(regionSize, -1));
        assertEquals(regionSize + 150L, mapping.position());
        assertEquals(regionSize - 150, mapping.length());
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
    void invalidLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(100, -2));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(100, regionSize - 99));
        assertTrue(mapping.moveTo(100, 10));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(10, -2));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(10, regionSize - 109));
    }
}
