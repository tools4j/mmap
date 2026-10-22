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
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.MappingPool;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.api.RegionMapping;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link MappingPoolImpl}: mappings acquired from one pool share mapped regions, and closing the pool
 * closes all its mappings.
 */
class MappingPoolImplTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;

    private MappingPool pool;

    @BeforeEach
    void open(@TempDir final Path tempDir) {
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(false)
                .maxFileSize(4L * REGION_SIZE)
                .mappingStrategy(MappingStrategyConfig.configure(MappingStrategyConfig.getDefaultSync())
                        .regionSize(REGION_SIZE));
        pool = Mappings.mappingPool(tempDir.resolve("pool.map").toFile(), OpenMode.OPEN_OR_CREATE, config, 4);
    }

    @AfterEach
    void close() {
        pool.close();
    }

    @Test
    void poolProperties() {
        assertEquals(AccessMode.READ_WRITE, pool.accessMode());
        assertEquals(REGION_SIZE, pool.regionSize());
        assertEquals(REGION_SIZE, pool.regionMetrics().regionSize());
        assertFalse(pool.isClosed());
        pool.acquireElasticMapping();
        assertEquals("MappingPoolImpl:mappings=1|accessMode=READ_WRITE|regionSize=" + REGION_SIZE + "|closed=false",
                pool.toString());
    }

    @Test
    void mappingsShareRegionsAndSeeEachOthersWrites() {
        final RegionMapping region = pool.acquireRegionMapping();
        final ElasticMapping elastic = pool.acquireElasticMapping();
        final AdaptiveMapping adaptive = pool.acquireAdaptiveMapping();
        assertTrue(region.moveToRegion(1));
        assertTrue(elastic.moveTo(REGION_SIZE + 16L));
        assertTrue(adaptive.moveTo(REGION_SIZE + 16L, Long.BYTES));
        assertEquals(region.address() + 16, elastic.address());
        assertEquals(elastic.address(), adaptive.address());

        region.buffer().putLong(16, 42);
        assertEquals(42, elastic.buffer().getLong(0));
        assertEquals(42, adaptive.buffer().getLong(0));
    }

    @Test
    void sharedRegionStaysMappedUntilLastMappingMovesAway() {
        final ElasticMapping first = pool.acquireElasticMapping();
        final ElasticMapping second = pool.acquireElasticMapping();
        assertTrue(first.moveToRegion(2));
        assertTrue(second.moveToRegion(2));
        first.buffer().putLong(0, 7);
        assertTrue(first.moveToRegion(0));
        assertEquals(7, second.buffer().getLong(0), "still mapped for second mapping");
    }

    @Test
    void closingPoolClosesMappingsAndRejectsNewOnes() {
        final ElasticMapping mapping = pool.acquireElasticMapping();
        assertTrue(mapping.moveToRegion(1));
        pool.close();
        assertTrue(pool.isClosed());
        assertTrue(mapping.isClosed());
        assertThrows(IllegalStateException.class, pool::acquireElasticMapping);
        assertThrows(IllegalStateException.class, pool::acquireRegionMapping);
        assertThrows(IllegalStateException.class, pool::acquireAdaptiveMapping);
        pool.close();
    }
}
