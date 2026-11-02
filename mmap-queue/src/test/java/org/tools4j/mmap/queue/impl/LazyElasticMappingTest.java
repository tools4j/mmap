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
import org.tools4j.mmap.queue.config.QueueConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Queue configuration in effect, null until the queue exists */
    private final AtomicReference<QueueConfig> queueConfig = new AtomicReference<>();
    /** Delegate returned by the factory */
    private ElasticMapping delegateToCreate;
    private final List<QueueConfig> createdWith = new ArrayList<>();
    /** True once the file to map exists */
    private final AtomicBoolean fileExists = new AtomicBoolean();
    private final List<QueueConfig> checkedWith = new ArrayList<>();

    private LazyElasticMapping lazyMapping() {
        return new LazyElasticMapping(queueConfig::get, cfg -> {
            createdWith.add(cfg);
            return delegateToCreate;
        }, cfg -> {
            checkedWith.add(cfg);
            return fileExists.get();
        });
    }

    private void queueExists(final ElasticMapping delegate) {
        delegateToCreate = delegate;
        queueConfig.set(QueueConfig.configure().toImmutableQueueConfig());
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

            assertFalse(mapping.moveTo(0));
            assertFalse(mapping.moveTo(8));
            assertEquals(List.of(), createdWith);
            assertEquals(List.of(), checkedWith);
            assertFalse(mapping.isOpened());
            assertFalse(mapping.isMapped());
        }
    }

    @Test
    void opensOnceFileExists() {
        try (final LazyElasticMapping mapping = lazyMapping();
             final ElasticMapping delegate = delegate()) {
            //queue exists, header file not yet
            queueExists(new FailingMapping(delegate));
            final QueueConfig cfg = queueConfig.get();
            assertFalse(mapping.moveTo(0));
            assertFalse(mapping.isOpened());
            queueConfig.set(QueueConfig.configure().toImmutableQueueConfig());//not used once delegate exists

            //file exists, but moving fails, e.g. beyond its end
            fileExists.set(true);
            assertFalse(mapping.moveTo(0));
            assertTrue(mapping.isOpened());
            fileExists.set(false);//not checked once opened
            assertFalse(mapping.moveTo(0));
            assertTrue(mapping.isOpened());
            assertEquals(List.of(cfg), createdWith);
            assertEquals(2, checkedWith.size());
            assertSame(cfg, checkedWith.get(0));
            assertSame(cfg, checkedWith.get(1));
        }
    }

    @Test
    void opensWithSuccessfulMove() {
        try (final LazyElasticMapping mapping = lazyMapping();
             final ElasticMapping delegate = delegate()) {
            queueExists(delegate);
            assertTrue(mapping.moveTo(0));
            assertTrue(mapping.isOpened());
            assertTrue(mapping.moveTo(8));
            assertTrue(mapping.isOpened());
        }
    }

    /** Delegate failing every move, as a mapping of a file that does not exist yet or is too short */
    private record FailingMapping(ElasticMapping delegate) implements ElasticMapping {
        @Override
        public boolean moveTo(final long position) {
            return false;
        }

        @Override
        public AccessMode accessMode() {
            return delegate.accessMode();
        }

        @Override
        public org.tools4j.mmap.mapping.api.RegionMetrics regionMetrics() {
            return delegate.regionMetrics();
        }

        @Override
        public int regionOffset() {
            return delegate.regionOffset();
        }

        @Override
        public long regionStartPosition() {
            return delegate.regionStartPosition();
        }

        @Override
        public int positionStepSize() {
            return delegate.positionStepSize();
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
        public org.agrona.concurrent.AtomicBuffer buffer() {
            return delegate.buffer();
        }

        @Override
        public boolean isMapped() {
            return delegate.isMapped();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            //owned by the test
        }
    }

    @Test
    void delegateIsCreatedOnceAndUsedThereafter() {
        try (final LazyElasticMapping mapping = lazyMapping();
             final ElasticMapping delegate = delegate()) {
            assertFalse(mapping.moveTo(16));
            queueExists(delegate);

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
            assertEquals(1, createdWith.size());
        }
    }

    @Test
    void closingBeforeDelegateExistsPreventsCreatingIt() {
        final LazyElasticMapping mapping = lazyMapping();
        final ElasticMapping delegate = delegate();
        queueExists(delegate);
        mapping.close();
        assertTrue(mapping.isClosed());
        assertFalse(mapping.moveTo(0));
        assertEquals(List.of(), createdWith);
        mapping.close();
        assertTrue(mapping.isClosed());
        delegate.close();
    }

    @Test
    void closingAfterDelegateExistsClosesDelegate() {
        final LazyElasticMapping mapping = lazyMapping();
        final ElasticMapping delegate = delegate();
        queueExists(delegate);
        assertTrue(mapping.moveTo(0));
        mapping.close();
        assertTrue(mapping.isClosed());
        assertTrue(delegate.isClosed());
        mapping.close();
    }
}
