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
import org.tools4j.mmap.region.impl.Constraints;
import org.tools4j.mmap.region.impl.DefaultRegion;

import java.util.function.Predicate;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionOffset;

/**
 * A region is a file block directly mapped into memory. The file data is accessible through the {@link #buffer()},
 * which can be positioned and any {@link #offset()} within the region. The region can also be moved to another file
 * position which triggers region pages to be mapped and unmapped with help of a {@link RegionMapper}.
 */
public interface Region extends AutoCloseable {
    static Region create(final RegionMapper regionMapper) {
        return new DefaultRegion(regionMapper);
    }

    /**
     * @return the size of a mappable memory region in bytes
     * @see #regionMetrics()
     */
    default int regionSize() {
        return regionMetrics().regionSize();
    }

    /**
     * @return the region metrics determined by the region size
     */
    RegionMetrics regionMetrics();

    default boolean isMapped() {
        return position() > NULL_POSITION;
    }

    boolean isClosed();

    /**
     * Returns the absolute start position of the {@linkplain #buffer() buffer}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet. If
     * {@linkplain #isMapped() mapped} then the returned position corresponds to the {@linkplain #buffer() buffer}'s
     * zero byte.
     *
     * @return the buffer's start position, a non-negative value if the region is mapped
     */
    long position();

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.
     *
     * @return the region's start position, equal to the largest region size multiple that is less or equal to the
     *         current position, or {@link NullValues#NULL_POSITION NULL_POSITION} if unavailable
     */
    long regionStartPosition();

    /**
     * Returns the buffer's offset from the {@linkplain #regionStartPosition() region start position}, a value between
     * zero and (regionSize - 1)
     * @return {@code position - regionStartPosition}
     */
    int offset();

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
     * Moves the cursor to the specified position. If the position lies within the region already mapped, the buffer
     * offset will be adjusted without triggering a region mapping operation. Otherwise, the requested region is mapped.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @return true if the cursor is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     */
    boolean moveTo(long position);

    /**
     * Moves the cursor forward or backward by the specified delta. Delegates to {@link #moveTo(long)} if current and
     * resulting position are valid. An exception is thrown if the region is not currently mapped or if the resulting
     * position is negative.
     *
     * @param delta the position delta relative to the current position
     * @return true if the cursor is ready for data access, and false otherwise
     * @throws IllegalStateException if this region has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        return moveTo(position + delta);
    }

    /**
     * Moves the cursor to a new position relative from the current {@link #regionStartPosition() region start position}.
     * Delegates to {@link #moveTo(long)} if current and resulting position are valid. An exception is thrown if the
     * region is not currently mapped or if the resulting position is negative.
     *
     * @param delta the position delta relative to the current region start position
     * @return true if the cursor is ready for data access, and false otherwise
     * @throws IllegalStateException if this region has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    default boolean moveRelativeToRegionStart(final int delta) {
        final long regionStartPosition = regionStartPosition();
        validatePositionState(regionStartPosition);
        return moveTo(regionStartPosition + delta);
    }

    /**
     * Moves the cursor to the start of the first region at position zero.
     *
     * @return true if the cursor is ready for data access, and false otherwise
     */
    default boolean moveToFirstRegion() {
        return moveTo(0);
    }

    /**
     * Moves the cursor to an offset from the first region.
     *
     * @param offset the position offset within the first region, a non-negative value less than region size
     * @return true if the cursor is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToFirstRegion(final int offset) {
        validateRegionOffset(offset, regionMetrics());
        return moveTo(offset);
    }
    /**
     * Moves the cursor to the next region start, or to the first region if no region is currently mapped.
     *
     * @return true if the cursor is ready for data access, and false otherwise
     */
    default boolean moveToNextRegion() {
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize() : 0;
        return moveTo(nextStartPosition);
    }

    /**
     * Moves the cursor to an offset from the next region start position, or from the first region start position if no
     * region is currently mapped.
     *
     * @param offset the position offset within the next region, a non-negative value less than region size
     * @return true if the cursor is ready for data access, and false otherwise
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
     * Moves the cursor to the previous region start. Delegates to {@link #moveTo(long)} if there is a previous region,
     * and throws an exception if this region is not currently mapped or if it is the first region.
     *
     * @return true if the cursor is ready for data access, and false otherwise
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

    /**
     * Moves the cursor to an offset from the previous region start position. Delegates to {@link #moveTo(long)} if there
     * is a previous region, and throws an exception if this region is not currently mapped, if it is the first region
     * or if the offset is invalid.
     *
     * @param offset the position offset within the previous region, a non-negative value less than region size
     * @return true if the cursor is ready for data access, and false otherwise
     * @throws IllegalStateException if this region is not currently mapped, or if it is the first region
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
     * true if a match is found, and leaves the region at the matching position. If no match is found, false is returned
     * and the region is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the increment to add to position at every step
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    boolean findLast(long startPosition, long positionIncrement, Predicate<? super Region> matcher);

    /**
     * Performs a logarithmic binary search for the last position that still results in a match given the specified
     * matcher. Returns true if a match is found, and leaves the region at the matching position. If no match is found,
     * false is returned and the region is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the increment of which a multiple is added to position at every step
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    boolean binarySearchLast(long startPosition, long positionIncrement, Predicate<? super Region> matcher);

    /**
     * Closes this region reader and the underlying region.
     */
    void close();
}
