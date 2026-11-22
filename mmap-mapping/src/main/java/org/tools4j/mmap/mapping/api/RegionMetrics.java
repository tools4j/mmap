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


/**
 * Region metrics define the geometry of the regions a file is mapped in, and operations for file positions related to
 * the region size. It provides methods to calculate region start position, offset from the region start and logical
 * region index.
 * <p>
 * Every region is mapped with an {@linkplain #overlapSize() overlap} beyond its end, the first bytes of the next
 * region mapped a second time, so that contiguous bytes beyond the region end are accessible through the mapping of the
 * region a position lies in. This includes the last region of a file, whose overlap lies beyond the max file size: files
 * are physically larger than their max size by the overlap size. Positions, offsets and indices relate to the region
 * size only.
 */
public interface RegionMetrics {
    /**
     * Returns the region size, the number of bytes from one region start position to the next.
     *
     * @return the region size in bytes, a power of two
     */
    int regionSize();

    /**
     * Returns the overlap size, the number of bytes mapped beyond the end of every region. They are the first bytes of
     * the next region, mapped a second time, or for the last region of a file bytes beyond its max size.
     *
     * @return the overlap size in bytes, at least zero and at most the {@linkplain #regionSize() region size}
     */
    int overlapSize();

    /**
     * Returns the mapping size, the number of bytes mapped for every region from its start position: the
     * {@linkplain #regionSize() region size} plus the {@linkplain #overlapSize() overlap size}.
     *
     * @return the mapping size in bytes, region size plus overlap size
     */
    int mappingSize();

    /**
     * Returns the max length at the specified position: the bytes from the position to the end of its region's
     * overlap, that is, the {@linkplain #mappingSize() mapping size} minus the {@linkplain #regionOffset(long) region
     * offset} of the position.
     * <p>
     * The position is not validated, see {@link DynamicMapping#maxLengthAtPosition(long)} for a variant accepting any
     * position.
     *
     * @param position non-negative position
     * @return the max length at the specified position, a value between overlap size plus one and the mapping size
     */
    int maxLengthAtPosition(long position);

    /**
     * Returns the safe length for positions that are a multiple of the given alignment: the length that fits at every
     * such position, since its offset within its region is at most region size minus alignment. This is the
     * {@linkplain #overlapSize() overlap size} plus alignment.
     * <p>
     * The alignment is not validated, see {@link DynamicMapping#safeLength(int)} for a validating variant.
     *
     * @param alignment the alignment of positions, a power of two no greater than the region size
     * @return the length that fits at every position that is a multiple of alignment
     */
    int safeLength(int alignment);

    /**
     * Returns the region byte offset given the absolute byte position.
     *
     * @param position non-negative region position value, a zero-based memory byte addresses
     * @return the byte offset within a region given the absolute byte position
     */
    int regionOffset(long position);

    /**
     * Returns the region start position given an arbitrary position. A region start position is a multiple of the
     * {@linkplain #regionSize() region size}.
     *
     * @param position non-negative region position value, a zero-based memory byte addresses
     * @return the region start position, a multiple of the {@linkplain #regionSize() region size}
     */
    long regionPosition(long position);

    /**
     * Returns the region index given the absolute byte position. Region index is a zero based region enumerator, and
     * each region contains {@code n = regionSize()} bytes.
     *
     * @param position non-negative region position value, a zero-based memory byte addresses
     * @return the zero-based region index
     */
    long regionIndex(long position);

    /**
     * Returns the region start position given a region index.
     *
     * @param index non-negative region index
     * @return the region start position, a multiple of the {@linkplain #regionSize() region size}
     */
    long regionPositionByIndex(long index);
}
