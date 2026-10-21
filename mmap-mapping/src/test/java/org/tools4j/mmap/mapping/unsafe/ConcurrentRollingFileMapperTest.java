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

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link ConcurrentRollingFileMapper}: unmapping releases the OS mapping, also for regions of files that
 * were closed (evicted) in the meantime.
 */
class ConcurrentRollingFileMapperTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int REGIONS_PER_FILE = 2;
    private static final long MAX_FILE_SIZE = (long) REGIONS_PER_FILE * REGION_SIZE;
    private static final FileInitialiser READ_WRITE_INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0);

    @TempDir
    Path tempDir;

    private File baseFile(final String name) {
        return tempDir.resolve(name).toFile();
    }

    private static ConcurrentRollingFileMapper readWrite(final File baseFile, final int maxOpenFiles) {
        return (ConcurrentRollingFileMapper) ConcurrentRollingFileMapper.forReadWrite(baseFile, AccessMode.READ_WRITE,
                false, 0, MAX_FILE_SIZE, REGION_SIZE, 0, maxOpenFiles, READ_WRITE_INITIALISER);
    }

    private static long position(final int fileIndex) {
        return fileIndex * MAX_FILE_SIZE;
    }

    @Test
    void unmapReleasesMappingOfOpenFile() {
        try (final ConcurrentRollingFileMapper mapper = readWrite(baseFile("open.map"), 2)) {
            final long address = mapper.map(position(0), REGION_SIZE);
            final Path file0 = OsMappings.realPath(baseFile("open_0.map"));
            assertTrue(OsMappings.isMapped(file0));
            mapper.unmap(position(0), address, REGION_SIZE);
            assertFalse(OsMappings.isMapped(file0));
        }
    }

    @Test
    void unmapReleasesMappingOfAlreadyClosedFile() {
        try (final ConcurrentRollingFileMapper mapper = readWrite(baseFile("evicted.map"), 2)) {
            final long address0 = mapper.map(position(0), REGION_SIZE);
            final Path file0 = OsMappings.realPath(baseFile("evicted_0.map"));
            final long address1 = mapper.map(position(1), REGION_SIZE);
            final long address2 = mapper.map(position(2), REGION_SIZE);//evicts file 0
            assertEquals(2, mapper.openFiles());
            assertTrue(OsMappings.isMapped(file0), "mapping outlives closing its file");
            mapper.unmap(position(0), address0, REGION_SIZE);
            assertFalse(OsMappings.isMapped(file0), "region of closed file must still be unmapped");
            mapper.unmap(position(1), address1, REGION_SIZE);
            mapper.unmap(position(2), address2, REGION_SIZE);
        }
    }
}
