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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the {@link Mappings} factory methods, writing through one mapping and reading back through another.
 */
class MappingsTest {

    private static final int REGION_SIZE = MappingConfigurations.defaultRegionSize();
    private static final int OVERLAP_SIZE = MappingConfig.getDefault().overlapSize();
    private static final int MAPPING_SIZE = REGION_SIZE + OVERLAP_SIZE;
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
        final byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        final File file = Files.write(tempDir.resolve("fixed.dat"), bytes).toFile();
        try (final Mapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY)) {
            assertEquals(16, mapping.bytesAvailable());
            assertEquals(0, mapping.position());
            assertEquals(15, mapping.buffer().getByte(15));
        }
        try (final Mapping mapping = Mappings.fixedMapping(file, OpenMode.READ_ONLY, 4)) {
            assertEquals(12, mapping.bytesAvailable());
            assertEquals(4, mapping.position());
            assertEquals(4, mapping.buffer().getByte(0));
            assertEquals(15, mapping.buffer().getByte(11));
        }
    }

    @Test
    void dynamicMappingWithDefaultConfig() {
        final File file = tempDir.resolve("dynamic-default.map").toFile();
        final long position = REGION_SIZE + 100L;
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE)) {
            assertTrue(mapping.moveTo(position));
            assertEquals(MAPPING_SIZE - 100, mapping.length());
            mapping.buffer().putLong(0, VALUE);
        }
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY)) {
            assertTrue(mapping.moveTo(position));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }

    /**
     * Rolling files with async map-ahead (the async mapping thread maps via its own per-thread file mapper of the concurrent rolling
     * file mapper) or with async unmapping only (single-threaded rolling file mapper, unmapped from another thread),
     * writing more files than can be open at a time.
     */
    @ParameterizedTest(name = "asyncMapAhead={0}")
    @ValueSource(booleans = {true, false})
    void dynamicMappingWithRollingFilesAndAsyncStrategy(final boolean asyncMapAhead) {
        final int files = 6;
        final int regionsPerFile = 2;
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(true)
                .maxFileSize((long) regionsPerFile * REGION_SIZE)
                .maxOpenFiles(asyncMapAhead ? 2 : 1)
                .filesToCreateAhead(asyncMapAhead ? 1 : 0)
                .mappingStrategy(asyncMapAhead
                        ? MappingStrategyConfig.getDefaultAsyncMapAhead()
                        : MappingStrategyConfig.getDefaultSyncWithAsyncUnmapping());
        final File file = tempDir.resolve("rolling.map").toFile();
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                config)) {
            for (int region = 0; region < files * regionsPerFile; region++) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE), "region " + region);
                mapping.buffer().putLong(0, VALUE + region);
            }
        }
        for (int fileIndex = 0; fileIndex < files; fileIndex++) {
            assertTrue(tempDir.resolve("rolling_" + fileIndex + ".map").toFile().isFile(), "file " + fileIndex);
        }
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
            for (int region = files * regionsPerFile - 1; region >= 0; region--) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE), "region " + region);
                assertEquals(VALUE + region, mapping.buffer().getLong(0), "region " + region);
            }
        }
    }

    @Test
    void dynamicMappingWithConfig() {
        final File file = tempDir.resolve("dynamic.map").toFile();
        final long position = 2L * REGION_SIZE + 100;
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                smallSyncConfig())) {
            assertTrue(mapping.moveTo(position));
            assertEquals(MAPPING_SIZE - 100, mapping.length());
            mapping.buffer().putLong(0, VALUE);
            assertTrue(mapping.moveTo(3L * REGION_SIZE, DynamicMapping.STRETCH_LENGTH));
            assertEquals(MAPPING_SIZE, mapping.length(), "last region of file with overlap");
            mapping.buffer().putLong(REGION_SIZE - Long.BYTES, VALUE);
        }
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, smallSyncConfig())) {
            assertTrue(mapping.moveTo(position));
            assertEquals(VALUE, mapping.buffer().getLong(0));
            assertTrue(mapping.moveTo(3L * REGION_SIZE, DynamicMapping.STRETCH_LENGTH));
            assertEquals(VALUE, mapping.buffer().getLong(REGION_SIZE - Long.BYTES));
        }
    }

    @Test
    void overlapMapsStartOfNextRegion() {
        final File file = tempDir.resolve("overlap.map").toFile();
        final long position = REGION_SIZE - Integer.BYTES;
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                smallSyncConfig())) {
            assertTrue(mapping.moveTo(position, Long.BYTES), "crossing the region end");
            mapping.buffer().putLong(0, VALUE);
        }
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, smallSyncConfig())) {
            assertTrue(mapping.moveTo(position, Long.BYTES));
            assertEquals(VALUE, mapping.buffer().getLong(0));
            assertTrue(mapping.moveTo(REGION_SIZE, Integer.BYTES), "next region");
            assertEquals((int) (VALUE >>> Integer.SIZE), mapping.buffer().getInt(0));
        }
    }

    @ParameterizedTest(name = "expandFile={0}")
    @ValueSource(booleans = {false, true})
    void filesAreLargerThanMaxFileSizeByOverlap(final boolean expandFile) {
        final File file = tempDir.resolve("max-size.map").toFile();
        final MappingConfig config = MappingConfig.configure(smallSyncConfig()).expandFile(expandFile);
        final long lastPosition = 4L * REGION_SIZE - Integer.BYTES;
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                config)) {
            assertTrue(mapping.moveTo(3L * REGION_SIZE, DynamicMapping.STRETCH_LENGTH));
            assertEquals(MAPPING_SIZE, mapping.length(), "last region of file with overlap");
            assertTrue(mapping.moveTo(lastPosition, mapping.safeLength(Integer.BYTES)), "crossing max file size");
            mapping.buffer().putLong(0, VALUE);
        }
        assertEquals(4L * REGION_SIZE + OVERLAP_SIZE, file.length());
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
            assertTrue(mapping.moveTo(lastPosition, Long.BYTES));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }

    @Test
    void fileIsReadWithSmallerOverlap() {
        final File file = tempDir.resolve("smaller-overlap.map").toFile();
        final MappingConfig withOverlap = MappingConfig.configure(smallSyncConfig())
                .expandFile(false).maxFileSize(2L * REGION_SIZE).overlapSize(REGION_SIZE);
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                withOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE - Integer.BYTES, Long.BYTES));
            mapping.buffer().putLong(0, VALUE);
        }
        assertEquals(3L * REGION_SIZE, file.length());
        final MappingConfig noOverlap = MappingConfig.configure(withOverlap).overlapSize(0);
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, noOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE - Integer.BYTES, DynamicMapping.STRETCH_LENGTH));
            assertEquals(Integer.BYTES, mapping.length(), "no overlap");
            assertEquals((int) VALUE, mapping.buffer().getInt(0));
            assertTrue(mapping.moveTo(REGION_SIZE, DynamicMapping.STRETCH_LENGTH), "last region");
            assertEquals(REGION_SIZE, mapping.length());
            assertEquals((int) (VALUE >>> Integer.SIZE), mapping.buffer().getInt(0));
        }
    }

    @Test
    void lastRegionIsNotAvailableToReaderWithLargerOverlap() {
        final File file = tempDir.resolve("larger-overlap.map").toFile();
        final MappingConfig noOverlap = MappingConfig.configure(smallSyncConfig())
                .expandFile(false).maxFileSize(2L * REGION_SIZE).overlapSize(0);
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                noOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE - Integer.BYTES));
            mapping.buffer().putInt(0, (int) VALUE);
            assertTrue(mapping.moveTo(REGION_SIZE));
            mapping.buffer().putInt(0, (int) (VALUE >>> Integer.SIZE));
        }
        assertEquals(2L * REGION_SIZE, file.length());
        final MappingConfig withOverlap = MappingConfig.configure(noOverlap).overlapSize(REGION_SIZE);
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, withOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE - Integer.BYTES, Long.BYTES), "first region covered by the file");
            assertEquals(VALUE, mapping.buffer().getLong(0));
            assertFalse(mapping.moveTo(REGION_SIZE), "last region with overlap beyond the file");
        }
    }

    @ParameterizedTest(name = "expandFile={0}")
    @ValueSource(booleans = {false, true})
    void writerWithLargerOverlapGrowsFile(final boolean expandFile) {
        final File file = tempDir.resolve("grow-overlap.map").toFile();
        final MappingConfig noOverlap = MappingConfig.configure(smallSyncConfig())
                .expandFile(expandFile).maxFileSize(2L * REGION_SIZE).overlapSize(0);
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                noOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE));
        }
        assertEquals(2L * REGION_SIZE, file.length());
        final MappingConfig withOverlap = MappingConfig.configure(noOverlap).overlapSize(REGION_SIZE);
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                withOverlap)) {
            assertTrue(mapping.moveTo(REGION_SIZE));
            assertEquals(2 * REGION_SIZE, mapping.length());
        }
        assertEquals(3L * REGION_SIZE, file.length());
    }

    /**
     * The overlap of the last region of every rolling file is the tail of that file, not continued in the next file,
     * with the single-threaded and the concurrent rolling file mapper.
     */
    @ParameterizedTest(name = "asyncMapAhead={0}")
    @ValueSource(booleans = {true, false})
    void overlapOfLastRegionIsTailOfRollingFile(final boolean asyncMapAhead) {
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(true)
                .maxFileSize(2L * REGION_SIZE)
                .maxOpenFiles(2)
                .mappingStrategy(asyncMapAhead
                        ? MappingStrategyConfig.getDefaultAsyncMapAhead()
                        : MappingStrategyConfig.getDefaultSync());
        final File file = tempDir.resolve("rolling-overlap.map").toFile();
        try (final DynamicMutableMapping mapping = Mappings.dynamicMutableMapping(file, OpenMode.OPEN_OR_CREATE,
                config)) {
            for (int fileIndex = 0; fileIndex < 3; fileIndex++) {
                final long fileEnd = (fileIndex + 1) * 2L * REGION_SIZE;
                assertTrue(mapping.moveTo(fileEnd - Integer.BYTES, Long.BYTES), "file end " + fileIndex);
                mapping.buffer().putLong(0, VALUE + fileIndex);
                assertTrue(mapping.moveTo(fileEnd - REGION_SIZE, DynamicMapping.STRETCH_LENGTH));
                assertEquals(MAPPING_SIZE, mapping.length(), "last region of file " + fileIndex);
            }
        }
        for (int fileIndex = 0; fileIndex < 3; fileIndex++) {
            final File rollingFile = tempDir.resolve("rolling-overlap_" + fileIndex + ".map").toFile();
            assertEquals(2L * REGION_SIZE + OVERLAP_SIZE, rollingFile.length(), "file " + fileIndex);
        }
        try (final DynamicMapping mapping = Mappings.dynamicMapping(file, OpenMode.READ_ONLY, config)) {
            for (int fileIndex = 2; fileIndex >= 0; fileIndex--) {
                final long fileEnd = (fileIndex + 1) * 2L * REGION_SIZE;
                assertTrue(mapping.moveTo(fileEnd - Integer.BYTES, Long.BYTES));
                assertEquals(VALUE + fileIndex, mapping.buffer().getLong(0), "file " + fileIndex);
                if (fileIndex < 2) {
                    assertTrue(mapping.moveTo(fileEnd, Integer.BYTES));
                    assertEquals(0, mapping.buffer().getInt(0), "next file not continuing the tail of file " +
                            fileIndex);
                }
            }
        }
    }

    @Test
    void mappingPoolWithDefaultConfig() {
        final File file = tempDir.resolve("pool-default.map").toFile();
        try (final MutableMappingPool pool = Mappings.mutableMappingPool(file, OpenMode.OPEN_OR_CREATE)) {
            final DynamicMutableMapping mapping = pool.acquireMapping();
            assertTrue(mapping.moveTo(REGION_SIZE + 8L));
            mapping.buffer().putLong(0, VALUE);
        }
        try (final MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY)) {
            final DynamicMapping mapping = pool.acquireMapping();
            assertTrue(mapping.moveTo(REGION_SIZE));
            assertEquals(VALUE, mapping.buffer().getLong(8));
        }
    }

    @Test
    void mappingPoolWithConfigAndPoolSize() {
        final File file = tempDir.resolve("pool.map").toFile();
        try (final MutableMappingPool pool = Mappings.mutableMappingPool(file, OpenMode.OPEN_OR_CREATE,
                smallSyncConfig())) {
            final DynamicMutableMapping mapping = pool.acquireMapping();
            assertTrue(mapping.moveTo(3L * REGION_SIZE + 16, Long.BYTES));
            mapping.buffer().putLong(0, VALUE);
        }
        try (final MappingPool pool = Mappings.mappingPool(file, OpenMode.READ_ONLY, smallSyncConfig(), 2)) {
            final DynamicMapping mapping = pool.acquireMapping();
            assertTrue(mapping.moveTo(3L * REGION_SIZE + 16));
            assertEquals(VALUE, mapping.buffer().getLong(0));
        }
    }
}
