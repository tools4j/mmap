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
package org.tools4j.mmap.mapping.unsafe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link FileMappers}.
 */
class FileMappersTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final long MAX_FILE_SIZE = 2L * REGION_SIZE;
    private static final FileInitialiser INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0);

    @TempDir
    Path tempDir;

    private static MappingConfig rollingConfig(final MappingStrategyConfig strategy, final int maxOpenFiles) {
        return MappingConfig.configure()
                .rollFiles(true)
                .maxFileSize(MAX_FILE_SIZE)
                .maxOpenFiles(maxOpenFiles)
                .mappingStrategy(MappingStrategyConfig.configure(strategy).regionSize(REGION_SIZE));
    }

    @Test
    void createUsesConcurrentRollingFileMapperOnlyForAsyncMapping() {
        final File file = tempDir.resolve("create.map").toFile();
        try (final FileMapper mapper = FileMappers.create(file, AccessMode.READ_WRITE, INITIALISER,
                rollingConfig(MappingStrategyConfig.getDefaultAsyncMapAhead(), 2))) {
            assertInstanceOf(ConcurrentRollingFileMapper.PerThreadFileMapper.class, mapper);
        }
        try (final FileMapper mapper = FileMappers.create(file, AccessMode.READ_ONLY, INITIALISER,
                rollingConfig(MappingStrategyConfig.getDefaultAsyncMapAhead(), 2))) {
            assertInstanceOf(ConcurrentRollingFileMapper.PerThreadFileMapper.class, mapper);
        }
        //async unmapping only: unmapping is thread-safe, and a single open file suffices
        try (final FileMapper mapper = FileMappers.create(file, AccessMode.READ_WRITE, INITIALISER,
                rollingConfig(MappingStrategyConfig.getDefaultSyncWithAsyncUnmapping(), 1))) {
            assertInstanceOf(RollingFileMapper.class, mapper);
        }
        try (final FileMapper mapper = FileMappers.create(file, AccessMode.READ_WRITE, INITIALISER,
                rollingConfig(MappingStrategyConfig.getDefaultSync(), 1))) {
            assertInstanceOf(RollingFileMapper.class, mapper);
        }
    }

    @Test
    void createRejectsSingleOpenFileForAsyncMapping() {
        assertThrows(IllegalArgumentException.class, () -> FileMappers.create(tempDir.resolve("one.map").toFile(),
                AccessMode.READ_WRITE, INITIALISER, rollingConfig(MappingStrategyConfig.getDefaultAsyncMapAhead(), 1)));
    }

    @Test
    void forThreadReturnsThreadSafeFileMapperItself() {
        final FileMapper fileMapper = mock(FileMapper.class);
        assertSame(fileMapper, FileMappers.forThread(fileMapper));
    }

    @Test
    void forThreadReturnsNewPerThreadFileMapperForConcurrentRollingFileMapper() {
        try (final FileMapper concurrent = ConcurrentRollingFileMapper.forReadWrite(
                tempDir.resolve("concurrent.map").toFile(), AccessMode.READ_WRITE, false, 0, MAX_FILE_SIZE,
                REGION_SIZE, 0, 3, INITIALISER)) {
            final FileMapper sibling = FileMappers.forThread(concurrent);
            assertNotSame(concurrent, sibling);
            assertNotSame(sibling, FileMappers.forThread(sibling));
        }
    }

    @Test
    void forThreadRejectsSingleThreadedFileMapper() {
        try (final FileMapper rolling = RollingFileMapper.forReadWrite(tempDir.resolve("rolling.map").toFile(),
                AccessMode.READ_WRITE, false, 0, MAX_FILE_SIZE, REGION_SIZE, 0, 2, INITIALISER)) {
            assertThrows(IllegalArgumentException.class, () -> FileMappers.forThread(rolling));
        }
    }

    @Test
    void forThreadRejectsNull() {
        assertThrows(NullPointerException.class, () -> FileMappers.forThread(null));
    }
}
