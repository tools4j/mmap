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


import org.tools4j.mmap.region.impl.DynamicMappings;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import java.util.function.Predicate;

import static org.tools4j.mmap.region.impl.Constraints.validateNonNegative;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;

/**
 * A dynamic mapping is a {@link Mapping} whose {@linkplain #position() position} can be changed by
 * {@link #moveTo(long) moving} to another position in the file. The position is a multiple of the
 * {@link #positionStepSize() position step size} which is defined by the dynamic mapping subclass.
 * <p>
 * Moving the region to a new position triggers mapping and unmapping operations if necessary which are performed
 * through a {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface DynamicMapping extends Mapping, RegionAware {
    /**
     * Returns the buffer's offset from the {@linkplain #regionStartPosition() region start position}, a value between
     * zero and (regionSize - 1)
     * @return the offset from the region start position: {@code position - regionStartPosition}
     */
    int regionOffset();

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.
     * <p>
     * If mapped, the region start position is always equal to the
     * {@linkplain #position() position} minus the {@linkplain #regionOffset() offset}.
     *
     * @return the region's start position, equal to the largest region size multiple that is less or equal to the
     *         current position, or -1 if unavailable
     */
    default long regionStartPosition() {
        return regionMetrics().regionPosition(position());
    }

    /**
     * The step size (or minimum increment) of position values passed to the {@link #moveTo(long)} method. For most
     * dynamic mapping types step size is equal to 1, except for {@link RegionMapping} where it is equal to the region
     * size.
     *
     * @return the step size for position values, typically one unless this is a region mapping
     */
    int positionStepSize();

    /**
     * Moves the region to the specified position, mapping (and possibly unmapping) file region blocks if necessary
     *
     * @param position  the position to move to, must be a multiple of {@linkplain #regionSize() region size} unless
     *                  this is an {@link ElasticMapping} or an {@link AdaptiveMapping}
     * @return true if the region is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative or not an allowed position value for this mapping
     */
    boolean moveTo(long position);

    /**
     * Moves the mapping forward or backward by the specified delta in bytes. Delegates to {@link #moveTo(long)} if
     * current and resulting position are valid. An exception is thrown if no position is currently mapped or if the
     * resulting position is negative.
     * <p>
     * This is equivalent to calling {@code moveTo(position() + delta)}.
     *
     * @param delta the position delta relative to the current position
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta, positionStepSize());
        return moveTo(position + delta);
    }


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
     * @param positionIncrement the increment added to position at every step, a multiple of {@link #positionStepSize()}
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    default boolean findLast(final long startPosition,
                             final long positionIncrement,
                             final Predicate<? super DynamicMapping> matcher) {
        return DynamicMappings.findLast(this, startPosition, positionIncrement, matcher);
    }

    /**
     * Performs a logarithmic binary search for the last position that still results in a match given the specified
     * matcher. Returns true if a match is found, and leaves the mapping at the matching position. If no match is found,
     * false is returned and the mapping is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the increment added to position at every step, a multiple of {@link #positionStepSize()}
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    default boolean binarySearchLast(final long startPosition,
                                     final long positionIncrement,
                                     final Predicate<? super DynamicMapping> matcher) {
        return DynamicMappings.binarySearchLast(this, startPosition, positionIncrement, matcher);
    }
}
