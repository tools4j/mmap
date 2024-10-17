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


import org.tools4j.mmap.region.unsafe.RegionMapper;

import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

/**
 * A dynamic mapping is a {@link RegionMapping} whose {@link #position() position} can be changed by
 * {@link #moveTo(long) moving} to another position in the file, which is a multiple of the
 * {@link #regionSize() region size} unless this dynamic mapping is an {@link OffsetMapping}. Moving the region to a new
 * position triggers mapping and unmapping operations if necessary which are performed through a {@link RegionMapper}.
 */
public interface DynamicMapping extends RegionMapping {

    /**
     * Moves the region to the specified position, mapping (and possibly unmapping) file region blocks if necessary
     *
     * @param position the position to move to, must be a multiple of {@linkplain #regionSize() region size}
     * @return true if the region is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative or not a multiple of {@link #regionSize()}
     */
    boolean moveTo(long position);

    /**
     * Moves to the start of the region specified by index, mapping (and possibly unmapping) file region blocks if
     * necessary
     * <p>
     * This is equivalent to calling {@code moveTo(regionIndex * regionSize)}.
     *
     * @param regionIndex the non-negative region index, zero for first region
     * @return true if the mapping is ready for data access
     * @throws IllegalArgumentException if region index is negative
     */
    default boolean moveToRegion(final long regionIndex) {
        validateNonNegative("Region index", regionIndex);
        return moveTo(regionMetrics().regionPositionByIndex(regionIndex));
    }

    /**
     * Moves the mapping to the start of the first region at position zero.
     * <p>
     * This is equivalent to calling {@code moveTo(0)}.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    default boolean moveToFirstRegion() {
        return moveTo(0L);
    }

    /**
     * Moves the mapping to the next region start, or to the first region if no region is currently mapped.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    default boolean moveToNextRegion() {
        final RegionMetrics metrics = regionMetrics();
        final long regionStartPosition = metrics.regionPosition(position());
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + metrics.regionSize() : 0L;
        return moveTo(nextStartPosition);
    }

    /**
     * Moves the mapping to the previous region start. Delegates to {@link #moveTo(long)} if there is a previous region,
     * and throws an exception if this region is not currently mapped or if it is the first region.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this region is not currently mapped, or if it is the first region
     */
    default boolean moveToPreviousRegion() {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region before position " + position());
        }
        return moveTo(startPosition - regionSize);
    }
}
