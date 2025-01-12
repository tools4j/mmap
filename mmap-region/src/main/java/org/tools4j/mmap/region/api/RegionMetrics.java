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
 * The region metrics are defined by the region size, and values such as region start position, offset and index can be
 * derived from an arbitrary byte position value.
 */
public interface RegionMetrics {
    /**
     * @return the size of a mappable memory region in bytes
     */
    int regionSize();

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
}
