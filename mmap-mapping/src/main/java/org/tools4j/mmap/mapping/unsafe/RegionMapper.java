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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.core.base.Closeable;
import org.tools4j.mmap.core.base.OverrideAll;
import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Mapping;
import org.tools4j.mmap.mapping.api.RegionMetrics;

import java.io.UncheckedIOException;

import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateAddress;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateRegionPosition;

/**
 * Low level API to map regions with implementation dependant optimisations such as unmapping, caching and pre-mapping
 * of regions. All regions have the same {@linkplain RegionMetrics#regionSize() size}, and are mapped with the
 * {@linkplain RegionMetrics#mappingSize() mapping size}, the region size plus the
 * {@linkplain RegionMetrics#overlapSize() overlap size}.
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
@OverrideAll
public interface RegionMapper extends Closeable {
    AccessMode accessMode();

    /**
     * Returns the region metrics with the region size and overlap size the regions are mapped with.
     *
     * @return the region metrics of this mapper
     */
    RegionMetrics regionMetrics();

    /**
     * Attempts to map the region at the specified position with its {@linkplain RegionMetrics#mappingSize()
     * mapping size}, and returns the address at which the region was mapped.
     *
     * @param position      the requested position, a non-negative value and a multiple of region size
     * @return  the address at which the region was mapped, or {@code NULL_ADDRESS} if the region is not available yet,
     *          for instance because the file to map does not exist yet
     * @throws IllegalArgumentException if position is negative or not a multiple of region size
     * @throws IllegalStateException if this mapper is closed, or if mapping fails, see {@link FileMapper#map(long, int, boolean)}
     * @throws UncheckedIOException if mapping fails with an I/O error
     * @see #regionMetrics()
     */
    default long map(final long position) {
        final RegionMetrics metrics = regionMetrics();
        validateRegionPosition(position, metrics.regionSize());
        validateNotClosed(this);
        return mapInternal(position, metrics.mappingSize());
    }

    long mapInternal(long position, int mappingSize);

    /**
     * Unmaps previously mapped address of the region starting at absolute position, mapped with its
     * {@linkplain RegionMetrics#mappingSize() mapping size}.
     *
     * @param position absolute position
     * @param address  previously mapped address
     */
    default void unmap(final long position, final long address) {
        final RegionMetrics metrics = regionMetrics();
        validateRegionPosition(position, metrics.regionSize());
        validateAddress(address);
        validateNotClosed(this);
        unmapInternal(position, address, metrics.mappingSize());
    }

    void unmapInternal(long position, long address, int mappingSize);

    boolean isMappedInCache(long position);

    /**
     * Returns whether this mapper is closed.
     *
     * @return true if this mapper is closed
     */
    @Override
    boolean isClosed();

    /**
     * Closes this region mapper and issued mapping.
     */
    @Override
    void close();
}
