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
package org.tools4j.mmap.region.unsafe;

import org.tools4j.mmap.region.api.Unsafe;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public final class SyncRingRegionMapper implements RegionMapper {

    private final FileMapper fileMapper;
    private final int regionSize;
    private final int regionSizeBits;
    private final int cacheSizeMask;
    private final long[] positions;
    private final long[] addresses;
    private boolean closed;

    public SyncRingRegionMapper(final FileMapper fileMapper, final int regionSize, final int cacheSize) {
        validateRegionSize(regionSize);
        validateRegionCacheSize(cacheSize);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionSize = regionSize;
        this.regionSizeBits = Integer.SIZE - Integer.numberOfLeadingZeros(regionSize - 1);
        this.cacheSizeMask = cacheSize - 1;
        this.positions = new long[cacheSize];
        this.addresses = new long[cacheSize];
        Arrays.fill(positions, NULL_POSITION);
        Arrays.fill(addresses, NULL_ADDRESS);
    }

    @Override
    public int regionSize() {
        return regionSize;
    }

    @Override
    public FileMapper fileMapper() {
        return fileMapper;
    }

    @Override
    public long map(final long position) {
        final int cacheIndex = (int)(cacheSizeMask & (position >> regionSizeBits));
        if (positions[cacheIndex] == position) {
            return addresses[cacheIndex];
        }
        unmapIfNecessary(cacheIndex);
        return map(cacheIndex, position);
    }

    private long map(final int cacheIndex, final long position) {
        final long addr = fileMapper.map(position, regionSize);
        if (addr > NULL_ADDRESS) {
            positions[cacheIndex] = position;
            addresses[cacheIndex] = addr;
            return addr;
        }
        return FAILED;
    }

    private void unmapIfNecessary(final int cacheIndex) {
        final long addr = addresses[cacheIndex];
        if (addr != NULL_ADDRESS) {
            final long pos = positions[cacheIndex];
            addresses[cacheIndex] = NULL_ADDRESS;
            positions[cacheIndex] = NULL_POSITION;
            assert pos != NULL_POSITION;
            fileMapper.unmap(addr, pos, regionSize);
        } else {
            assert positions[cacheIndex] == NULL_POSITION;
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                final int cacheSize = cacheSizeMask + 1;
                for (int cacheIndex = 0; cacheIndex < cacheSize; cacheIndex++) {
                    unmap(cacheIndex);
                }
            } finally {
                closed = true;
            }
            System.out.println(this + " closed");
        }
    }

    private void unmap(final int cacheIndex) {
        final long pos = positions[cacheIndex];
        if (pos != NULL_POSITION) {
            final long addr = addresses[cacheIndex];
            assert addr != NULL_ADDRESS;
            positions[cacheIndex] = NULL_POSITION;
            addresses[cacheIndex] = NULL_ADDRESS;
            try {
                fileMapper.unmap(addr, pos, regionSize);
            } catch (final Exception e) {
                //ignore
            }
        } else {
            assert addresses[cacheIndex] == NULL_ADDRESS;
        }
    }

    @Override
    public String toString() {
        return "SyncRingRegionMapper:cacheSize=" + (cacheSizeMask + 1) + "|regionSize=" + regionSize();
    }
}
