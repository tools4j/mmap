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
package org.tools4j.mmap.mapping.api;

import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.mapping.impl.DynamicMappings;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;

import java.io.UncheckedIOException;
import java.util.function.Predicate;

import static org.tools4j.mmap.mapping.impl.MappingValidations.validatePositionDelta;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validatePositionState;

/**
 * A dynamic mapping is a {@link Mapping} of a slice of the underlying file whose {@linkplain #position() position} and
 * {@linkplain #length() length} can be changed by {@link #moveTo(long, int) moving} it to another slice of the file.
 * <p>
 * The file is mapped into memory in regions of a predefined {@linkplain RegionMetrics#regionSize() region size} (see
 * {@link #regionMetrics()}) through a {@link RegionMapper}, mapping and unmapping regions as the mapping moves. A slice
 * can therefore not cross the end of the region its position lies in: its length is at least zero and at most the {@linkplain #maxLengthAtPosition(long)
 * max length} at its position, the bytes from the position to the end of its region. Moving within the region that is
 * already mapped only adjusts the {@link #buffer() buffer}.
 * <p>
 * The mapping keeps the <i>requested length</i>, the length last passed to a move, {@link #length(int)} or
 * {@link #limit(long)}, and moves without a length map it again at the new position: {@link #STRETCH_LENGTH}, the
 * initial requested length, stretches to the end of the region at every position, and an explicit length maps exactly
 * that many bytes, for instance to read values of the same type at successive positions without passing the length
 * each time. A move throws an {@link IllegalArgumentException} if the requested length exceeds the max length at the
 * new position, and leaves the mapping as it was.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface DynamicMapping extends Mapping {
    /**
     * Length passed to move methods or {@link #length(int)} to map all bytes from the position to the end of its
     * region at every move, the initial requested length of a mapping
     */
    int STRETCH_LENGTH = -1;

    /**
     * Returns the region metrics of this mapping, with the region size the file is mapped in and the operations for
     * positions related to it.
     *
     * @return the region metrics of this mapping
     */
    RegionMetrics regionMetrics();

    /**
     * Moves the mapping to the specified position, mapping the requested length: all bytes to the end of the region
     * for {@link #STRETCH_LENGTH}, otherwise the length last requested.
     * <p>
     * If the new position lies within the region that is already mapped, only the buffer is adjusted without mapping
     * a region. Otherwise, the requested region is mapped, and the previous region possibly unmapped, so that it is
     * illegal to continue using buffers wrapped to the previous address.
     * <p>
     * Returns false if the region is not available yet, for instance because a read-only file has not been extended
     * to this position yet; a later move to the same position may succeed. If mapping fails with an error, the
     * exception is thrown. Either way, the mapping remains at its previous position.
     *
     * @param position the position to move to (absolute, not relative to the current position or region start)
     * @return true if the mapping is ready for data access, and false if the region is not available yet
     * @throws IllegalArgumentException if position is negative or beyond the max file size of a non-rolling file, or
     *                                  if the requested length exceeds the {@linkplain #maxLengthAtPosition(long) max
     *                                  length} at the position
     * @throws IllegalStateException if mapping fails, for instance if the max file size is exceeded
     * @throws UncheckedIOException if mapping fails with an I/O error
     */
    boolean moveTo(long position);

    /**
     * Moves the mapping to the specified position and maps the specified number of bytes, which becomes the requested
     * length of later moves, like {@link #moveTo(long)} otherwise.
     *
     * @param position the position to move to (absolute, not relative to the current position or region start)
     * @param length   the new length of this mapping, at least zero and no more than the
     *                 {@linkplain #maxLengthAtPosition(long) max length} at the position, or {@link #STRETCH_LENGTH}
     * @return true if the mapping is ready for data access, and false if the region is not available yet
     * @throws IllegalArgumentException if position is negative or beyond the max file size of a non-rolling file, or
     *                                  if length is less than -1 or exceeds the max length at the position
     * @throws IllegalStateException if mapping fails, for instance if the max file size is exceeded
     * @throws UncheckedIOException if mapping fails with an I/O error
     */
    boolean moveTo(long position, int length);

    /**
     * Moves the mapping forward or backward by the specified delta in bytes, mapping the requested length like
     * {@link #moveTo(long)}.
     * <p>
     * This is equivalent to calling {@code moveTo(position() + delta)}.
     *
     * @param delta the position delta relative to the current position
     * @return true if the mapping is ready for data access, and false if the region is not available yet
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position, or if the requested
     *                                  length exceeds the max length at the new position
     */
    default boolean moveBy(final long delta) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        return moveTo(position + delta);
    }

    /**
     * Moves the mapping forward or backward by the specified delta in bytes and maps the specified number of bytes,
     * which becomes the requested length of later moves.
     * <p>
     * This is equivalent to calling {@code moveTo(position() + delta, length)}.
     *
     * @param delta  the position delta relative to the current position
     * @param length the new length of this mapping, at least zero and no more than the
     *               {@linkplain #maxLengthAtPosition(long) max length} at the new position, or {@link #STRETCH_LENGTH}
     * @return true if the mapping is ready for data access, and false if the region is not available yet
     * @throws IllegalStateException if this mapping has no current position
     * @throws IllegalArgumentException if the provided delta value results in a negative position, or the length is
     *                                  less than -1 or exceeds the max length at the new position
     */
    default boolean moveBy(final long delta, final int length) {
        final long position = position();
        validatePositionState(position);
        validatePositionDelta(position, delta);
        return moveTo(position + delta, length);
    }

    /**
     * Returns the max length at the current position, or zero if this mapping is not mapped: the bytes from the
     * current position to the end of its region.
     *
     * @return the max length that can be passed to {@link #length(int)}, a value between zero and region size
     */
    default int maxLengthAtCurrentPosition() {
        return isMapped() ? maxLengthAtPosition(position()) : 0;
    }

    /**
     * Returns the max length at the specified position, or zero if position is negative: the bytes from the position
     * to the end of its region.
     *
     * @param position the position for which the max length is evaluated
     * @return the max length that can be passed to {@link #moveTo(long, int)} for the specified position, a value
     *         between zero and region size
     */
    default int maxLengthAtPosition(final long position) {
        final RegionMetrics metrics = regionMetrics();
        return position >= 0 ? metrics.regionSize() - metrics.regionOffset(position) : 0;
    }

    /**
     * Returns the length of this mapping in bytes, the same as {@link #bytesAvailable()} and equal to the
     * {@linkplain #buffer() buffer's} {@linkplain MemoryBuffer#capacity() capacity}.
     *
     * @return the number of bytes mapped and available through the buffer, zero if not {@linkplain #isMapped() mapped}
     */
    default int length() {
        return bytesAvailable();
    }

    /**
     * Sets the length of this mapping, extending or limiting the bytes available through the {@link #buffer() buffer}
     * at the current position; the length becomes the requested length of later moves.
     *
     * @param length the new length of this mapping, at least zero and no more than the
     *               {@linkplain #maxLengthAtCurrentPosition() max length at the current position}, or
     *               {@link #STRETCH_LENGTH}
     * @throws IllegalArgumentException if length is less than -1 or exceeds the max length at the current position
     */
    void length(int length);

    /**
     * Sets the limit of this mapping, the position <i>after</i> the last byte that is accessible through this mapping,
     * not exceeding the position plus the {@linkplain #maxLengthAtCurrentPosition() max length at the current
     * position}; the resulting length becomes the requested length of later moves.
     *
     * @param limit the new position limit (exclusive)
     * @throws IllegalArgumentException if the limit is less than the position, or if {@code (limit - position)}
     *                                  exceeds the max length at the current position
     * @see #position()
     */
    void limit(long limit);

    /**
     * Performs a linear search for the last position that still results in a match given the specified matcher. Returns
     * true if a match is found, and leaves the mapping at the matching position. If no match is found, false is
     * returned and the mapping is left at the position last tried.
     *
     * @param startPosition     the first position to test
     * @param positionIncrement the positive increment added to position at every step
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
     * @param positionIncrement the positive increment added to position at every step
     * @param matcher           the matcher to evaluate whether the position data matches the desired search criteria
     * @return true if a match is found, and false otherwise
     */
    default boolean binarySearchLast(final long startPosition,
                                     final long positionIncrement,
                                     final Predicate<? super DynamicMapping> matcher) {
        return DynamicMappings.binarySearchLast(this, startPosition, positionIncrement, matcher);
    }
}
