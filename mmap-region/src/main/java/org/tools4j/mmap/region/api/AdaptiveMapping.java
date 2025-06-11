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

import static org.tools4j.mmap.region.impl.Constraints.validateLength;
import static org.tools4j.mmap.region.impl.Constraints.validateLimit;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionDelta;
import static org.tools4j.mmap.region.impl.Constraints.validatePositionState;

/**
 * An adaptive mapping is a {@link DynamicMapping} that represents an arbitrary slice of the underlying file. It starts
 * at an {@linkplain #offset() offset} from the region's {@linkplain #regionStartPosition() start position} and its end
 * is determined by the mapping {@linkplain #length() length}.
 * <p>
 * Adaptive mappings can span across region boundaries. This is achieved by mapping blocks into memory that are slightly
 * larger than the region size if necessary. The {@link #regionMappingSize() region-mapping-size} and the
 * {@link #positionGranularity() position-granularity} also determine the {@link #maxLength() max-length} value, the
 * ceiling for the number of bytes that can be mapped at any point time. In return adaptive mappings can be moved around
 * in arbitrary ways to any position in the file, while preserving or changing the mapping length at the same time.
 * <p>
 * Moving the region to a new position triggers mapping and unmapping operations if necessary which are performed
 * through a {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface AdaptiveMapping extends DynamicMapping {
    /**
     * Moves the mapping to the specified position. The current mapping length is preserved.
     * <p>
     * If the new position lies within the region that is already mapped, only the buffer will be adjusted without
     * triggering a region mapping operation. Otherwise, the requested region is mapped (and previous regions possibly
     * unmapped, so it is illegal to continue using buffers wrapped to the previous address).
     * <p>
     * This is equivalent to calling {@code moveTo(position, length())}.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative
     */
    @Override
    default boolean moveTo(final long position) {
        return moveTo(position, length());
    }

    /**
     * Moves the mapping to the specified position and maps the specified number of bytes. If the position lies within
     * the region already mapped, buffer offset and capacity will be adjusted without triggering a region mapping
     * operation. Otherwise, the requested region is mapped.
     *
     * @param position the position to move to (absolute, not relative to current position or region start)
     * @param length the number of bytes starting from {@code position}, no more than {@link #maxLength()}
     * @return true if the mapping is ready for data access, and false otherwise
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if position or length is negative, or the length crosses region boundaries
     */
    boolean moveTo(long position, int length);

    /**
     * Returns the maximum length that can be used for this adaptive mapping
     * @return the maximum allowed number of bytes that can be mapped
     */
    default int maxLength() {
        return regionMappingSize() - regionSize() + positionGranularity();
    }

    /**
     * Returns the mapping length in bytes, the same as {@link #bytesAvailable()} and equal to the
     * {@linkplain #buffer() buffer's} {@linkplain DirectBuffer#capacity() capacity}. The returned value is at least
     * zero and never more than {@link #maxLength()}.
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
        if (length != buffer.capacity()) {
            validateLength(length, maxLength());
            buffer.wrap(buffer.addressOffset(), length);
        }
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
        final long position = position();
        final DirectBuffer buffer = buffer();
        if (limit != position + buffer.capacity()) {
            validateLimit(position, limit, maxLength());
            buffer().wrap(buffer.addressOffset(), (int) (limit - position));
        }
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes. The current mapping length is preserved if
     * possible, or truncated if it would cross into the next region. Delegates to {@link #moveTo(long, int)} if
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
        validatePositionDelta(position, delta, positionGranularity());
        return moveTo(position + delta);
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
        validatePositionDelta(position, delta, positionGranularity());
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
        return moveTo(0L);
    }

    /**
     * Moves the mapping to the next region start, or to the first region if no region is currently mapped. The current
     * mapping length is preserved.
     *
     * @return true if the mapping is ready for data access, and false otherwise
     */
    @Override
    default boolean moveToNextRegion() {
        final RegionMetrics metrics = regionMetrics();
        final long regionStartPosition = metrics.regionPosition(position());
        final long nextStartPosition = regionStartPosition >= 0 ? regionStartPosition + metrics.regionSize() : 0L;
        return moveTo(nextStartPosition);
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
        final long startPosition = regionStartPosition();
        final int regionSize = regionSize();
        if (startPosition < regionSize) {
            throw new IllegalStateException("There is no previous region before position " + position());
        }
        return moveTo(startPosition - regionSize);
    }
}
