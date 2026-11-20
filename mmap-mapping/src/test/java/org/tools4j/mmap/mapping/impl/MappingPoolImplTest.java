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
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.MutableMappingPool;
import org.tools4j.mmap.mapping.api.OpenMode;
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

    private MutableMappingPool pool;

    @BeforeEach
    void open(@TempDir final Path tempDir) {
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(false)
                .maxFileSize(4L * REGION_SIZE)
                .regionSize(REGION_SIZE)
                .mappingStrategy(MappingStrategyConfig.getDefaultSync());
        pool = Mappings.mutableMappingPool(tempDir.resolve("pool.map").toFile(), OpenMode.OPEN_OR_CREATE, config, 4);
    }

    @AfterEach
    void close() {
        pool.close();
    }

    @Test
    void poolProperties() {
        assertEquals(AccessMode.READ_WRITE, pool.accessMode());
        assertEquals(REGION_SIZE, pool.regionMetrics().regionSize());
        assertFalse(pool.isClosed());
        pool.acquireMapping();
        assertEquals("MappingPoolImpl:mappings=1|accessMode=READ_WRITE|regionSize=" + REGION_SIZE + "|closed=false",
                pool.toString());
    }

    @Test
    void mappingsShareRegionsAndSeeEachOthersWrites() {
        final DynamicMutableMapping region = pool.acquireMapping();
        final DynamicMapping toRegionEnd = pool.acquireMapping();
        final DynamicMapping slice = pool.acquireMapping();
        assertTrue(region.moveTo(REGION_SIZE));
        assertTrue(toRegionEnd.moveTo(REGION_SIZE + 16L));
        assertTrue(slice.moveTo(REGION_SIZE + 16L, Long.BYTES));
        assertEquals(region.address() + 16, toRegionEnd.address());
        assertEquals(toRegionEnd.address(), slice.address());

        region.buffer().putLong(16, 42);
        assertEquals(42, toRegionEnd.buffer().getLong(0));
        assertEquals(42, slice.buffer().getLong(0));
    }

    @Test
    void sharedRegionStaysMappedUntilLastMappingMovesAway() {
        final DynamicMutableMapping first = pool.acquireMapping();
        final DynamicMapping second = pool.acquireMapping();
        assertTrue(first.moveTo(2L * REGION_SIZE));
        assertTrue(second.moveTo(2L * REGION_SIZE));
        first.buffer().putLong(0, 7);
        assertTrue(first.moveTo(0));
        assertEquals(7, second.buffer().getLong(0), "still mapped for second mapping");
    }

    @Test
    void closingPoolClosesMappingsAndRejectsNewOnes() {
        final DynamicMapping mapping = pool.acquireMapping();
        assertTrue(mapping.moveTo(REGION_SIZE));
        pool.close();
        assertTrue(pool.isClosed());
        assertTrue(mapping.isClosed());
        assertThrows(IllegalStateException.class, pool::acquireMapping);
        pool.close();
    }
}
