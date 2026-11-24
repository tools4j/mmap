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
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the default methods of {@link DynamicMapping}, exercised through a delegate to the mapping
 * implementation that implements only the abstract methods.
 */
class DynamicMappingDefaultMethodsTest {

    private static final int REGION_SIZE = MappingConfigurations.defaultRegionSize();
    private static final int REGIONS = 8;

    private DynamicMapping implementation;
    private DynamicMapping mapping;
    private int overlapSize;
    private int mappingSize;

    @BeforeEach
    void open(@TempDir final Path tempDir) {
        final MappingConfig config = MappingConfig.configure()
                .rollFiles(false)
                .maxFileSize((long) REGIONS * REGION_SIZE)
                .mappingStrategy(MappingStrategyConfig.getDefaultSync());
        implementation = Mappings.dynamicMutableMapping(tempDir.resolve("dynamic.map").toFile(),
                OpenMode.OPEN_OR_CREATE, config);
        mapping = new AbstractMethodsOnlyDynamicMapping(implementation);
        overlapSize = implementation.regionMetrics().overlapSize();
        mappingSize = implementation.regionMetrics().mappingSize();
    }

    @AfterEach
    void close() {
        implementation.close();
    }

    @Test
    void maxLength() {
        assertFalse(mapping.isMapped());
        assertEquals(0, mapping.maxLengthAtCurrentPosition(), "unmapped");
        assertEquals(0, mapping.maxLengthAtPosition(-1));
        assertEquals(mappingSize, mapping.maxLengthAtPosition(REGION_SIZE));
        assertEquals(mappingSize - 100, mapping.maxLengthAtPosition(REGION_SIZE + 100L));
        assertEquals(overlapSize + 1, mapping.maxLengthAtPosition(REGION_SIZE - 1L));
        assertTrue(mapping.moveTo(100, 10));
        assertEquals(mappingSize - 100, mapping.maxLengthAtCurrentPosition());
        assertEquals(10, mapping.length());
    }

    @Test
    void safeLength() {
        assertTrue(overlapSize > 0, "default overlap");
        assertEquals(overlapSize + 1, mapping.safeLength(1));
        assertEquals(overlapSize + Long.BYTES, mapping.safeLength(Long.BYTES));
        assertEquals(mappingSize, mapping.safeLength(REGION_SIZE));
        assertTrue(mapping.moveTo(REGION_SIZE - Long.BYTES, mapping.safeLength(Long.BYTES)), "fits at last aligned");
        assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(REGION_SIZE - 1L,
                mapping.safeLength(Long.BYTES)), "does not fit at unaligned position");
        assertThrows(IllegalArgumentException.class, () -> mapping.safeLength(0));
        assertThrows(IllegalArgumentException.class, () -> mapping.safeLength(3));
        assertThrows(IllegalArgumentException.class, () -> mapping.safeLength(2 * REGION_SIZE));
    }

    @Test
    void moveByMapsRequestedLength() {
        assertThrows(IllegalStateException.class, () -> mapping.moveBy(10));
        assertThrows(IllegalStateException.class, () -> mapping.moveBy(10, 5));

        assertTrue(mapping.moveTo(100, 10));
        assertTrue(mapping.moveBy(100));
        assertEquals(200, mapping.position());
        assertEquals(10, mapping.length());
        assertTrue(mapping.moveBy(5, 20));
        assertEquals(205, mapping.position());
        assertEquals(20, mapping.length());
        assertTrue(mapping.moveBy(REGION_SIZE));
        assertEquals(20, mapping.length(), "kept in another region");
        assertTrue(mapping.moveBy(10, DynamicMapping.STRETCH_LENGTH));
        assertEquals(mappingSize - 215, mapping.length());

        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(-4L * REGION_SIZE));
        assertThrows(IllegalArgumentException.class, () -> mapping.moveBy(-4L * REGION_SIZE, 5));
        assertEquals(REGION_SIZE + 215L, mapping.position());
    }

    /** Delegates only the abstract methods, so that all default methods of {@link DynamicMapping} are used */
    private record AbstractMethodsOnlyDynamicMapping(DynamicMapping delegate) implements DynamicMapping {
        @Override
        public boolean moveTo(final long position) {
            return delegate.moveTo(position);
        }

        @Override
        public boolean moveTo(final long position, final int length) {
            return delegate.moveTo(position, length);
        }

        @Override
        public boolean moveToExisting(final long position) {
            return delegate.moveToExisting(position);
        }

        @Override
        public boolean moveToExisting(final long position, final int length) {
            return delegate.moveToExisting(position, length);
        }

        @Override
        public void length(final int length) {
            delegate.length(length);
        }

        @Override
        public void limit(final long limit) {
            delegate.limit(limit);
        }

        @Override
        public RegionMetrics regionMetrics() {
            return delegate.regionMetrics();
        }

        @Override
        public AccessMode accessMode() {
            return delegate.accessMode();
        }

        @Override
        public long position() {
            return delegate.position();
        }

        @Override
        public long address() {
            return delegate.address();
        }

        @Override
        public MemoryBuffer buffer() {
            return delegate.buffer();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
