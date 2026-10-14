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
package org.tools4j.mmap.mapping.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the default methods of {@link DynamicMapping}, {@link ElasticMapping} and {@link AdaptiveMapping},
 * exercised through the mapping implementations.
 */
class DynamicMappingDefaultMethodsTest {

    private static final int REGION_SIZE = MappingConfigurations.defaultRegionSize();
    private static final int REGIONS = 8;

    private RegionMapping region;
    private ElasticMapping elastic;
    private AdaptiveMapping adaptive;

    @BeforeEach
    void open(@TempDir final Path tempDir) {
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(false)
                .maxFileSize((long) REGIONS * REGION_SIZE)
                .mappingStrategy(MappingStrategyConfig.getDefaultSync());
        region = Mappings.regionMapping(tempDir.resolve("region.map").toFile(), AccessMode.READ_WRITE, config);
        elastic = Mappings.elasticMapping(tempDir.resolve("elastic.map").toFile(), AccessMode.READ_WRITE, config);
        adaptive = Mappings.adaptiveMapping(tempDir.resolve("adaptive.map").toFile(), AccessMode.READ_WRITE, config);
    }

    @AfterEach
    void close() {
        region.close();
        elastic.close();
        adaptive.close();
    }

    @Test
    void moveByStepsInMultiplesOfPositionStepSize() {
        assertThrows(IllegalStateException.class, () -> region.moveBy(REGION_SIZE));
        assertTrue(region.moveTo(REGION_SIZE));
        assertTrue(region.moveBy(REGION_SIZE));
        assertEquals(2L * REGION_SIZE, region.position());
        assertTrue(region.moveBy(-2L * REGION_SIZE));
        assertEquals(0, region.position());
        assertThrows(IllegalArgumentException.class, () -> region.moveBy(1));
        assertThrows(IllegalArgumentException.class, () -> region.moveBy(-REGION_SIZE));
    }

    @Test
    void moveToRegionByIndex() {
        for (final DynamicMapping mapping : new DynamicMapping[]{region, elastic, adaptive}) {
            assertTrue(mapping.moveToRegion(3), mapping.toString());
            assertEquals(3L * REGION_SIZE, mapping.position(), mapping.toString());
            assertThrows(IllegalArgumentException.class, () -> mapping.moveToRegion(-1), mapping.toString());
        }
    }

    @Test
    void moveToFirstNextAndPreviousRegion() {
        for (final DynamicMapping mapping : new DynamicMapping[]{region, elastic, adaptive}) {
            assertTrue(mapping.moveToNextRegion(), mapping.toString());
            assertEquals(0, mapping.position(), "unmapped next region is first region: " + mapping);
            assertThrows(IllegalStateException.class, mapping::moveToPreviousRegion, mapping.toString());
            assertTrue(mapping.moveToNextRegion(), mapping.toString());
            assertTrue(mapping.moveToNextRegion(), mapping.toString());
            assertEquals(2L * REGION_SIZE, mapping.position(), mapping.toString());
            assertTrue(mapping.moveToPreviousRegion(), mapping.toString());
            assertEquals(REGION_SIZE, mapping.position(), mapping.toString());
            assertTrue(mapping.moveToFirstRegion(), mapping.toString());
            assertEquals(0, mapping.position(), mapping.toString());
        }
    }

    @Test
    void elasticMoveWithinCurrentRegion() {
        assertThrows(IllegalStateException.class, elastic::moveToCurrentRegion);
        assertTrue(elastic.moveTo(2L * REGION_SIZE + 100));
        assertTrue(elastic.moveToCurrentRegion());
        assertEquals(2L * REGION_SIZE, elastic.position());
        assertEquals(REGION_SIZE, elastic.bytesAvailable());
        assertTrue(elastic.moveToCurrentRegion(50));
        assertEquals(2L * REGION_SIZE + 50, elastic.position());
        assertEquals(REGION_SIZE - 50, elastic.bytesAvailable());
        assertThrows(IllegalArgumentException.class, () -> elastic.moveToCurrentRegion(REGION_SIZE));
    }

