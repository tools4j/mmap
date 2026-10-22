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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quick introduction to memory-mapped files, used as example in the README.
 */
class QuickIntroTest {

    @Test
    void writeAndReadFixedMapping(@TempDir final Path tempDir) {
        final File file = tempDir.resolve("fixed.map").toFile();

        //FixedMapping: maps a fixed slice of the file, here the first 64 bytes
        try (FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.OPEN_OR_CREATE, 0, 64)) {
            mapping.buffer().putInt(0, 42);
            mapping.buffer().putInt(60, 43);
        }

        //without length, the mapping spans from the offset to the end of the file
        try (FixedMapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, 60)) {
            assertEquals(4, mapping.bytesAvailable());
            assertEquals(43, mapping.buffer().getInt(0));
        }
    }

    @Test
    void writeAndReadRegions(@TempDir final Path tempDir) {
        final File file = tempDir.resolve("data.map").toFile();

        //RegionMapping: exposes a whole region of the file and is moved region by region
        try (RegionMapping mapping = Mappings.regionMapping(file, OpenMode.OPEN_OR_CREATE)) {
            for (int region = 0; region < 3; region++) {
                mapping.moveToRegion(region);
                mapping.buffer().putInt(0, 100 * region);
                mapping.buffer().putInt(4, 100 * region + 1);
            }
        }

        //ElasticMapping: can be moved to any position, its buffer spans to the end of the region
        try (ElasticMapping mapping = Mappings.elasticMapping(file, OpenMode.READ_ONLY)) {
            mapping.moveTo(2L * mapping.regionSize());
            assertEquals(200, mapping.buffer().getInt(0));
            mapping.moveBy(4);
            assertEquals(201, mapping.buffer().getInt(0));
        }

        //AdaptiveMapping: maps an arbitrary slice of the underlying file from one region
        try (AdaptiveMapping mapping = Mappings.adaptiveMapping(file, OpenMode.READ_ONLY)) {
            int readLength = 8;
            mapping.moveTo(2L * mapping.regionSize(), readLength);//buffer capped at size 8
            assertEquals(200, mapping.buffer().getInt(0));
            assertEquals(201, mapping.buffer().getInt(4));
        }

        //MappingPool: mappings from the same pool share mapped regions (one pool per thread)
        try (MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY)) {
            ElasticMapping elastic = pool.acquireElasticMapping();
            AdaptiveMapping adaptive = pool.acquireAdaptiveMapping();
            elastic.moveToFirstRegion();
            adaptive.moveTo(4, Integer.BYTES);
            assertEquals(0, elastic.buffer().getInt(0));
            assertEquals(1, adaptive.buffer().getInt(0));
        }
    }

    @Test
    void rollingFilesWithAsyncMapping(@TempDir final Path tempDir) {
        //roll file every 1MB, map regions ahead in a background thread when accessed sequentially
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(true)
                .maxFileSize(1 << 20)
                .maxOpenFiles(4)
                .mappingStrategy(cfg -> cfg.asyncMapping(true).regionSize(1 << 16));
        final File file = tempDir.resolve("rolling.map").toFile();//creates rolling_0.map, rolling_1.map, ...

        try (var mapping = Mappings.regionMapping(file, OpenMode.OPEN_OR_CREATE, config)) {
            for (int region = 0; region < 64; region++) {
                mapping.moveToRegion(region);
                mapping.buffer().putInt(0, region);
            }
        }
        try (var mapping = Mappings.regionMapping(file, OpenMode.READ_ONLY, config)) {
            mapping.moveToRegion(63);
            assertEquals(63, mapping.buffer().getInt(0));
        }
    }
}
