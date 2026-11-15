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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.mapping.api.AdaptiveMapping;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.FixedMapping;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;
import org.tools4j.mmap.mapping.api.RegionMapping;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the buffers of read-only {@link FixedMappingImpl}, {@link RegionMappingImpl},
 * {@link ElasticMappingImpl} and {@link AdaptiveMappingImpl}, created through {@link Mappings}.
 */
class ReadOnlyMappingsTest {

    @TempDir
    Path tempDir;
    /** Written through an elastic mapping, so with the default configuration rolling files */
    private File file;

    @BeforeEach
    void writeFile() {
        file = tempDir.resolve("read-only.dat").toFile();
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, OpenMode.CREATE)) {
            assertTrue(mapping.moveTo(0));
            assertFalse(mapping.buffer().isReadOnly());
            mapping.buffer().putLong(0, 42);
            mapping.buffer().putLong(8, 43);
        }
    }

    @Test
    void fixedMappingBufferIsReadOnly() throws IOException {
        final byte[] bytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(42).putLong(43).array();
        final File fixedFile = Files.write(tempDir.resolve("fixed.dat"), bytes).toFile();
        try (final FixedMapping mapping = Mappings.fixedMapping(fixedFile, OpenMode.READ_ONLY, 0, 16)) {
            assertReadOnly(mapping, 42);
        }
    }

    @Test
    void regionMappingBufferIsReadOnly() {
        try (final RegionMapping mapping = Mappings.regionMapping(file, OpenMode.READ_ONLY)) {
            assertReadOnlyAfterMove(mapping, 0, 42);
        }
    }

    @Test
    void elasticMappingBufferIsReadOnly() {
        try (final ElasticMapping mapping = Mappings.elasticMapping(file, OpenMode.READ_ONLY)) {
            assertReadOnlyAfterMove(mapping, 0, 42);
            assertReadOnlyAfterMove(mapping, 8, 43);
        }
    }

    @Test
    void adaptiveMappingBufferIsReadOnly() {
        try (final AdaptiveMapping mapping = Mappings.adaptiveMapping(file, OpenMode.READ_ONLY)) {
            assertReadOnlyAfterMove(mapping, 0, 42);
            assertReadOnlyAfterMove(mapping, 8, 43);
            mapping.length(8);
            assertReadOnly(mapping, 43);
        }
    }

    private static void assertReadOnlyAfterMove(final DynamicMapping mapping, final long position, final long value) {
        assertTrue(mapping.moveTo(position));
        assertReadOnly(mapping, value);
    }

    private static void assertReadOnly(final Mapping mapping, final long value) {
        final MutableMemoryBuffer buffer = mapping.buffer();
        assertTrue(buffer.isReadOnly());
        assertEquals(value, buffer.getLong(0));
        assertThrows(ReadOnlyBufferException.class, () -> buffer.putLong(0, 0));
        assertEquals(value, buffer.getLong(0));
    }
}
