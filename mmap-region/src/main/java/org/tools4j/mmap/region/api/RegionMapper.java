/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
 * Interface offering operations to map or unmap a region.
 */
public interface RegionMapper {
    long NULL = 0;

    /**
     * Attempts to map a region.
     * In synchronous implementations, it is expected to be mapped immediately,
     *    if not mapped yet, and true is returned.
     * In asynchronous implementations, if the region is not mapped yet, mapping
     *    will be performed asynchronously and false will be returned.
     *
     * @param regionStartPosition - start currentPosition of a region, must be power of two and aligned with
     *                            the length of the region.
     * @return the start currentAddress of the mapped region, or NULL if not mapped (yet)
     */
    long map(final long regionStartPosition);

    /**
     * Attempts to unmap a region.
     * In synchronous implementations, it is expected to be unmapped immediately,
     *    if not unmapped yet.
     * In asynchronous implementations, if the region is not unmapped yet, mapping
     *    will be performed asynchronously and false will be returned.
     *
     * @return true if the region is unmapped after the call, otherwise - false.
     */
    boolean unmap();

    /**
     * Returns the total size of the region.
     *
     * @return the region size
     */
    int size();
}
