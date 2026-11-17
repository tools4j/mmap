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

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.internal.FileAllocation;
import org.tools4j.mmap.core.internal.FileChannelLock;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.ZeroFillMode;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.config.MappingConfigurations.ZERO_FILL_PROPERTY;

/**
 * Allocates new space of a writable file natively instead of writing zeros, if the file is initialised by
 * {@link FileInitialiser#ZERO} and the {@link ZeroFillMode} and platform allow it, warning as the mode asks for when
 * zeros are written instead; used by a {@link FileHandle} for its file.
 */
final class ZeroFillAllocator {
    /** The results of native allocation reported once per JVM, as bits by ordinal */
    private static final AtomicInteger REPORTED_ALLOCATIONS = new AtomicInteger();

    private final Describable owner;
    private final File file;
    private final boolean allocates;
    private final ZeroFillMode zeroFillMode;
    private final ErrorListener errorListener;

    /**
     * Constructor.
     *
     * @param owner           the file mapper owning the file, the source of warnings
     * @param accessMode      the access mode of the file, allocating natively for writable files only
     * @param file            the file, for reference in warnings
     * @param fileInitialiser the initialiser of the file, allocating natively for {@link FileInitialiser#ZERO} only
     * @param zeroFillMode    whether and when to allocate natively
     * @param errorListener   the listener warned when zeros are written instead of allocating natively
     */
    ZeroFillAllocator(final Describable owner,
                      final AccessMode accessMode,
                      final File file,
                      final FileInitialiser fileInitialiser,
                      final ZeroFillMode zeroFillMode,
                      final ErrorListener errorListener) {
        this.owner = requireNonNull(owner);
        this.file = requireNonNull(file);
        this.zeroFillMode = requireNonNull(zeroFillMode);
        this.allocates = accessMode == AccessMode.READ_WRITE && fileInitialiser == FileInitialiser.ZERO &&
                zeroFillMode != ZeroFillMode.WRITE;
        this.errorListener = requireNonNull(errorListener);
    }

    /**
     * Allocates the new space natively while holding the exclusive lock, if the file is writable, initialised by
     * {@link FileInitialiser#ZERO}, and the zero fill mode and platform allow it.
     *
     * @param lock        the lock of the file, held exclusively
     * @param currentSize the current file size, the start of the new space
     * @param size        the new file size
     * @return true if allocated, false if the initialiser's hook is to be invoked
     * @throws IOException if allocating fails other than by not being supported, for instance if the disk is full
     */
    boolean allocate(final FileChannelLock lock, final long currentSize, final long size) throws IOException {
        if (!allocates || currentSize >= size) {
            return false;
        }
        final FileAllocation allocation = lock.allocate(currentSize, size - currentSize,
                zeroFillMode == ZeroFillMode.FALLOCATE);
        if (allocation == FileAllocation.ALLOCATED) {
            return true;
        }
        if (warns(zeroFillMode, allocation) &&
                (allocation == FileAllocation.FILE_SYSTEM_UNSUPPORTED || firstReport(allocation))) {
            errorListener.onWarning(owner, "Zero fill writes zeros instead of allocating file space, as " +
                    allocation.description() + ", for file " + file + "; set " + ZERO_FILL_PROPERTY +
                    "=WRITE to write zeros without warning");
        }
        return false;
    }

    /**
     * Returns true if the zero fill mode warns about the allocation result: AUTO only about native access not being
     * enabled, FALLOCATE about any reason for writing zeros; the file system is detected once per file
     */
    static boolean warns(final ZeroFillMode zeroFillMode, final FileAllocation allocation) {
        return switch (allocation) {
            case ALLOCATED, SKIPPED -> false;
            case NATIVE_ACCESS_DISABLED -> zeroFillMode != ZeroFillMode.WRITE;
            case JDK_UNSUPPORTED, PLATFORM_UNSUPPORTED, NATIVE_ACCESS_DENIED, FILE_SYSTEM_UNSUPPORTED ->
                    zeroFillMode == ZeroFillMode.FALLOCATE;
        };
    }

    /** Returns true the first time per JVM for the given allocation result */
    private static boolean firstReport(final FileAllocation allocation) {
        final int bit = 1 << allocation.ordinal();
        int reported;
        do {
            reported = REPORTED_ALLOCATIONS.get();
            if ((reported & bit) != 0) {
                return false;
            }
        } while (!REPORTED_ALLOCATIONS.compareAndSet(reported, reported | bit));
        return true;
    }
}
