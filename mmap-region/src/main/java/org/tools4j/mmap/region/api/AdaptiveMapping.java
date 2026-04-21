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


import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.unsafe.RegionMapper;

import static org.tools4j.mmap.region.impl.Constraints.validateAdaptiveMappingLength;
import static org.tools4j.mmap.region.impl.Constraints.validateLimit;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;

/**
 * An adaptive mapping is a {@link DynamicMapping} that represents an arbitrary slice of the underlying file that lies
 * entirely within a region. It starts at an {@linkplain #regionOffset() offset} from the region's
 * {@linkplain #regionStartPosition() start position} and its end is determined by the mapping
 * {@linkplain #length() length}, a value from zero to no more than the remaining bytes of the region. In other words,
 * an adaptive mapping can map any slice of the file (including zero length slices) as long as no region boundaries are
 * crossed.
 * <p>
 * An adaptive mapping can be re-positioned to any arbitrary file position within the same region, or to any other
 * position from the underlying file. Move operations preserve the existing mapping length if possible, unless a new
 * length is specified at the same time.
 * <p>
 * Moving the mapping to a new position triggers mapping and unmapping operations if necessary which are performed
 * through a {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface AdaptiveMapping extends DynamicMapping {
    /**
     * Moves the mapping to the specified position. The current mapping length is preserved if possible, or truncated at
     * the end of the region if it exceeds the maximum bytes available. If this mapping is not currently mapped, this
     * method will attempt to map the maximum bytes possible.
     * <p>
     * If the new position lies within the region that is already mapped, only the buffer will be adjusted without
     * triggering a region mapping operation. Otherwise, the requested region is mapped (and previous regions possibly
     * unmapped, so it is illegal to continue using buffers wrapped to the previous address).
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     * @see #maxLength()
     */
    @Override
    default boolean moveTo(final long position) {
        final int maxLength = maxLengthAtPosition(position);
        final int newLength = isMapped() ? Math.min(length(), maxLength) : maxLength;
        return moveTo(position, newLength);
    }

    /**
     * Moves the mapping to the specified position and maps the specified number of bytes. If the position lies within
     * the region already mapped, buffer offset and capacity will be adjusted without triggering a region mapping
     * operation. Otherwise, the requested region is mapped.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @param length the new byte length for this mapping, at least zero and no more than {@link #maxLength()}, or -1
     *               to map all bytes to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if position is negative, or length is less than -1 or exceeds the maximum bytes
     *                                  available at the specified position
     * @see #maxLengthAtPosition(long)
     */
    boolean moveTo(long position, int length);

    /**
     * Returns the maximum bytes that can be made available at the current position, or zero if this mapping is not
     * currently mapped. The maximum length is the bytes from the current {@linkplain #regionOffset() region offset} to
     * the end of the region.
     *
     * @return the maximum length that can be passed to {@link #length(int)}, a value between zero and region size
     */
    default int maxLength() {
        return maxLengthAtPosition(position());
    }

    /**
     * Returns the maximum bytes that can be made available at the specified position, or zero if position is negative.
     * The maximum length is the bytes from the {@linkplain RegionMetrics#regionOffset(long) region offset} for the
     * given to position to the end of the region.
     *
     * @param position the position for which the maximum length should be evaluated
     * @return  the maximum length that can be passed to {@link #moveTo(long, int)} for the specified position, a value
     *          between zero and region size
     */
    default int maxLengthAtPosition(final long position) {
        final RegionMetrics metrics = regionMetrics();
        return position >= 0 ? metrics.regionSize() - metrics.regionOffset(position) : 0;
    }

    /**
     * Returns the mapping length in bytes, the same as {@link #bytesAvailable()} and equal to the
     * {@linkplain #buffer() buffer's} {@linkplain DirectBuffer#capacity() capacity}.
     * <p>
     * The length value is at least zero and never more than {@link #regionSize()}. Length can be
     * {@link #length(int) set} directly or can be specified when {@link #moveTo(long, int) moving} to a new position.
     *
     * @return the number of bytes mapped and available via buffer, zero if not {@linkplain #isMapped() mapped}
     */
    default int length() {
        return bytesAvailable();
    }

    /**
     * Sets the new length of this mapping, extending or limiting the bytes currently available through the
     * {@link #buffer() buffer}. If length is -1, the length will be set to span all bytes available to the end of the
     * region currently mapped. If length is less than -1 or exceeds {@link #maxLength()}, an exception is thrown.
     *
     * @param length the new byte length for this mapping, at least zero and no more than {@link #maxLength()}, or -1
     *               to map all bytes available to the end of the region currently mapped
     * @throws IllegalArgumentException if length less than -1 or exceeds max-length
     */
    default void length(final int length) {
        final int newLength;
        if (length == -1) {
            newLength = maxLength();
        } else {
            validateAdaptiveMappingLength(length, maxLength());
            newLength = length;
        }
        final DirectBuffer buffer = buffer();
        if (newLength != buffer.capacity()) {
            buffer.wrap(buffer.addressOffset(), newLength);
        }
    }

    /**
     * Sets the new limit of this mapping, the position <i>after</i> the last byte that
     * is accessible through this mapping. The new limit must not exceed {@code position + max-length)}.
     *
     * @param limit the new position limit (exclusive)
     * @throws IllegalArgumentException if the limit is less than position or if {@code (limit-position)} exceeds
     *                                  {@link #maxLength()}
     * @see #position()
     * @see #maxLength()
     */
    default void limit(final long limit) {
        final long position = position();
        final DirectBuffer buffer = buffer();
        if (limit != position + buffer.capacity()) {
            validateLimit(position, limit, maxLength());
            buffer().wrap(buffer.addressOffset(), (int) (limit - position));
        }
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes. The current mapping length is preserved
     * if possible, or truncated at the end of the (currently or newly) mapped region.
     * <p>
     * Delegates to {@link #moveTo(long)} if current and resulting position are valid. An exception is thrown if no
     * position is currently mapped or if the resulting position is negative.
     * <p>
     * This is equivalent to calling {@code moveTo(position() + delta)}.
     *
     * @param delta the position delta relative to the current position
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position
     */
    @Override
    default boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta, positionStepSize());
        final long newPosition = position + delta;
        return moveTo(newPosition, Math.min(length(), maxLengthAtPosition(newPosition)));
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes mapping the specified number of bytes.
     * Delegates to {@link #moveTo(long, int)} if current and resulting position are valid. An exception is thrown if no
     * position is currently mapped, the resulting position is negative or if the specified length crosses region
     * boundaries.
     * <p>
     * This is equivalent to calling {@code moveTo(position() + delta, length)}.
     *
     * @param delta the position delta relative to the current position
     * @param length the new byte length for this mapping, at least zero and no more than {@link #maxLength()}, or -1
     *               to map all bytes to the end of the region
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position, or the length is
     *                                  negative or exceeds max-length
     */
    default boolean moveBy(final long delta, final int length) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta, positionStepSize());
        return moveTo(position + delta, length);
    }

    /**
     * Moves the mapping to the start of the first region at position zero setting. The current mapping length is
     * preserved.
     * <p>
     * This is equivalent to calling {@code moveTo(0, length())}.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    @Override
    default boolean moveToFirstRegion() {
        return DynamicMapping.super.moveToFirstRegion();
    }

    /**
     * Moves the mapping to the next region start, or to the first region if no region is currently mapped. The current
     * mapping length is preserved.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    @Override
    default boolean moveToNextRegion() {
        return DynamicMapping.super.moveToNextRegion();
    }

    /**
     * Moves the mapping to the previous region start. The current mapping length is preserved. Delegates to
     * {@link #moveTo(long, int)} if there is a previous region, and throws an exception if this region is not currently
     * mapped or if it is the first region.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this region is not currently mapped, or if it is the first region
     */
    @Override
    default boolean moveToPreviousRegion() {
        return DynamicMapping.super.moveToPreviousRegion();
    }
}
