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
package org.tools4j.mmap.region.api;


import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static org.tools4j.mmap.region.impl.Constraints.validateLength;
import static org.tools4j.mmap.region.impl.Constraints.validateLimit;
import static org.tools4j.mmap.region.impl.Constraints.validatePosition;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionOffset;

/**
 * An elastic mapping is a {@link AdaptiveMapping} that starts at an {@linkplain #offset() offset} from the region's
 * {@linkplain #regionStartPosition() start position} and has a restricted {@linkplain #length() length}. Note that an
 * elastic mapping can map an arbitrary slice of the region (or of the underlying file, as long as the slice does not
 * cross region boundaries). A pure {@link AdaptiveMapping} always spans all bytes until the ends of the region.
 * <p>
 * Moving the region to a new position triggers mapping and unmapping operations if necessary which are performed
 * through a {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface ElasticMapping extends DynamicMapping {
    /**
     * Moves the mapping to the specified position. The current mapping length is preserved if possible and if this
     * mapping is currently mapped, otherwise it is set to reach to the region end position.
     * <p>
     * If the new position lies within the region that is already mapped, only the buffer will be adjusted without
     * triggering a region mapping operation. Otherwise, the requested region is mapped (and previous regions possibly
     * unmapped, so it is illegal to continue using buffers wrapped to the previous address).
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     */
    @Override
    default boolean moveTo(final long position) {
        validatePosition(position);
        final RegionMetrics metrics = regionMetrics();
        final int maxAvailable = metrics.regionSize() - metrics.regionOffset(position);
        final int newLength = isMapped() ? Math.min(length(), maxAvailable) : maxAvailable;
        return moveTo(position, newLength);
    }

    /**
     * Moves the mapping to the specified position and maps the specified number of bytes. If the position lies within
     * the region already mapped, buffer offset and capacity will be adjusted without triggering a region mapping
     * operation. Otherwise, the requested region is mapped.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @param length the number of bytes starting from {@code position}, or -1 to span all bytes available from the new
     *               position to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if position or length is negative, or the length crosses region boundaries
     */
    boolean moveTo(long position, int length);

    /**
     * Returns the mapping length in bytes, the same as {@link #bytesAvailable()} and equal to the
     * {@linkplain #buffer() buffer's} {@linkplain DirectBuffer#capacity() capacity}.
     *
     * @return the number of bytes mapped and available via buffer, zero if not {@linkplain #isMapped() mapped}
     */
    default int length() {
        return bytesAvailable();
    }

    /**
     * Sets the new length of this mapping, extending or limiting the bytes currently available through the
     * {@link #buffer() buffer}. If {@code length == -1}, the length is extended to the end of the region.
     * If the new length exceeds beyond the region limit, an exception is thrown.
     *
     * @param length the new byte length for this mapping, a value in {@code [0..(regionSize - offset)]}, or
     *               -1 to extend the length to the end of the region
     * @throws IllegalArgumentException if {@code length <= -2} or if it exceeds the mapping beyond the region boundaries
     */
    default void length(final int length) {
        final DirectBuffer buffer = buffer();
        final int actualLength = validateLength(position(), length, regionMetrics());
        if (actualLength != buffer.capacity()) {
            buffer.wrap(buffer.addressOffset(), actualLength);
        }
    }

    /**
     * Returns the limit of this mapping, the position <i>after</i> the last byte that
     * is accessible through this mapping
     * @return the absolute position limit of this mapping (exclusive)
     */
    default long limit() {
        return position() + length();//NOTE: also works for NULL_POSITION with zero length
    }

    /**
     * Sets the new limit of this mapping, the position <i>after</i> the last byte that
     * is accessible through this mapping. The new limit must not exceed the mapping beyond the region limit, otherwise
     * an exception is thrown.
     *
     * @param limit the new position limit (exclusive), at least {@code regionStartPosition} and no more than
     *              {@code (regionStartPosition + regionSize)}
     * @throws IllegalArgumentException if the limit is outside the
     *                                  {@code [regionStartPosition..(regionStartPosition + regionSize)]} range
     */
    default void limit(final long limit) {
        final DirectBuffer buffer = buffer();
        final long position = position();
        if (limit != position + buffer.capacity()) {
            validateLimit(position, limit, regionMetrics());
            buffer().wrap(buffer.addressOffset(), (int) (limit - position));
        }
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes. The current mapping length is preserved if
     * possible, or truncated if it would cross into the next region. Delegates to {@link #moveTo(long, int)} if
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
        validatePositionDelta(position, delta);
        final long newPosition = position + delta;
        final RegionMetrics metrics = regionMetrics();
        final int offset = metrics.regionOffset(newPosition);
        final int newLength = Math.min(length(), metrics.regionSize() - offset);
        return moveTo(newPosition, newLength);
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes mapping the specified number of bytes.
     * Delegates to {@link #moveTo(long, int)} if current and resulting position are valid. An exception is thrown if no
     * position is currently mapped, the resulting position is negative or if the specified length crosses region
     * boundaries.
     *
     * @param delta the position delta relative to the current position
     * @param length the number of bytes starting from {@code (position + delta)}, or -1 to span all bytes available
     *      *        from the new position to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position, or the length is
     *                                  negative or crosses region boundaries
     */
    default boolean moveBy(final long delta, final int length) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        return moveTo(position + delta, length);
    }

    /**
     * Moves the mapping to the start of the current region setting the length the whole region.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     */
    default boolean moveToCurrentRegion() {
        final long regionStartPosition = regionStartPosition();
        validatePositionState(regionStartPosition);
        return moveTo(regionStartPosition, regionSize());
    }

    /**
     * Moves the mapping to an offset from the current region, setting the length to reach the region end position.
     *
     * @param offset the position offset relative to the current region start position
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     * @throws IllegalStateException if this mapping has no current position
     */
    default boolean moveToCurrentRegion(final int offset) {
        final long regionStartPosition = regionStartPosition();
        final int regionSize = regionSize();
        validatePositionState(regionStartPosition);
        validateRegionOffset(offset, regionSize);
        return moveTo(regionStartPosition + offset, regionSize - offset);
    }

    /**
     * Moves the mapping to an offset from the current region and maps the specified number of bytes.
     *
     * @param offset the position offset relative to the current region start position
     * @param length the number of bytes starting from {@code (regionStart + offset)}, or -1 to span all bytes available
     *               from offset to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}, or if the length
     *                                  is negative or crosses region boundaries
     */
    default boolean moveToCurrentRegion(final int offset, final int length) {
        final long regionStartPosition = regionStartPosition();
        validatePositionState(regionStartPosition);
        validateRegionOffset(offset, regionMetrics());
        return moveTo(regionStartPosition + offset, length);
    }

    /**
     * Moves the mapping to the start of the first region at position zero setting the length the whole region.
     * <p>
     * This is equivalent to calling {@code moveTo(0, regionSize())}.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    default boolean moveToFirstRegion() {
        return moveTo(0L, regionSize());
    }

    /**
     * Moves the mapping to an offset from the first region, setting the length to reach the region end position.
     *
     * @param offset the position offset within the first region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToFirstRegion(final int offset) {
        final int regionSize = regionSize();
        validateRegionOffset(offset, regionSize);
        return moveTo(offset, regionSize - offset);
    }

    /**
     * Moves the mapping to an offset from the first region and maps the specified number of bytes.
     *
     * @param offset the position offset within the first region, a non-negative value less than region size
     * @param length the number of bytes starting from {@code offset}, or -1 to span all bytes available from offset to
     *               the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}, or if the length
     *                                  is negative or crosses region boundaries
     */
    default boolean moveToFirstRegion(final int offset, final int length) {
        validateRegionOffset(offset, regionMetrics());
        return moveTo(offset, length);
    }

    /**
     * Moves the mapping to the next region start, or to the first region if no region is currently mapped. The mapping
     * length is set to span the whole region.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    default boolean moveToNextRegion() {
        final RegionMetrics metrics = regionMetrics();
        final int regionSize = metrics.regionSize();
        final long regionStartPosition = metrics.regionPosition(position());
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize : 0L;
        return moveTo(nextStartPosition, regionSize);
    }

    /**
     * Moves the mapping to an offset from the next region start position, or from the first region start position if no
     * region is currently mapped. The mapping length is set to reach the region end position.
     *
     * @param offset the position offset within the next region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToNextRegion(final int offset) {
        final int regionSize = regionSize();
        validateRegionOffset(offset, regionSize);
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize : 0;
        return moveTo(nextStartPosition + offset, regionSize - offset);
    }

    /**
     * Moves the mapping to an offset from the next region start position, or from the first region start position if no
     * region is currently mapped.
     *
     * @param offset the position offset within the next region, a non-negative value less than region size
     * @param length the number of bytes starting from {@code (nextRegionStart + offset)}, or -1 to span all bytes
     *               available from offset to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}, or if the length
     *                                  is negative or crosses region boundaries
     */
    default boolean moveToNextRegion(final int offset, final int length) {
        final int regionSize = regionSize();
        validateRegionOffset(offset, regionSize);
        final long regionStartPosition = regionStartPosition();
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + regionSize : 0;
        return moveTo(nextStartPosition + offset, length);
    }

    /**
     * Moves the mapping to the previous region start and sets the length to the whole region. Delegates to
     * {@link #moveTo(long, int)} if there is a previous region, and throws an exception if this region is not currently
     * mapped or if it is the first region.
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
        return moveTo(startPosition - regionSize, regionSize);
    }

    /**
     * Moves the mapping to an offset from the previous region start position, setting the length to reach the region
     * end position. Delegates to {@link #moveTo(long, int)} if there is a previous region, and throws an exception if
     * this region is not currently mapped, if it is the first region or if the offset is invalid.
     *
     * @param offset the position offset within the previous region, a non-negative value less than region size
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position, or if it is at the first region
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}
     */
    default boolean moveToPreviousRegion(final int offset) {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        validateRegionOffset(offset, regionSize);
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return moveTo(startPosition - regionSize + offset, regionSize - offset);
    }

    /**
     * Moves the mapping to an offset from the previous region start position. Delegates to {@link #moveTo(long)} if
     * there is a previous region, and throws an exception if this region is not currently mapped, if it is the first
     * region or if the offset is invalid.
     *
     * @param offset the position offset within the previous region, a non-negative value less than region size
     * @param length the number of bytes starting from {@code (previousRegionStart + offset)}, or -1 to span all bytes
     *               available from offset to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position, or if it is at the first region
     * @throws IllegalArgumentException if the provided offset is not in {@code [0..(regionSize-1)]}, or if the length
     *                                  is negative or crosses region boundaries
     */
    default boolean moveToPreviousRegion(final int offset, final int length) {
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        validateRegionOffset(offset, regionSize);
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region from start position " + startPosition);
        }
        return moveTo(startPosition - regionSize + offset, length);
    }
}
