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
package org.tools4j.mmap.region.api;


import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validPositionState;
import static org.tools4j.mmap.region.impl.Constraints.validRegionOffset;

/**
 * A region of a file that maps a certain block of file to a memory address. The region data can be accessed through the
 * {@linkplain #buffer() buffer} which can be mapped at any {@linkplain #offset() offset} from the
 * {@linkplain #regionStartPosition() region start position}. Note that a region may or may not be ready for data access
 * (see {@link RegionState} for more information).
 *
 * @see #regionState()
 */
public interface Region extends RegionStateAware {
    /**
     * Returns the region mapper in use to perform the actual mapping operations
     * @return the region mapper in use
     */
    RegionMapper regionMapper();

    /**
     * @return the region metrics determined by the region size
     */
    default RegionMetrics metrics() {
        return regionMapper().regionMetrics();
    }

    /**
     * @return the size of this region in bytes
     * @see #metrics()
     */
    default int regionSize() {
        return metrics().regionSize();
    }

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.

     * @return the region's start position, a non-negative multiple of the region size if it is ready for data access
     */
    default long regionStartPosition() {
        final long position = position();
        return position >= 0 ? metrics().regionPosition(position) : NULL_POSITION;
    }

    /**
     * Returns the absolute start position of the {@linkplain #buffer() buffer}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.  Note that the region may
     * not yet be ready for data access, which can be checked through {@link #isReady()} or through one of the await
     * methods.
     * <p>
     * If {@linkplain RegionState#isReady() ready} for data access, the returned position is mapped to the
     * {@linkplain #buffer() buffer}'s zero byte.
     *
     * @return the buffer's start position, a non-negative value if the region is ready for data access
     */
    long position();

    /**
     * Returns the buffer's offset from the region start
     * @return {@code position - regionStartPosition}
     */
    default int offset() {
        final long position = position();
        return position >= 0 ? metrics().regionOffset(position) : 0;
    }

    /**
     * Returns the number of bytes available via {@linkplain #buffer() buffer} which is equal to the buffer's
     * {@linkplain DirectBuffer#capacity() capacity}.
     * @return the bytes available via buffer, a value between zero and region size
     */
    default int bytesAvailable() {
        return buffer().capacity();
    }

    /**
     * Returns the buffer to access region data. If the region is not ready for data access, the returned buffer will
     * have zero {@linkplain DirectBuffer#capacity() capacity}.
     * @return the buffer to read and/or write region data.
     */
    AtomicBuffer buffer();

    /**
     * Maps this or a new region at the specified position and returns it. If the position lies within this region,
     * the buffer offset will be adjusted locally without an actual map operation. Otherwise, a new region is mapped
     * which can occur synchronously or asynchronously. Readiness for data access can be checked through
     * {@link #isReady()}.
     *
     * @param position the position to read from (absolute, not relative to current position or region start)
     * @return the region for accessing data from the given position (can be the same or a different region instance)
     * @throws IllegalArgumentException if position is negative
     */
    Region map(long position);

    /**
     * Maps this or a new region at an offset from the current position and returns it. Delegates to {@link #map(long)}
     * if the current position is valid, and throws an error if the region is not currently mapped.
     *
     * @param delta the position delta relative to the current position
     * @return the region for accessing data from the new position (can be the same or a different region instance)
     * @throws IllegalStateException if this region has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default Region mapFromCurrentPosition(final long delta) {
        final long position = position();
        validPositionState(position);
        return map(position + delta);
    }

    /**
     * Maps this or a new region at an offset from the current region start and returns it. Delegates to
     * {@link #map(long)} if the current region start position is valid, and throws an error if the region is not
     * currently mapped.
     *
     * @param delta the position delta relative to the current position
     * @return the region for accessing data from the new position (can be the same or a different region instance)
     * @throws IllegalStateException if this region has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default Region mapFromRegionStart(final int delta) {
        final long regionStartPosition = regionStartPosition();
        validPositionState(regionStartPosition);
        return map(regionStartPosition + delta);
    }

    /**
     * Maps the next region returns it, or the first region if no region is currently mapped. Mapping the new region can
     * occur synchronously or asynchronously, and readiness for data access can be checked through {@link #isReady()}.
     *
     * @return the next region for accessing data (can be the same or a different region instance)
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     * @see #mapNextRegion(int)
     */
    default Region mapNextRegion() {
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize() : 0;
        return map(nextStartPosition);
    }

    /**
     * Maps the next region at an offset and returns it, or the first region if no region is currently mapped. Mapping
     * the new region can occur synchronously or asynchronously, and readiness for data access can be checked through
     * {@link #isReady()}.
     *
     * @param offset the position offset within the next region, a non-negative value less than regionSize
     * @return the next region for accessing data from the given offset (can be the same or a different region instance)
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default Region mapNextRegion(final int offset) {
        final int regionSize = regionSize();
        validRegionOffset(offset, regionSize);
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize() : 0;
        return map(nextStartPosition + offset);
    }

    /**
     * Maps the previous region and returns it. Delegates to {@link #map(long)} if there is a previous region, and
     * throws an error if this region is not currently mapped or if it is the first region.
     *
     * @return the next region for accessing data from the given offset (can be the same or a different region instance)
     * @throws IllegalStateException if this region is not currently mapped, or if it is the first region
     */
    default Region mapPreviousRegion() {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return map(startPosition - regionSize);
    }

    /**
     * Maps the previous region at an offset and returns it. Delegates to {@link #map(long)} if there is a previous
     * region, and throws an error if this region is not currently mapped or if it is the first region.
     *
     * @param offset the position offset within the previous region, a non-negative value less than regionSize
     * @return the next region for accessing data from the given offset (can be the same or a different region instance)
     * @throws IllegalStateException if this region is not currently mapped, or if it is the first region
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default Region mapPreviousRegion(final int offset) {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        validRegionOffset(offset, regionSize);
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return map(startPosition - regionSize + offset);
    }

    /**
     * Closes this region.
     */
    void close();
}
