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

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.impl.RegionMetricsImpl;

import java.util.ArrayList;
import java.util.List;

import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;

/**
 * Base region mapper for cache tests: records map and unmap calls and returns a synthetic address for every position
 * up to {@link #maxPosition()}, and {@code NULL_ADDRESS} beyond. Addresses are never dereferenced by the caching
 * mappers under test, so no actual memory is mapped.
 */
final class RecordingRegionMapper implements RegionMapper {

    static final int REGION_SIZE = 4096;
    private static final long ADDRESS_BASE = 1L << 40;

    private final RegionMetrics regionMetrics = new RegionMetricsImpl(REGION_SIZE);
    private final long maxPosition;
    private final List<String> calls = new ArrayList<>();
    private int mapped;
    private boolean closed;

    RecordingRegionMapper(final int regions) {
        this.maxPosition = (long) (regions - 1) * REGION_SIZE;
    }

    static long position(final int region) {
        return (long) region * REGION_SIZE;
    }

    static long address(final long position) {
        return ADDRESS_BASE + position;
    }

    long maxPosition() {
        return maxPosition;
    }

    /** @return calls since last invocation, e.g. ["map:2", "unmap:0"] with region indices */
    List<String> takeCalls() {
        final List<String> result = new ArrayList<>(calls);
        calls.clear();
        return result;
    }

    /** @return number of regions currently mapped through this mapper */
    int mapped() {
        return mapped;
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_WRITE;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        if (position > maxPosition) {
            return NULL_ADDRESS;
        }
        calls.add("map:" + position / REGION_SIZE);
        mapped++;
        return address(position);
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        if (address != address(position)) {
            throw new IllegalArgumentException("Position " + position + " was not mapped to address " + address);
        }
        calls.add("unmap:" + position / REGION_SIZE);
        mapped--;
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return false;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        calls.add("close");
        closed = true;
    }

    @Override
    public String toString() {
        return "RecordingRegionMapper:mapped=" + mapped + "|closed=" + closed;
    }
}
