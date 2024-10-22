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


import org.tools4j.mmap.region.impl.Constraints;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import java.util.function.Predicate;

import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionOffset;

/**
 * An offset mapping is a {@link DynamicMapping} that starts at an {@link #offset()} from the region's
 * {@linkplain #regionStartPosition() start position}. It can be re-positioned at any arbitrary file position
 * within the same region, or at any other position in the underlying file. Moving to a new position triggers mapping
 * and unmapping operations if necessary which are performed through a {@link RegionMapper}.
 */
public interface OffsetMapping extends DynamicMapping {

    /**
     * Moves the mapping to the specified position. If the position lies within the region already mapped, the buffer
     * offset will be adjusted without triggering a region mapping operation. Otherwise, the requested region is mapped.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     */
    @Override
    boolean moveTo(long position);

    /**
     * Moves the mapping forward or backward by the specified delta in bytes. Delegates to {@link #moveTo(long)} if
     * current and resulting position are valid. An exception is thrown if no position is currently mapped or if the
     * resulting position is negative.
     *
     * @param delta the position delta relative to the current position
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        return moveTo(position + delta);
    }

    /**
     * Moves the mapping to the start of the current region.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     */
    default boolean moveToCurrentRegion() {
        final long regionStartPosition = regionStartPosition();
        validatePositionState(regionStartPosition);
        return moveTo(regionStartPosition);
    }

    /**
     * Moves the mapping to an offset from the current region.
     *
     * @param offset the position offset relative to the current region start position
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     * @throws IllegalStateException if this mapping has no current position
     */
    default boolean moveToCurrentRegion(final int offset) {
        validateRegionOffset(offset, regionMetrics());
        final long regionStartPosition = regionStartPosition();
        validatePositionState(regionStartPosition);
        return moveTo(regionStartPosition + offset);
    }

    /**
     * Moves the mapping to an offset from the first region.
     *
     * @param offset the position offset within the first region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToFirstRegion(final int offset) {
        validateRegionOffset(offset, regionMetrics());
        return moveTo(offset);
    }

    /**
     * Moves the mapping to an offset from the next region start position, or from the first region start position if no
     * region is currently mapped.
     *
     * @param offset the position offset within the next region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToNextRegion(final int offset) {
        final int regionSize = regionSize();
        Constraints.validateRegionOffset(offset, regionSize);
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize : 0;
        return moveTo(nextStartPosition + offset);
    }

    /**
     * Moves the mapping to an offset from the previous region start position. Delegates to {@link #moveTo(long)} if
     * there is a previous region, and throws an exception if this region is not currently mapped, if it is the first
     * region or if the offset is invalid.
     *
     * @param offset the position offset within the previous region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position, or if it is at the first region
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToPreviousRegion(final int offset) {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        Constraints.validateRegionOffset(offset, regionSize);
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return moveTo(startPosition - regionSize + offset);
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
    boolean findLast(long startPosition, long positionIncrement, Predicate<? super OffsetMapping> matcher);

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
    boolean binarySearchLast(long startPosition, long positionIncrement, Predicate<? super OffsetMapping> matcher);
}
