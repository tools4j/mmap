/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MappedRegion;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validatePowerOfTwo;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionPosition;

public class FixedRegion implements MappedRegion {
    private final FileMapper fileMapper;
    private final boolean closeFileMapperOnClose;
    private final RegionMetrics regionMetrics;
    private final UnsafeBuffer buffer;
    private long mappedPosition;

    public FixedRegion(final FileMapper fileMapper, final int regionSize) {
        this(fileMapper, true, 0L, regionSize);
    }

    public FixedRegion(final FixedSizeFileMapper fileMapper, final boolean closeFileMapperOnClose) {
        this(fileMapper, closeFileMapperOnClose, 0L, regionSize(fileMapper));
    }

    public FixedRegion(final FileMapper fileMapper,
                       final boolean closeFileMapperOnClose,
                       final long startPosition,
                       final int regionSize) {
        validatePowerOfTwo("Region size", regionSize);
        validateRegionPosition(startPosition, regionSize);
        this.fileMapper = requireNonNull(fileMapper);
        this.closeFileMapperOnClose = closeFileMapperOnClose;
        this.regionMetrics = new PowerOfTwoRegionMetrics(regionSize);
        this.buffer = new UnsafeBuffer(map(fileMapper, startPosition, regionSize), regionSize);
        this.mappedPosition = startPosition;
    }

    private static int regionSize(final FixedSizeFileMapper fileMapper) {
        final long maxSize = 1<<30;
        final long fileSize = fileMapper.fileSize();
        if (fileSize <= maxSize) {
            return (int)fileSize;
        }
        throw new IllegalArgumentException("File size exceeds max allowed region size: "
                + fileSize + " > " + maxSize);
    }

    private static long map(final FileMapper fileMapper, final long startPosition, final int regionSize) {
        final long address = fileMapper.map(startPosition, regionSize);
        if (address != NULL_ADDRESS) {
            return address;
        }
        throw new IllegalArgumentException("Mapping region failed: fileMapper=" + fileMapper +
                ", regionSize=" + regionSize);
    }

    private static void unmap(final FileMapper fileMapper,
                              final long mappedAddress,
                              final long mappedPosition,
                              final int regionSize) {
        if (mappedAddress != NULL_ADDRESS) {
            assert mappedPosition != NULL_POSITION;
            fileMapper.unmap(mappedAddress, mappedPosition, regionSize);
        } else {
            assert mappedPosition == NULL_POSITION;
        }
    }

    @Override
    public long position() {
        return mappedPosition;
    }

    @Override
    public long address() {
        return buffer.addressOffset();
    }

    @Override
    public boolean isMapped() {
        return mappedPosition != NULL_POSITION;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public int regionSize() {
        return regionMetrics.regionSize();
    }

    @Override
    public boolean isClosed() {
        return mappedPosition == NULL_POSITION;
    }

    @Override
    public long regionStartPosition() {
        return mappedPosition;
    }

    @Override
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public void close() {
        final long position = mappedPosition;
        if (position != NULL_POSITION) {
            mappedPosition = NULL_POSITION;
            final long address = address();
            buffer.wrap(0, 0);
            unmap(fileMapper, address, position, regionSize());
            if (closeFileMapperOnClose) {
                fileMapper.close();
            }
        }
    }

    @Override
    public String toString() {
        return "FixedRegion:startPosition=" + mappedPosition + "|regionSize=" + regionSize() + "|closed=" + isClosed();
    }
}
