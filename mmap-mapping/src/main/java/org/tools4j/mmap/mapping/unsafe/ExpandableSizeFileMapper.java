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
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.core.internal.FileChannels;
import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateMinFileSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateOverlapSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateRegionSize;

/**
 * A {@link FileMapper} for a file that grows on demand between a minimum and maximum size, in multiples of the minimum
 * size. The file is physically larger by the overlap size, so that the last region can be mapped with its overlap.
 * <p>
 * <b>Note:</b> This class is <b>thread safe</b>; {@code map} and {@code unmap} may be called concurrently from
 * multiple threads.
 */
@Unsafe
public class ExpandableSizeFileMapper implements FileMapper, Describable {
    private final File file;
    private final long minFileSize;
    private final long maxFileSize;
    private final int overlapSize;
    private final FileHandle fileHandle;
    private final PreToucher preToucher;
    private final AtomicLong fileLengthCache = new AtomicLong();
    private final AtomicBoolean fileSizeExtensionLatch = new AtomicBoolean();

    public ExpandableSizeFileMapper(final File file,
                                    final long minFileSize,
                                    final long maxFileSize,
                                    final int regionSize,
                                    final int overlapSize,
                                    final FileInitialiser fileInitialiser) {
        this(file, minFileSize, maxFileSize, regionSize, overlapSize, fileInitialiser,
                MappingConfigurations.defaultLifecycleListener(), CoreConfigurations.defaultErrorListener());
    }

    public ExpandableSizeFileMapper(final File file,
                                    final long minFileSize,
                                    final long maxFileSize,
                                    final int regionSize,
                                    final int overlapSize,
                                    final FileInitialiser fileInitialiser,
                                    final LifecycleListener lifecycleListener,
                                    final ErrorListener errorListener) {
        this.file = Objects.requireNonNull(file);
        validateRegionSize(regionSize);
        validateMinFileSize(minFileSize);
        validateMaxFileSize(maxFileSize);
        validateOverlapSize(regionSize, overlapSize);
        //noinspection IfStatementWithIdenticalBranches
        if (minFileSize == 0) {
            if (maxFileSize % regionSize != 0) {
                throw new IllegalArgumentException("Max file size must be a multiple of region size " + regionSize +
                        " but was " + maxFileSize);
            }
            this.minFileSize = regionSize;
            this.maxFileSize = maxFileSize;
        } else {
            if (minFileSize % regionSize != 0) {
                throw new IllegalArgumentException("Min file size must be a multiple of region size " + regionSize +
                        " but was " + minFileSize);
            }
            if (maxFileSize % minFileSize != 0) {
                throw new IllegalArgumentException("Max file size must be a multiple of min file size " + minFileSize +
                        " but was " + maxFileSize);
            }
            this.minFileSize = minFileSize;
            this.maxFileSize = maxFileSize;
        }
        this.overlapSize = overlapSize;
        this.fileHandle = new FileHandle(this, AccessMode.READ_WRITE, file, fileInitialiser,
                minFileSize == 0 ? 0 : minFileSize + overlapSize, MappingConfigurations.defaultZeroFillMode(),
                lifecycleListener, errorListener);
        this.preToucher = new PreToucher();
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_WRITE;
    }

    @Override
    public long map(final long position, final int length, final MappingMode mode) {
        assert position >= 0;
        assert length >= 0;
        //NOTE: relying on fileHandle's closed check
        final boolean ifExists = mode == MappingMode.MAP_IF_EXISTS;
        if (position + length > mappingLimit()) {
            if (ifExists) {
                return NULL_ADDRESS;
            }
            throw new IllegalArgumentException("Attempt to map [" + position + ", " + (position + length - 1) + "] " +
                    "exceeds max file size " + maxFileSize + " plus overlap size " + overlapSize + " for file " + file);
        }
        final FileChannel channel = ifExists ? fileHandle.channelIfExists() : fileHandle.channel();
        if (channel == null || !channel.isOpen()) {
            return NULL_ADDRESS;
        }
        if (ifExists) {
            if (!fileCovers(channel, position + length)) {
                return NULL_ADDRESS;
            }
        } else {
            ensureFileLength(position + length);
        }
        final long address = FileChannels.map(channel, AccessMode.READ_WRITE.mapMode(), position, length);
        if (mode == MappingMode.MAP_AND_TOUCH) {
            preToucher.preTouch(position, length, address);
        }
        return address;
    }

    /**
     * Returns whether the file is at least the given length, querying its length only if the cached length is shorter,
     * as another process may have extended the file
     */
    private boolean fileCovers(final FileChannel channel, final long minLength) {
        if (minLength <= fileLengthCache.get()) {
            return true;
        }
        try {
            return minLength <= fileLengthCache.accumulateAndGet(channel.size(), Math::max);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to get the length of file " + file, e);
        }
    }

    @Override
    public long mappingLimit() {
        return maxFileSize + overlapSize;
    }

    @Override
    public void unmap(final long position, final long address, final int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        validateNotClosed(this);
        FileChannels.unmap(address, length);
    }

    private void ensureFileLength(final long minLength) {
        long cachedFileLength = fileLengthCache.get();
        if (minLength <= cachedFileLength) {
            return;
        }
        if (minLength > mappingLimit()) {
            throw new IllegalStateException("Exceeded max file size " + maxFileSize + " plus overlap size " +
                    overlapSize + " for file " + file);
        }
        //NOTE: threads waiting for another thread's extension spin on the cached length, published before the latch is
        //      released, so they neither query the file length nor contend for the channel's lock meanwhile; they
        //      read the latch before the CAS, which would otherwise write its cache line on every spin
        boolean tryExtensionLatch = true;
        while (cachedFileLength < minLength) {
            if (tryExtensionLatch && fileSizeExtensionLatch.compareAndSet(false, true)) {
                try {
                    final long fileLength = fileHandle.extendTo(newFileLength(minLength));
                    cachedFileLength = fileLengthCache.accumulateAndGet(fileLength, Math::max);
                } finally {
                    fileSizeExtensionLatch.set(false);
                }
            } else {
                Thread.onSpinWait();
                tryExtensionLatch = !fileSizeExtensionLatch.get();
                cachedFileLength = fileLengthCache.get();
            }
        }
    }

    /** Returns the smallest multiple of min file size plus overlap size that is at least the given length */
    private long newFileLength(final long minLength) {
        final long minLogicalLength = Math.max(1, minLength - overlapSize);
        final long newFileLength = minFileSize + minFileSize * ((minLogicalLength - 1) / minFileSize) + overlapSize;
        assert newFileLength <= mappingLimit() : "newFileLen exceeds maxFileSize plus overlapSize";
        return newFileLength;
    }

    @Override
    public boolean isClosed() {
        return fileHandle.isClosed();
    }

    @Override
    public void close() {
        if (fileHandle.closeIfOpened()) {
            preToucher.reset();
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("ExpandableSizeFileMapper")
                .append(":file=").append(file.getPath())
                .append("|minFileSize=").append(minFileSize)
                .append("|maxFileSize=").append(maxFileSize)
                .append("|overlapSize=").append(overlapSize)
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
