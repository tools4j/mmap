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


import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

/**
 * A dynamic mapping is a {@link Mapping} whose {@linkplain #position() position} can be changed by
 * {@linkplain #moveTo(long) moving} to another file position.  Moving the mapping to a new position triggers mapping
 * and unmapping operations if necessary which are performed through a {@link RegionMapper}.
 */
public interface DynamicMapping extends Mapping {

    /**
     * Moves to the specified position in the file, mapping (and possibly unmapping) file region blocks if necessary.
     *
     * @param position the file position to move to
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     */
    boolean moveTo(long position);

    /**
     * Moves to the start of the region specified by index, mapping (and possibly unmapping) file region blocks if
     * necessary
     * <p>
     * This is equivalent to calling {@code moveTo(regionIndex * regionSize)}.
     *
     * @param regionIndex the non-negative region index, zero for first region
     * @return true if the region is ready for data access
     * @throws IllegalArgumentException if region index is negative
     */
    default boolean moveToRegion(final long regionIndex) {
        validateNonNegative(regionIndex, "Region index");
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
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return moveTo(startPosition - regionSize);
    }
}