    @Test
    void elasticMoveToOffsetInFirstNextAndPreviousRegion() {
        assertTrue(elastic.moveToNextRegion(10));
        assertEquals(10, elastic.position(), "unmapped next region is first region");
        assertThrows(IllegalStateException.class, () -> elastic.moveToPreviousRegion(10));
        assertTrue(elastic.moveToNextRegion(20));
        assertEquals(REGION_SIZE + 20L, elastic.position());
        assertTrue(elastic.moveToNextRegion(30));
        assertEquals(2L * REGION_SIZE + 30, elastic.position());
        assertTrue(elastic.moveToPreviousRegion(40));
        assertEquals(REGION_SIZE + 40L, elastic.position());
        assertTrue(elastic.moveToFirstRegion(50));
        assertEquals(50, elastic.position());
        assertThrows(IllegalArgumentException.class, () -> elastic.moveToFirstRegion(-1));
        assertThrows(IllegalArgumentException.class, () -> elastic.moveToNextRegion(REGION_SIZE));
        assertThrows(IllegalArgumentException.class, () -> elastic.moveToPreviousRegion(REGION_SIZE));
    }

    @Test
    void adaptiveMaxLength() {
        assertFalse(adaptive.isMapped());
        assertEquals(0, adaptive.maxLength(), "unmapped");
        assertEquals(0, adaptive.maxLengthAtPosition(-1));
        assertEquals(REGION_SIZE, adaptive.maxLengthAtPosition(REGION_SIZE));
        assertEquals(REGION_SIZE - 100, adaptive.maxLengthAtPosition(REGION_SIZE + 100L));
        assertTrue(adaptive.moveTo(100, 10));
        assertEquals(REGION_SIZE - 100, adaptive.maxLength());
    }

    @Test
    void adaptiveLength() {
        assertTrue(adaptive.moveTo(100, 10));
        assertEquals(10, adaptive.length());
        adaptive.length(20);
        assertEquals(20, adaptive.length());
        assertEquals(120, adaptive.limit());
        adaptive.length(20);
        assertEquals(20, adaptive.length());
        adaptive.length(-1);
        assertEquals(REGION_SIZE - 100, adaptive.length());
        adaptive.length(0);
        assertEquals(0, adaptive.length());
        assertThrows(IllegalArgumentException.class, () -> adaptive.length(-2));
        assertThrows(IllegalArgumentException.class, () -> adaptive.length(REGION_SIZE - 99));
        assertEquals(100, adaptive.position());
    }

    @Test
    void adaptiveLimit() {
        assertTrue(adaptive.moveTo(100, 10));
        adaptive.limit(150);
        assertEquals(50, adaptive.length());
        adaptive.limit(150);
        assertEquals(50, adaptive.length());
        adaptive.limit(100);
        assertEquals(0, adaptive.length());
        adaptive.limit(REGION_SIZE);
        assertEquals(REGION_SIZE - 100, adaptive.length());
        assertThrows(IllegalArgumentException.class, () -> adaptive.limit(99));
        assertThrows(IllegalArgumentException.class, () -> adaptive.limit(REGION_SIZE + 1L));
        assertEquals(100, adaptive.position());
    }

    @Test
    void adaptiveRegionMovesPreserveLength() {
        assertTrue(adaptive.moveTo(REGION_SIZE + 10L, 20));
        assertTrue(adaptive.moveToNextRegion());
        assertEquals(2L * REGION_SIZE, adaptive.position());
        assertEquals(20, adaptive.length());
        assertTrue(adaptive.moveToPreviousRegion());
        assertEquals(REGION_SIZE, adaptive.position());
        assertEquals(20, adaptive.length());
        assertTrue(adaptive.moveToFirstRegion());
        assertEquals(0, adaptive.position());
        assertEquals(20, adaptive.length());
    }
}
