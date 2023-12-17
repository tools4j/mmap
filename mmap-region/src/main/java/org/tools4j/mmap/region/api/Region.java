/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
     * @return  the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     *          {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been requested yet
     */
    default long regionStartPosition() {
        final long position = position();
        return position >= 0 ? metrics().regionPosition(position) : NULL_POSITION;
    }

    /**
     * Returns the absolute start position of the {@linkplain #buffer() buffer}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been requested yet.  Note that the region may
     * not yet be ready for data access, which can be checked through {@link #isReady()} or through one of the await
     * methods.
     * <p>
     * If {@linkplain RegionState#isReady() ready} for data access, the returned position is mapped to the
     * {@linkplain #buffer() buffer}'s zero byte.
     *
     * @return the buffer's start position, negative only if no position has been requested yet
     */
    long position();

    /**
     * Returns the buffer's offset from the region start.
     * @return {@code position - regionStartPosition}
     */
    default int offset() {
        return metrics().regionOffset(position());
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
     */
    Region map(long position);

    default Region mapFromCurrentPosition(final long delta) {
        final long position = position();
        validPositionState(position);
        return map(position + delta);
    }

    default Region mapFromRegionStart(final int offset) {
        validRegionOffset(offset, regionSize());
        return map(regionStartPosition() + offset);
    }

    default Region mapNextRegion() {
        return map(Math.max(0, regionStartPosition()) + regionSize());
    }

    default Region mapNextRegion(final int offset) {
        final int regionSize = regionSize();
        validRegionOffset(offset, regionSize);
        return map(Math.max(0, regionStartPosition()) + regionSize + offset);
    }

    default Region mapPreviousRegion() {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return map(startPosition - regionSize);
    }

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
