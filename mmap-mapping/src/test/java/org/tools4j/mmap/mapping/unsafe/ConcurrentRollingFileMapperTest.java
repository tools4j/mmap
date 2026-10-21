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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;

import java.io.File;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link ConcurrentRollingFileMapper}: rolling over files, creating files ahead, unmapping (also for
 * regions of evicted files), owner and sibling per-thread file mapper semantics, and concurrent mapping via owner and sibling.
 */
class ConcurrentRollingFileMapperTest {

    private static final long MAX_WAIT_MILLIS = 10_000;
    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int REGIONS_PER_FILE = 2;
    private static final long MAX_FILE_SIZE = (long) REGIONS_PER_FILE * REGION_SIZE;
    private static final FileInitialiser READ_WRITE_INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0);
    private static final FileInitialiser READ_ONLY_INITIALISER = FileInitialiser.zeroBytes(AccessMode.READ_ONLY, 0);

    @TempDir
    Path tempDir;

    private File baseFile(final String name) {
        return tempDir.resolve(name).toFile();
    }

    private static ConcurrentRollingFileMapper.PerThreadFileMapper readWrite(final File baseFile,
                                                                  final int filesToCreateAhead,
                                                                  final int maxOpenFiles) {
        return (ConcurrentRollingFileMapper.PerThreadFileMapper) ConcurrentRollingFileMapper.forReadWrite(baseFile,
                AccessMode.READ_WRITE, false, 0, MAX_FILE_SIZE, REGION_SIZE, filesToCreateAhead, maxOpenFiles,
                READ_WRITE_INITIALISER);
    }

    private static long filePosition(final int fileIndex) {
        return fileIndex * MAX_FILE_SIZE;
    }

    private static long regionPosition(final int region) {
        return (long) region * REGION_SIZE;
    }

    private static long valueFor(final int region) {
        return 0x1234_5678_0000_0000L + region;
    }

    private static void writeRegion(final FileMapper mapper, final int region, final int slot) {
        final long address = mapper.map(regionPosition(region), REGION_SIZE);
        assertNotEquals(NULL_ADDRESS, address, "region " + region);
        new UnsafeBuffer(address, REGION_SIZE).putLong(slot * Long.BYTES, valueFor(region));
        mapper.unmap(regionPosition(region), address, REGION_SIZE);
    }

    private static long readRegion(final FileMapper mapper, final int region, final int slot) {
        final long address = mapper.map(regionPosition(region), REGION_SIZE);
        assertNotEquals(NULL_ADDRESS, address, "region " + region);
        try {
            return new UnsafeBuffer(address, REGION_SIZE).getLong(slot * Long.BYTES);
        } finally {
            mapper.unmap(regionPosition(region), address, REGION_SIZE);
        }
    }

    @Test
    void writesRollOverFilesAndCanBeReadBackAfterEviction() {
        final int regions = 3 * REGIONS_PER_FILE;
        try (final ConcurrentRollingFileMapper.PerThreadFileMapper mapper = readWrite(baseFile("data.map"), 0, 2)) {
            assertEquals(AccessMode.READ_WRITE, mapper.accessMode());
            for (int region = 0; region < regions; region++) {
                writeRegion(mapper, region, 0);
            }
            assertEquals(2, mapper.openFiles());
            for (int region = 0; region < regions; region++) {
                assertEquals(valueFor(region), readRegion(mapper, region, 0), "region " + region);
            }
            assertEquals(2, mapper.openFiles());
        }
        for (int fileIndex = 0; fileIndex < 3; fileIndex++) {
            assertEquals(MAX_FILE_SIZE, baseFile("data_" + fileIndex + ".map").length());
        }
        assertFalse(baseFile("data_3.map").exists());
    }

    @Test
    void filesAreCreatedAhead() {
        try (final ConcurrentRollingFileMapper.PerThreadFileMapper mapper = readWrite(baseFile("ahead.map"), 2, 3)) {
            writeRegion(mapper, 0, 0);
            assertTrue(baseFile("ahead_0.map").isFile());
            assertTrue(baseFile("ahead_1.map").isFile());
            assertTrue(baseFile("ahead_2.map").isFile());
            assertFalse(baseFile("ahead_3.map").exists());
            assertEquals(3, mapper.openFiles());
        }
    }

    @Test
    void readOnlyDoesNotMapMissingFile() {
        try (final FileMapper writer = readWrite(baseFile("ro.map"), 0, 2)) {
            writeRegion(writer, 0, 0);
        }
        try (final FileMapper reader = ConcurrentRollingFileMapper.forReadOnly(baseFile("ro.map"), MAX_FILE_SIZE,
                REGION_SIZE, 2, READ_ONLY_INITIALISER)) {
            assertEquals(AccessMode.READ_ONLY, reader.accessMode());
            assertEquals(valueFor(0), readRegion(reader, 0, 0));
            assertEquals(NULL_ADDRESS, reader.map(filePosition(1), REGION_SIZE));
            assertFalse(baseFile("ro_1.map").exists());
        }
    }

    @Test
    void negativePositionIsNotMappedAndLengthMustMatchRegionSize() {
        try (final FileMapper mapper = readWrite(baseFile("args.map"), 0, 2)) {
            assertEquals(NULL_ADDRESS, mapper.map(-1, REGION_SIZE));
            assertThrows(IllegalArgumentException.class, () -> mapper.map(0, 2 * REGION_SIZE));
            assertThrows(IllegalArgumentException.class, () -> mapper.unmap(0, 1, 2 * REGION_SIZE));
        }
    }

    @Test
    void unmapReleasesMappingOfOpenFile() {
        try (final FileMapper mapper = readWrite(baseFile("open.map"), 0, 2)) {
            final long address = mapper.map(filePosition(0), REGION_SIZE);
            final Path file0 = OsMappings.realPath(baseFile("open_0.map"));
            assertTrue(OsMappings.isMapped(file0));
            mapper.unmap(filePosition(0), address, REGION_SIZE);
            assertFalse(OsMappings.isMapped(file0));
        }
    }

    @Test
    void unmapReleasesMappingOfAlreadyClosedFile() {
        try (final ConcurrentRollingFileMapper.PerThreadFileMapper mapper = readWrite(baseFile("evicted.map"), 0, 2)) {
            final long address0 = mapper.map(filePosition(0), REGION_SIZE);
            final Path file0 = OsMappings.realPath(baseFile("evicted_0.map"));
            final long address1 = mapper.map(filePosition(1), REGION_SIZE);
            final long address2 = mapper.map(filePosition(2), REGION_SIZE);//evicts file 0
            assertEquals(2, mapper.openFiles());
            assertTrue(OsMappings.isMapped(file0), "mapping outlives closing its file");
            mapper.unmap(filePosition(0), address0, REGION_SIZE);
            assertFalse(OsMappings.isMapped(file0), "region of closed file must still be unmapped");
            mapper.unmap(filePosition(1), address1, REGION_SIZE);
            mapper.unmap(filePosition(2), address2, REGION_SIZE);
        }
    }

    @Test
    void siblingSharesFilesAndIsReleasedWithOwner() {
        final FileMapper owner = readWrite(baseFile("sibling.map"), 0, 2);
        final FileMapper sibling = FileMappers.forThread(owner);
        assertNotSame(owner, sibling);
        assertEquals(owner.accessMode(), sibling.accessMode());
        writeRegion(owner, 0, 0);
        assertEquals(valueFor(0), readRegion(sibling, 0, 0));
        writeRegion(sibling, 1, 1);
        assertEquals(valueFor(1), readRegion(owner, 1, 1));

        final long address = sibling.map(regionPosition(0), REGION_SIZE);
        owner.close();
        assertTrue(owner.isClosed());
        assertTrue(sibling.isClosed());
        assertThrows(IllegalStateException.class, () -> sibling.map(regionPosition(0), REGION_SIZE));
        assertThrows(IllegalStateException.class, () -> FileMappers.forThread(owner));
        assertThrows(IllegalStateException.class, () -> sibling.unmap(regionPosition(0), address, REGION_SIZE));
        FileChannels.unmap(address, REGION_SIZE);
        owner.close();//idempotent
    }

    @Test
    void closingSiblingReleasesOnlySibling() {
        try (final FileMapper owner = readWrite(baseFile("release.map"), 0, 2)) {
            final FileMapper sibling = FileMappers.forThread(owner);
            writeRegion(sibling, 0, 0);
            sibling.close();
            assertTrue(sibling.isClosed());
            assertFalse(owner.isClosed());
            assertThrows(IllegalStateException.class, () -> sibling.map(regionPosition(0), REGION_SIZE));
            assertEquals(valueFor(0), readRegion(owner, 0, 0));
            //released borrower slot can be reused
            final FileMapper another = FileMappers.forThread(owner);
            assertEquals(valueFor(0), readRegion(another, 0, 0));
        }
    }

    @Test
    void atMostMaxOpenFilesPerThreadFileMappers() {
        try (final FileMapper owner = readWrite(baseFile("limit.map"), 0, 2)) {
            FileMappers.forThread(owner);
            assertThrows(IllegalStateException.class, () -> FileMappers.forThread(owner));
        }
    }

    /**
     * Owner and sibling map random regions concurrently, from more files than can be open at a time, so that files are
     * evicted while the other thread still has regions of it mapped. Each thread writes its own slot in every region it
     * maps, and checks that the other thread's slot is either still unset or holds the expected value.
     */
    @ParameterizedTest(name = "maxOpenFiles={0}")
    @ValueSource(ints = {2, 3})
    void ownerAndSiblingMapConcurrently(final int maxOpenFiles) throws Exception {
        final int regions = 6 * REGIONS_PER_FILE;
        final int iterations = 5_000;
        final long seed = System.nanoTime();
        final File baseFile = baseFile("concurrent.map");
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        try (final FileMapper owner = readWrite(baseFile, 0, maxOpenFiles)) {
            final FileMapper[] mappers = {owner, FileMappers.forThread(owner)};
            final CyclicBarrier start = new CyclicBarrier(mappers.length);
            final Thread[] threads = new Thread[mappers.length];
            for (int t = 0; t < threads.length; t++) {
                final int slot = t;
                threads[t] = new Thread(() -> {
                    final Random random = new Random(seed + slot);
                    final FileMapper mapper = mappers[slot];
                    try {
                        start.await(MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                        for (int i = 0; i < iterations; i++) {
                            final int region = random.nextInt(regions);
                            final long address = mapper.map(regionPosition(region), REGION_SIZE);
                            assertNotEquals(NULL_ADDRESS, address, "region " + region);
                            final UnsafeBuffer buffer = new UnsafeBuffer(address, REGION_SIZE);
                            buffer.putLongVolatile(slot * Long.BYTES, valueFor(region));
                            final long other = buffer.getLongVolatile((1 - slot) * Long.BYTES);
                            if (other != 0 && other != valueFor(region)) {
                                throw new AssertionError("region " + region + ": unexpected value " + other);
                            }
                            mapper.unmap(regionPosition(region), address, REGION_SIZE);
                        }
                    } catch (final Throwable e) {
                        failures.add(e);
                        start.reset();
                    }
                }, "mapper-" + t);
                threads[t].start();
            }
            for (final Thread thread : threads) {
                thread.join(MAX_WAIT_MILLIS);
                assertFalse(thread.isAlive(), thread.getName() + " still running after " + MAX_WAIT_MILLIS + "ms");
            }
            if (!failures.isEmpty()) {
                final AssertionError error = new AssertionError(failures.size() + " failures, seed=" + seed,
                        failures.peek());
                failures.stream().skip(1).forEach(error::addSuppressed);
                throw error;
            }
            for (int region = 0; region < regions; region++) {
                for (int slot = 0; slot < mappers.length; slot++) {
                    final long value = readRegion(owner, region, slot);
                    assertTrue(value == 0 || value == valueFor(region), "region " + region + ", slot " + slot);
                }
            }
        }
    }
}
