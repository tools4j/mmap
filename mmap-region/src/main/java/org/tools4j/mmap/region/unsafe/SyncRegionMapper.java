/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.Unsafe;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionPosition;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public final class SyncRegionMapper implements RegionMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncRegionMapper.class);
    private final FileMapper fileMapper;
    private final int regionSize;
    private long mappedAddress = NULL_ADDRESS;
    private long mappedPosition = NULL_POSITION;
    private boolean closed;

    public SyncRegionMapper(final FileMapper fileMapper, final int regionSize) {
        validateRegionSize(regionSize);
        this.fileMapper = requireNonNull(fileMapper);
        this.regionSize = regionSize;
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
        validateRegionPosition(position, regionSize);
        if (position == mappedPosition) {
            return mappedAddress;
        }
        if (isClosed()) {
            return CLOSED;
        }
        try {
            unmapIfNecessary();
            final long addr = fileMapper.map(position, regionSize);
            if (addr > 0) {
                mappedAddress = addr;
                mappedPosition = position;
                return addr;
            } else {
                return FAILED;
            }
        } catch (final Exception exception) {
            return FAILED;
        }
    }

    private void unmapIfNecessary() {
        final long addr = mappedAddress;
        final long pos = mappedPosition;
        if (addr != NULL_ADDRESS) {
            mappedAddress = NULL_ADDRESS;
            mappedPosition = NULL_POSITION;
            assert pos != NULL_POSITION;
            fileMapper.unmap(addr, pos, regionSize);
        } else {
            assert pos == NULL_POSITION;
        }
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                unmapIfNecessary();
            } finally {
                closed = true;
            }
            LOGGER.info("{} closed.", this);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        return "SyncRegion:mappedPosition=" + mappedPosition;
    }
}
