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
 * Quick introduction to memory-mapped files, used as example in the README, see {@link Mappings}.
 */
class MappingIntroTest {

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
    void writeAndReadDynamicMappings(@TempDir final Path tempDir) {
        final File file = tempDir.resolve("data.map").toFile();
        final long mb = 1 << 20;

        //DynamicMapping: maps a slice of the file that can be moved, here spanning to the end of the region
        try (DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.OPEN_OR_CREATE)) {
            for (int i = 0; i < 10; i++) {
                mapping.moveTo(i * mb, DynamicMapping.STRETCH_LENGTH);
                mapping.buffer().putInt(0, 100 * i);
                mapping.buffer().putInt(4, 100 * i + 1);
            }
        }

        //moves keep the length, here 4 bytes for reading one int after another
        try (DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY)) {
            mapping.moveTo(5 * mb, Integer.BYTES);
            assertEquals(500, mapping.buffer().getInt(0));
            mapping.moveBy(Integer.BYTES);
            assertEquals(4, mapping.length());
            assertEquals(501, mapping.buffer().getInt(0));
        }

        //MappingPool: mappings from the same pool share mapped regions (one pool per thread)
        try (MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY)) {
            DynamicMapping first = pool.acquireMapping();
            DynamicMapping second = pool.acquireMapping();
            first.moveTo(0);
            second.moveTo(4, Integer.BYTES);
            assertEquals(0, first.buffer().getInt(0));
            assertEquals(1, second.buffer().getInt(0));
        }
    }

    @Test
    void rollingFilesWithAsyncMapping(@TempDir final Path tempDir) {
        //roll file every 1MB, map regions ahead in a background thread when accessed sequentially
        final int regionSize = 1 << 16;
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(true)
                .maxFileSize(1 << 20)
                .maxOpenFiles(4)
                .regionSize(regionSize)
                .mappingStrategy(cfg -> cfg.asyncMapping(true));
        final File file = tempDir.resolve("rolling.map").toFile();//creates rolling_0.map, rolling_1.map, ...

        try (var mapping = Mappings.dynamicMapping(file, OpenMode.OPEN_OR_CREATE, config)) {
            for (int region = 0; region < 64; region++) {
                mapping.moveTo((long) region * regionSize);
                mapping.buffer().putInt(0, region);
            }
        }
        try (var mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
            mapping.moveTo(63L * regionSize);
            assertEquals(63, mapping.buffer().getInt(0));
        }
    }
}
