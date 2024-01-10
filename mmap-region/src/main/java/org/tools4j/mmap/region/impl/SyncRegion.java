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

import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.Region;

import java.util.Objects;

public class SyncRegion implements Region {
    private static final long NULL = -1;

    private final FileMapper fileMapper;
    private final int length;

    private long currentPosition = NULL;
    private long currentAddress = NULL;

    public SyncRegion(final FileMapper fileMapper, final int length) {
        this.fileMapper = Objects.requireNonNull(fileMapper);
        this.length = length;
    }

    @Override
    public boolean wrap(final long position, final DirectBuffer source) {
        final int regionOffset = (int)(position & (this.length - 1));
        final long regionStartPosition = position - regionOffset;
        if (map(regionStartPosition)) {
            source.wrap(currentAddress + regionOffset, this.length - regionOffset);
            return true;
        }
        return false;
    }

    @Override
    public boolean map(final long position) {
        if (position < 0)
            throw new IllegalArgumentException("Invalid regionStartPosition " + position);

        if (currentPosition == position)
            return true;

        if (currentAddress != NULL) {
            fileMapper.unmap(currentAddress, currentPosition, length);
            currentAddress = NULL;
            currentPosition = NULL;
        }

        final long mappedAddress = fileMapper.map(position, length);
        if (mappedAddress > 0) {
            currentAddress = mappedAddress;
            currentPosition = position;
            return true;

        } else {
            return false;
        }
    }

    @Override
    public boolean unmap() {
        if (currentAddress != NULL && currentPosition != NULL) {
            fileMapper.unmap(currentAddress, currentPosition, length);
            currentAddress = NULL;
            currentPosition = NULL;
        }
        return true;
    }

    @Override
    public void close() {
        unmap();
    }

    @Override
    public int size() {
        return length;
    }

}
