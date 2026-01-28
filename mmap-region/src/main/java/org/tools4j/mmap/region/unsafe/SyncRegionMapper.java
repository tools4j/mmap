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
package org.tools4j.mmap.region.unsafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.impl.PowerOfTwoRegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;

class SyncRegionMapper implements DirectRegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyncRegionMapper.class);
    private final FileMapper fileMapper;
    private final RegionMetrics regionMetrics;

    public SyncRegionMapper(final FileMapper fileMapper, final int regionSize) {
        this(fileMapper, new PowerOfTwoRegionMetrics(regionSize));
    }

    public SyncRegionMapper(final FileMapper fileMapper, final RegionMetrics regionMetrics) {
        this.fileMapper = requireNonNull(fileMapper);
        this.regionMetrics = requireNonNull(regionMetrics);
    }

    @Override
    public FileMapper fileMapper() {
        return fileMapper;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        try {
            final long addr = fileMapper.map(position, regionSize);
            return addr > 0 ? addr : NULL_ADDRESS;
        } catch (final Exception exception) {
            return NULL_ADDRESS;
        }
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        fileMapper.unmap(position, address, regionSize);
    }

    @Override
    public boolean isClosed() {
        return fileMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            fileMapper.close();
            LOGGER.info("Closed {}.", this);
        }
    }

    @Override
    public String toString() {
        return "SyncRegionMapper" +
                ":fileMapper=" + fileMapper +
                "|regionSize=" + regionSize() +
                "|closed=" + isClosed();
    }
}
