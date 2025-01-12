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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.region.api.RegionCursor;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.WaitingPolicy;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.api.RegionMapper.FAILED;

public final class NoWaitRegionCursor implements RegionCursor {
    private final RegionMapper regionMapper;
    private final RegionMetrics regionMetrics;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;
    private int mappingState;

    public NoWaitRegionCursor(final RegionMapper regionMapper) {
        this.regionMapper = requireNonNull(regionMapper);
        this.regionMetrics = regionMapper.regionMetrics();
        this.mappedPosition = NULL_POSITION;
        this.mappingState = FAILED;
    }

    @Override
    public RegionMapper regionMapper() {
        return regionMapper;
    }

    @Override
    public WaitingPolicy waitingPolicy() {
        return WaitingPolicy.noWait();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public long position() {
        return mappedPosition;
    }

    @Override
    public int mappingState() {
        return mappingState;
    }

    @Override
    public boolean moveTo(final long position) {
        final int mappingState = regionMapper.map(position, buffer);
        final boolean mapped = mappingState > 0;
        this.mappedPosition = mapped ? position : NULL_POSITION;
        this.mappingState = mappingState;
        return mapped;
    }

    @Override
    public void close() {
        regionMapper.close();
    }

    @Override
    public String toString() {
        return "NoWaitRegionCursor:mappingState=" + mappingState() +
                "|start=" + regionStartPosition() +
                "|offset=" + offset() +
                "|bytesAvailable=" + bytesAvailable();
    }
}
