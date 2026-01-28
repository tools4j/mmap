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
package org.tools4j.mmap.region.api;


import org.tools4j.mmap.region.unsafe.RegionMapper;

import java.util.function.Predicate;

import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;

/**
 * A dynamic mapping is a {@link RegionMapping} whose {@linkplain #position() position} can be changed by
 * {@link #moveTo(long) moving} to another position in the file. The position is a multiple of the
 * {@link #regionSize() region size} unless this dynamic mapping is also a {@link AdaptiveMapping} or
 * {@link ElasticMapping}.
 * <p>
 * Moving the region to a new position triggers mapping and unmapping operations if necessary which are performed
 * through a {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface DynamicMapping extends RegionMapping {
    /**
     * The granularity (or minimum increment) of position passed to {@link #moveTo(long)} method.
     * It is one for {@link AdaptiveMapping} or {@link ElasticMapping}, and equal to {@link #regionSize()} otherwise
     * when only region start positions are valid positions.
     *
     * @return the position granularity, typically one or region size
     */
    int positionGranularity();

    /**
     * Moves the region to the specified position, mapping (and possibly unmapping) file region blocks if necessary
     *
     * @param position  the position to move to, must be a multiple of {@linkplain #regionSize() region size} unless
     *                  this is an {@link AdaptiveMapping} or an {@link ElasticMapping}
     * @return true if the region is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative or not an allowed position value for this mapping
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

    /**
     * Performs a linear search for the last position that still results in a match given the specified matcher. Returns
     * true if a match is found, and leaves the mapping at the matching position. If no match is found, false is
     * returned and the mapping is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the increment to add to position at every step
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    boolean findLast(long startPosition, long positionIncrement, Predicate<? super DynamicMapping> matcher);

    /**
     * Performs a logarithmic binary search for the last position that still results in a match given the specified
     * matcher. Returns true if a match is found, and leaves the mapping at the matching position. If no match is found,
     * false is returned and the mapping is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the increment to add to position at every step
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    boolean binarySearchLast(long startPosition, long positionIncrement, Predicate<? super DynamicMapping> matcher);
}
