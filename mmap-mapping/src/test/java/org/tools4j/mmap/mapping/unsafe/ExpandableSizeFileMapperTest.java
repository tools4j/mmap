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
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for {@link ExpandableSizeFileMapper}: the file grows on demand in multiples of the min file size, up to
 * the max file size.
 */
class ExpandableSizeFileMapperTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final long MAX_FILE_SIZE = 8L * REGION_SIZE;
    private static final FileInitialiser INITIALISER = FileInitialiser.none();

    @TempDir
    Path tempDir;

    private File file;

    private ExpandableSizeFileMapper mapper(final long minFileSize) {
        file = tempDir.resolve("expandable.map").toFile();
        return new ExpandableSizeFileMapper(file, minFileSize, MAX_FILE_SIZE, REGION_SIZE, INITIALISER);
    }

    private static long mapAndWrite(final FileMapper mapper, final long position, final long value) {
        final long address = mapper.map(position, REGION_SIZE);
        assertNotEquals(NULL_ADDRESS, address, "position " + position);
        new UnsafeBuffer(address, REGION_SIZE).putLong(0, value);
        mapper.unmap(position, address, REGION_SIZE);
        return address;
    }

    @Test
    void growsInRegionSizeStepsIfMinFileSizeIsZero() {
        try (final ExpandableSizeFileMapper mapper = mapper(0)) {
            assertEquals(AccessMode.READ_WRITE, mapper.accessMode());
            mapAndWrite(mapper, 0, 1);
            assertEquals(REGION_SIZE, file.length());
            mapAndWrite(mapper, 3L * REGION_SIZE, 2);
            assertEquals(4L * REGION_SIZE, file.length());
            mapAndWrite(mapper, REGION_SIZE, 3);
            assertEquals(4L * REGION_SIZE, file.length(), "file never shrinks");
        }
    }

    @Test
    void growsInMultiplesOfMinFileSize() {
        try (final ExpandableSizeFileMapper mapper = mapper(2L * REGION_SIZE)) {
            mapAndWrite(mapper, 0, 1);
            assertEquals(2L * REGION_SIZE, file.length());
            mapAndWrite(mapper, 2L * REGION_SIZE, 2);
            assertEquals(4L * REGION_SIZE, file.length());
            mapAndWrite(mapper, 7L * REGION_SIZE, 3);
            assertEquals(MAX_FILE_SIZE, file.length());
        }
    }

    @Test
    void dataSurvivesReopening() {
        try (final ExpandableSizeFileMapper mapper = mapper(0)) {
            mapAndWrite(mapper, 5L * REGION_SIZE, 0x0102030405060708L);
        }
        try (final ExpandableSizeFileMapper mapper = mapper(0)) {
            final long address = mapper.map(5L * REGION_SIZE, REGION_SIZE);
            assertEquals(0x0102030405060708L, new UnsafeBuffer(address, REGION_SIZE).getLong(0));
            mapper.unmap(5L * REGION_SIZE, address, REGION_SIZE);
            assertEquals(6L * REGION_SIZE, file.length());
        }
    }

    @Test
    void mappingBeyondMaxFileSizeIsRejected() {
        try (final ExpandableSizeFileMapper mapper = mapper(0)) {
            assertThrows(IllegalArgumentException.class, () -> mapper.map(MAX_FILE_SIZE, REGION_SIZE));
            assertThrows(IllegalArgumentException.class, () -> mapper.map(MAX_FILE_SIZE - REGION_SIZE + 1,
                    REGION_SIZE));
        }
    }

    @Test
    void invalidSizesAreRejected() {
        final File file = tempDir.resolve("invalid.map").toFile();
        assertThrows(IllegalArgumentException.class, () -> new ExpandableSizeFileMapper(file, 0,
                REGION_SIZE / 2, REGION_SIZE, INITIALISER), "max smaller than region");
        assertThrows(IllegalArgumentException.class, () -> new ExpandableSizeFileMapper(file, 4L * REGION_SIZE,
                2L * REGION_SIZE, REGION_SIZE, INITIALISER), "max smaller than min");
        assertThrows(IllegalArgumentException.class, () -> new ExpandableSizeFileMapper(file, REGION_SIZE,
                MAX_FILE_SIZE, 2 * REGION_SIZE, INITIALISER), "min smaller than region");
        assertThrows(IllegalArgumentException.class, () -> new ExpandableSizeFileMapper(file, 3L * REGION_SIZE,
                MAX_FILE_SIZE, REGION_SIZE, INITIALISER), "min not a power of two");
    }

    @Test
    void closedMapperDoesNotMapAndRejectsUnmap() {
        final ExpandableSizeFileMapper mapper = mapper(0);
        final long address = mapper.map(0, REGION_SIZE);
        assertFalse(mapper.isClosed());
        mapper.close();
        assertTrue(mapper.isClosed());
        assertTrue(mapper.toString().contains("|closed=true"), mapper.toString());
        mapper.close();
        assertThrows(IllegalStateException.class, () -> mapper.map(0, REGION_SIZE));
        assertThrows(IllegalStateException.class, () -> mapper.unmap(0, address, REGION_SIZE));
        FileChannels.unmap(address, REGION_SIZE);
    }
    @Test
    void concurrentMappingExtendsFileToLargestRequestedLength() throws Exception {
        final int threads = 8;
        final int regions = (int) (MAX_FILE_SIZE / REGION_SIZE);
        try (final ExpandableSizeFileMapper mapper = mapper(0)) {
            final CountDownLatch start = new CountDownLatch(1);
            final AtomicReference<Throwable> error = new AtomicReference<>();
            final List<Thread> workers = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int thread = t;
                final Thread worker = new Thread(() -> {
                    try {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        //each thread grows the file region by region, starting at a different region
                        for (int i = 0; i < regions; i++) {
                            final int region = (thread + i) % regions;
                            final long address = mapper.map((long) region * REGION_SIZE, REGION_SIZE);
                            assertNotEquals(NULL_ADDRESS, address, "region " + region);
                            new UnsafeBuffer(address, REGION_SIZE).putLong(thread * Long.BYTES, region + 1);
                            mapper.unmap((long) region * REGION_SIZE, address, REGION_SIZE);
                        }
                    } catch (final Throwable e) {
                        error.compareAndSet(null, e);
                    }
                }, "mapper-" + t);
                workers.add(worker);
                worker.start();
            }
            start.countDown();
            for (final Thread worker : workers) {
                worker.join(10_000);
            }
            assertNull(error.get(), () -> "mapping failed: " + error.get());
            assertEquals(MAX_FILE_SIZE, file.length());
            for (int region = 0; region < regions; region++) {
                final long address = mapper.map((long) region * REGION_SIZE, REGION_SIZE);
                final UnsafeBuffer buffer = new UnsafeBuffer(address, REGION_SIZE);
                for (int thread = 0; thread < threads; thread++) {
                    assertEquals(region + 1, buffer.getLong(thread * Long.BYTES), "region " + region);
                }
                mapper.unmap((long) region * REGION_SIZE, address, REGION_SIZE);
            }
        }
    }

    @Test
    void mapperSeesFileExtendedByAnotherMapper() {
        try (final ExpandableSizeFileMapper first = mapper(0);
             final ExpandableSizeFileMapper second = mapper(0)) {
            mapAndWrite(first, 0, 1);
            mapAndWrite(first, 3L * REGION_SIZE, 2);
            assertEquals(4L * REGION_SIZE, file.length());
            //no extension needed, the second mapper picks up the length from the file
            mapAndWrite(second, 2L * REGION_SIZE, 3);
            assertEquals(4L * REGION_SIZE, file.length());
            final long address = second.map(3L * REGION_SIZE, REGION_SIZE);
            assertEquals(2, new UnsafeBuffer(address, REGION_SIZE).getLong(0));
            second.unmap(3L * REGION_SIZE, address, REGION_SIZE);
        }
    }
}
