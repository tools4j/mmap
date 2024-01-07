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

/**
 * Interface with method to map a region.  Subsequent mappings can be achieved directly through one of the
 * {@link Region}'s {@code map(..)} methods.
 */
public interface RegionMapper extends AutoCloseable {
    /**
     * Returns the metrics of regions mapped by this region mapper.
     * @return metrics of regions mapped by this class
     */
    RegionMetrics regionMetrics();

    /**
     * Maps a region given the absolute byte position. If the position is not a multiple of region size, the region's
     * {@linkplain Region#buffer() buffer} will be wrapped at an according offset.
     * <p>
     * Mapping can be performed synchronously or asynchronously; readiness for data access can be checked through
     * {@link Region#isReady()}.
     *
     * @param position absolute start position, does not have to be a multiple of region size
     * @return the region, guaranteed to be immediately mapped if synchronous mapping is used
     */
    Region map(long position);

    /**
     * Returns true if this region mapper performs asynchronous mapping in the background, and false if mappings are
     * performed synchronously.
     * @return true if this mapper performs asynchronous mapping, and false if it performs synchronous mapping
     */
    boolean isAsync();

    /**
     * Closes all regions that have been mapped directly or indirectly mapped through this region mapper
     */
    @Override
    void close();
}
