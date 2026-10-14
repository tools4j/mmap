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
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.FixedSizeFileMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for the {@link Mappings} factory methods, writing through one mapping and reading back through another.
 */
class MappingsTest {

    private static final int REGION_SIZE = MappingConfigurations.defaultRegionSize();
    private static final long VALUE = 0x0123456789abcdefL;

    @TempDir
    Path tempDir;

    /** Single fixed-size file, fully synchronous mapping */
    private static MappingConfig smallSyncConfig() {
        return MappingConfig.configure()
                .rollFiles(false)
                .maxFileSize(4L * REGION_SIZE)
                .mappingStrategy(MappingStrategyConfig.getDefaultSync());
    }

    @Test
    void fixedMappingOfWholeFileAndFromOffset() throws IOException {
        //NOTE: offset must currently be page aligned, mapping fails with an IOException otherwise
        final int offset = (int) REGION_SIZE_GRANULARITY;
        final byte[] bytes = new byte[2 * offset];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        final File file = Files.write(tempDir.resolve("fixed.dat"), bytes).toFile();
        try (final FixedMapping mapping = Mappings.fixedMapping(file, AccessMode.READ_ONLY)) {
            assertEquals(bytes.length, mapping.bytesAvailable());
            assertEquals(0, mapping.position());
            assertEquals(bytes[bytes.length - 1], mapping.buffer().getByte(bytes.length - 1));
        }
        try (final FixedMapping mapping = Mappings.fixedMapping(file, AccessMode.READ_ONLY, offset)) {
            assertEquals(bytes.length - offset, mapping.bytesAvailable());
            assertEquals(offset, mapping.position());
            assertEquals(bytes[offset], mapping.buffer().getByte(0));
            assertEquals(bytes[bytes.length - 1], mapping.buffer().getByte(bytes.length - offset - 1));
        }
    }

    @Test
    void fixedMappingFromFileMapperClosesFileMapper() {
        final File file = tempDir.resolve("fixed.map").toFile();
        final FileMapper fileMapper = new FixedSizeFileMapper(file, 64, AccessMode.READ_WRITE,
                FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0, 64));
        try (final FixedMapping mapping = Mappings.fixedMapping(fileMapper, 0, 64)) {
            assertEquals(64, mapping.bytesAvailable());
            mapping.buffer().putLong(56, VALUE);
            assertEquals(VALUE, mapping.buffer().getLong(56));
        }
        assertTrue(fileMapper.isClosed());
    }

    @Test
    void regionMappingWithDefaultConfig() {
        final File file = tempDir.resolve("region-default.map").toFile();
        try (final RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_WRITE)) {
            assertTrue(mapping.moveToRegion(1));
            mapping.buffer().putLong(8, VALUE);
        }
        try (final RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_ONLY)) {
            assertTrue(mapping.moveToRegion(1));
            assertEquals(VALUE, mapping.buffer().getLong(8));
        }
    }

    @Test
    void regionMappingWithConfig() {
        final File file = tempDir.resolve("region.map").toFile();
        try (final RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_WRITE, smallSyncConfig())) {
            assertTrue(mapping.moveToRegion(3));
            mapping.buffer().putLong(REGION_SIZE - Long.BYTES, VALUE);
        }
        try (final RegionMapping mapping = Mappings.regionMapping(file, AccessMode.READ_ONLY, smallSyncConfig())) {
            assertTrue(mapping.moveToRegion(3));
            assertEquals(VALUE, mapping.buffer().getLong(REGION_SIZE - Long.BYTES));
        }
    }

    @Test
    void elasticMappingWithDefaultConfig() {
        final File file = tempDir.resolve("elastic-default.map").toFile();
        final long position = REGION_SIZE + 100L;
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, AccessMode.READ_WRITE)) {
            assertTrue(mapping.moveTo(position));
            mapping.buffer().putLong(0, VALUE);
        }
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, AccessMode.READ_ONLY)) {
            assertTrue(mapping.moveTo(position));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }

    @Test
    void elasticMappingWithConfig() {
        final File file = tempDir.resolve("elastic.map").toFile();
        final long position = 2L * REGION_SIZE + 100;
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, AccessMode.READ_WRITE, smallSyncConfig())) {
            assertTrue(mapping.moveTo(position));
            assertEquals(REGION_SIZE - 100, mapping.bytesAvailable());
            mapping.buffer().putLong(0, VALUE);
        }
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, AccessMode.READ_ONLY, smallSyncConfig())) {
            assertTrue(mapping.moveTo(position));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }

    @Test
    void mappingPoolWithDefaultConfig() {
        final File file = tempDir.resolve("pool-default.map").toFile();
        try (final MappingPool pool = Mappings.mappingPool(file, AccessMode.READ_WRITE)) {
            final ElasticMapping mapping = pool.acquireElasticMapping();
            assertTrue(mapping.moveTo(REGION_SIZE + 8L));
            mapping.buffer().putLong(0, VALUE);
        }
        try (final MappingPool pool = Mappings.mappingPool(file, AccessMode.READ_ONLY)) {
            final RegionMapping mapping = pool.acquireRegionMapping();
            assertTrue(mapping.moveToRegion(1));
            assertEquals(VALUE, mapping.buffer().getLong(8));
        }
    }

    @Test
    void mappingPoolWithConfigAndPoolSize() {
        final File file = tempDir.resolve("pool.map").toFile();
        try (final MappingPool pool = Mappings.mappingPool(file, AccessMode.READ_WRITE, smallSyncConfig())) {
            final AdaptiveMapping mapping = pool.acquireAdaptiveMapping();
            assertTrue(mapping.moveTo(3L * REGION_SIZE + 16, Long.BYTES));
            mapping.buffer().putLong(0, VALUE);
        }
        try (final MappingPool pool = Mappings.mappingPool(file, AccessMode.READ_ONLY, smallSyncConfig(), 2)) {
            final ElasticMapping mapping = pool.acquireElasticMapping();
            assertTrue(mapping.moveTo(3L * REGION_SIZE + 16));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }
}
