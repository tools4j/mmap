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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.FixedMapping;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.FixedSizeFileMapper;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateFixedMappingLength;
import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

public class FixedMappingImpl implements FixedMapping {
    private final FileMapper fileMapper;
    private final boolean closeFileMapperOnClose;
    private final int fileSize;
    private final UnsafeBuffer buffer;
    private long mappedPosition;

    public FixedMappingImpl(final FixedSizeFileMapper fileMapper) {
        this(fileMapper, 0L);
    }

    public FixedMappingImpl(final FixedSizeFileMapper fileMapper, final long offset) {
        this(fileMapper, offset, mappingLength(offset, fileMapper.fileSize()), true);
    }

    public FixedMappingImpl(final FileMapper fileMapper, final int length) {
        this(fileMapper, 0L, length, true);
    }

    public FixedMappingImpl(final FileMapper fileMapper,
                            final long offset,
                            final int length,
                            final boolean closeFileMapperOnClose) {
        requireNonNull(fileMapper);
        validateNonNegative("offset", offset);
        validateNonNegative("length", offset);
        this.fileMapper = fileMapper;
        this.closeFileMapperOnClose = closeFileMapperOnClose;
        this.fileSize = length;
        this.buffer = new UnsafeBuffer(map(fileMapper, offset, length), length);
        this.mappedPosition = offset;
    }

    private static int mappingLength(final long offset, final long fileSize) {
        validateFixedMappingLength(offset, fileSize);
        return (int)(fileSize - offset);
    }

    private static long map(final FileMapper fileMapper, final long position, final int length) {
        final long address = fileMapper.map(position, length);
        if (address != NULL_ADDRESS) {
            return address;
        }
        throw new IllegalArgumentException("Fixed-size mapping failed: fileMapper=" + fileMapper +
                ", length=" + length);
    }

    private static void unmap(final FileMapper fileMapper,
                              final long position,
                              final long address,
                              final int length) {
        if (address != NULL_ADDRESS) {
            assert position != NULL_POSITION;
            fileMapper.unmap(position, address, length);
        } else {
            assert position == NULL_POSITION;
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
            unmap(fileMapper, position, address, fileSize);
            if (closeFileMapperOnClose) {
                fileMapper.close();
            }
        }
    }

    @Override
    public String toString() {
        return "FixedMappingImpl" +
                ":position=" + mappedPosition +
                "|size=" + fileSize +
                "|accessMode=" + accessMode() +
                "|closed=" + isClosed();
    }
}
