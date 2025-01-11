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


/**
 * A region mapping is a {@link Mapping} whose size follows a standard {@linkplain #regionSize() region size}, typically
 * powers of two. The file block of the size of a region is directly mapped into memory. The file data is accessible
 * through the {@link #buffer()}.
 */
public interface RegionMapping extends Mapping {

    /**
     * Returns the start position of the mapping, or {@link NullValues#NULL_POSITION NULL_POSITION} if this mapping is
     * not {@link #isMapped() mapped}.
     * <p>
     * If mapped, the position is always equal to the
     * {@linkplain #regionStartPosition() region start position} plus the {@linkplain #offset() offset}.
     *
     * @return the mapped position, or -1 if unavailable
     */
    @Override
    default long position() {
        return regionStartPosition() + offset();
    }

    /**
     * Returns the buffer's offset from the {@linkplain #regionStartPosition() region start position}, a value between
     * zero and (regionSize - 1)
     * @return {@code position - regionStartPosition}
     */
    int offset();

    /**
     * @return the size of a mappable memory region in bytes
     * @see #regionMetrics()
     */
    default int regionSize() {
        return regionMetrics().regionSize();
    }

    /**
     * @return the region metrics determined by the {@linkplain #regionSize() region size}
     */
    RegionMetrics regionMetrics();

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.
     * <p>
     * If mapped, the region start position is always equal to the
     * {@linkplain #position() position} minus the {@linkplain #offset() offset}.
     *
     * @return the region's start position, equal to the largest region size multiple that is less or equal to the
     *         current position, or -1 if unavailable
     */
    default long regionStartPosition() {
        return regionMetrics().regionPosition(position());
    }

    /**
     * Closes this mapped region.
     */
    @Override
    void close();
}
