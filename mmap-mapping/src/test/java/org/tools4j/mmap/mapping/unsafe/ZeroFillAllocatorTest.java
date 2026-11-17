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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.internal.FileAllocation;
import org.tools4j.mmap.core.internal.FileChannelLock;
import org.tools4j.mmap.core.internal.OS;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.ZeroFillMode;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for {@link ZeroFillAllocator}.
 */
class ZeroFillAllocatorTest {

    private static final long SIZE = 64 * 1024;
    private static final Describable OWNER = sb -> sb.append("ZeroFillAllocatorTest");

    /** Whether zero fill allocates natively in this JVM: tests run with native access enabled */
    private static final boolean NATIVE_ALLOCATION = OS.LINUX && Runtime.version().feature() >= 22;

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "zeroFill={0}")
    @EnumSource(ZeroFillMode.class)
    void allocatesForZeroOnlyWhereSupported(final ZeroFillMode zeroFillMode) throws IOException {
        final FileInitialiser[] initialisers = {FileInitialiser.LAZY, FileInitialiser.ZERO, FileInitialiser.MINUS_ONE};
        for (final FileInitialiser initialiser : initialisers) {
            final File file = tempDir.resolve(initialiser + "_" + zeroFillMode + ".map").toFile();
            final ZeroFillAllocator allocator = new ZeroFillAllocator(OWNER, AccessMode.READ_WRITE, file, initialiser,
                    zeroFillMode, ErrorListener.NO_OP);
            final boolean allocates = initialiser == FileInitialiser.ZERO && zeroFillMode != ZeroFillMode.WRITE &&
                    NATIVE_ALLOCATION;
            try (final RandomAccessFile raf = new RandomAccessFile(file, "rw");
                 final FileChannelLock lock = FileChannelLock.create(raf.getChannel())) {
                lock.lock();
                try {
                    assertEquals(allocates, allocator.allocate(lock, 0, SIZE), initialiser.toString());
                    assertEquals(allocates ? SIZE : 0, raf.length(), initialiser.toString());
                    assertFalse(allocator.allocate(lock, SIZE, SIZE), "no new space, " + initialiser);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @ParameterizedTest(name = "zeroFill={0}")
    @EnumSource(ZeroFillMode.class)
    void readOnlyFileIsNeverAllocated(final ZeroFillMode zeroFillMode) throws IOException {
        final File file = tempDir.resolve("readOnly.map").toFile();
        final ZeroFillAllocator allocator = new ZeroFillAllocator(OWNER, AccessMode.READ_ONLY, file,
                FileInitialiser.ZERO, zeroFillMode, ErrorListener.NO_OP);
        try (final RandomAccessFile raf = new RandomAccessFile(file, "rw");
             final FileChannelLock lock = FileChannelLock.create(raf.getChannel())) {
            lock.lock();
            try {
                assertFalse(allocator.allocate(lock, 0, SIZE));
                assertEquals(0, raf.length());
            } finally {
                lock.unlock();
            }
        }
    }

    @Test
    void zeroFillModesWarnAboutWritingZerosAsDocumented() {
        for (final FileAllocation allocation : FileAllocation.values()) {
            assertFalse(ZeroFillAllocator.warns(ZeroFillMode.WRITE, allocation), "WRITE, " + allocation);
            assertEquals(allocation == FileAllocation.NATIVE_ACCESS_DISABLED,
                    ZeroFillAllocator.warns(ZeroFillMode.AUTO, allocation), "AUTO, " + allocation);
            assertEquals(allocation != FileAllocation.ALLOCATED && allocation != FileAllocation.SKIPPED,
                    ZeroFillAllocator.warns(ZeroFillMode.FALLOCATE, allocation), "FALLOCATE, " + allocation);
        }
    }
}
