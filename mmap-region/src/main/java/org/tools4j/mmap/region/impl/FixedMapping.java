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
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.FixedSizeFileMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

public class FixedMapping implements Mapping {
    private final FileMapper fileMapper;
    private final boolean closeFileMapperOnClose;
    private final int size;
    private final UnsafeBuffer buffer;
    private long mappedPosition;

    public FixedMapping(final FileMapper fileMapper, final int size) {
        this(fileMapper, true, 0, size);
    }

    public FixedMapping(final FixedSizeFileMapper fileMapper, final boolean closeFileMapperOnClose) {
        this(fileMapper, closeFileMapperOnClose, 0, fileSize(fileMapper));
    }

    public FixedMapping(final FileMapper fileMapper,
                        final boolean closeFileMapperOnClose,
                        final long offset,
                        final int size) {
        requireNonNull(fileMapper);
        validateNonNegative("offset", offset);
        validateNonNegative("size", offset);
        this.fileMapper = fileMapper;
        this.closeFileMapperOnClose = closeFileMapperOnClose;
        this.size = size;
        this.buffer = new UnsafeBuffer(map(fileMapper, offset, size), size);
        this.mappedPosition = offset;
    }

    private static int fileSize(final FixedSizeFileMapper fileMapper) {
        final long fileSize = fileMapper.fileSize();
        final int size = (int)fileSize;
        if (size == fileSize) {
            return size;
        }
        throw new IllegalArgumentException("File size exceeds max allowed mapping size: " +
                fileSize + " > " + Integer.MAX_VALUE);
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
    public AccessMode accessMode() {
        return fileMapper.accessMode();
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
    public boolean isClosed() {
        return mappedPosition == NULL_POSITION;
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
            unmap(fileMapper, address, position, size);
            if (closeFileMapperOnClose) {
                fileMapper.close();
            }
        }
    }

    @Override
    public String toString() {
        return "FixedRegion:" +
                "position=" + mappedPosition +
                "|size=" + size +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
