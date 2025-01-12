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
package org.tools4j.mmap.region.unsafe;

import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Mapping;
import org.tools4j.mmap.region.api.Unsafe;

/**
 * Low level API to map regions with implementation dependant optimisations such as unmapping, caching and pre-mapping
 * of regions. All mapped regions have the same {@linkplain #regionSize() size}.
 * <p>
 * <br>
 * Applications should not use this class directly and instead use any of the {@link Mapping} implementations.
 * <p>
 * <br>
 * <b>Important notes:</b><ul>
 *     <li>A region is mapped through {@link #map(long)} which returns the mapped memory address</li>
 *     <li>A mapped address is only valid until the next map invocation is made; after that continuing to use the
 *         address may result in a JVM crash.
 *     </li>
 *     <li>All mapped regions are closed through {@link #close()} and continuing to use any mapped address thereafter
 *         may result in a JVM crash.
 *     </li>
 * </ul>
 */
@Unsafe
public interface RegionMapper extends AutoCloseable {
    /**
     * @return the file access mode used by this region mapper
     */
    default AccessMode accessMode() {
        return fileMapper().accessMode();
    }

    FileMapper fileMapper();

    /**
     * Returns the size of a mapped region.
     * @return region size in bytes
     */
    int regionSize();

    /**
     * Attempts to map the buffer at the specified position and returns the address at which the region was mapped.
     *
     * @param position      the requested position, a non-negative value and a multiple of region size
     * @return  the address at which the region was mapped, or zero if mapping failed for instance because the file to
     *          map does not exist yet
     * @throws IllegalArgumentException if position is negative or not a multiple of region size
     * @see #regionSize()
     */
    long map(long position);


    /** @return true if this mapper is closed*/
    boolean isClosed();

    /**
     * Closes this region mapper and issued mapping.
     */
    void close();
}
