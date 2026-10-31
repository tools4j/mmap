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
package org.tools4j.mmap.queue.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.api.OpenMode;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;

/**
 * Unit test for {@link LazyElasticMapping}.
 */
class LazyElasticMappingTest {

    @TempDir
    Path tempDir;

    /** Delegate returned by the factory once set, null until then */
    private final AtomicReference<ElasticMapping> available = new AtomicReference<>();
    private final AtomicInteger factoryCalls = new AtomicInteger();

    private LazyElasticMapping lazyMapping() {
        return new LazyElasticMapping(() -> {
            factoryCalls.incrementAndGet();
            return available.get();
        });
    }

    private ElasticMapping delegate() {
        return Mappings.elasticMapping(tempDir.resolve("delegate.map").toFile(), OpenMode.OPEN_OR_CREATE);
    }

    @Test
    void beforeDelegateExistsMappingIsUnmapped() {
        try (final LazyElasticMapping mapping = lazyMapping()) {
            assertEquals(AccessMode.READ_ONLY, mapping.accessMode());
            assertEquals(1, mapping.positionStepSize());
            assertFalse(mapping.isMapped());
            assertEquals(NULL_POSITION, mapping.position());
            assertEquals(NULL_POSITION, mapping.regionStartPosition());
            assertEquals(0, mapping.regionOffset());
            assertEquals(NULL_ADDRESS, mapping.address());
            assertEquals(0, mapping.buffer().capacity());
            assertThrows(IllegalStateException.class, mapping::regionMetrics);
            assertThrows(IllegalStateException.class, () -> mapping.moveBy(8));
            assertThrows(IllegalArgumentException.class, () -> mapping.moveTo(-1));
            assertEquals(0, factoryCalls.get());

            assertFalse(mapping.moveTo(0));
            assertFalse(mapping.moveTo(8));
            assertEquals(2, factoryCalls.get());
            assertFalse(mapping.isMapped());
        }
    }

    @Test
    void delegateIsCreatedOnceAndUsedThereafter() {
        try (final LazyElasticMapping mapping = lazyMapping();
             final ElasticMapping delegate = delegate()) {
            assertFalse(mapping.moveTo(16));
            available.set(delegate);

            assertTrue(mapping.moveTo(16));
            assertTrue(mapping.isMapped());
            assertEquals(16, mapping.position());
            assertEquals(16, mapping.regionOffset());
            assertEquals(0, mapping.regionStartPosition());
            assertEquals(delegate.address(), mapping.address());
            assertSame(delegate.buffer(), mapping.buffer());
            assertSame(delegate.regionMetrics(), mapping.regionMetrics());
            assertEquals(AccessMode.READ_ONLY, mapping.accessMode());

            assertTrue(mapping.moveBy(8));
            assertEquals(24, mapping.position());
            assertEquals(24, delegate.position());
            assertEquals(2, factoryCalls.get());
        }
    }

    @Test
    void closingBeforeDelegateExistsPreventsCreatingIt() {
        final LazyElasticMapping mapping = lazyMapping();
        available.set(delegate());
        mapping.close();
        assertTrue(mapping.isClosed());
        assertFalse(mapping.moveTo(0));
        assertEquals(0, factoryCalls.get());
        mapping.close();
        assertTrue(mapping.isClosed());
        available.get().close();
    }

    @Test
    void closingAfterDelegateExistsClosesDelegate() {
        final LazyElasticMapping mapping = lazyMapping();
        final ElasticMapping delegate = delegate();
        available.set(delegate);
        assertTrue(mapping.moveTo(0));
        mapping.close();
        assertTrue(mapping.isClosed());
        assertTrue(delegate.isClosed());
        mapping.close();
    }
}
