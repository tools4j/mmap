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


import org.tools4j.mmap.region.impl.DynamicRegionImpl;

/**
 * A dynamic region is a {@link MappedRegion} whose {@link #regionStartPosition() start position} can be changed by
 * {@link #moveTo(long) moving} to another file position.  Moving the region to a new position triggers mapping and
 * unmapping operations if necessary which are performed through a {@link RegionMapper}.
 */
public interface DynamicRegion extends MappedRegion, DynamicMapping {

    static DynamicRegion create(final RegionMapper regionMapper) {
        return new DynamicRegionImpl(regionMapper);
    }

    /**
     * Moves the region to the specified position, mapping (and possibly unmapping) file region blocks if necessary
     *
     * @param position the position to move to, must be a multiple of {@linkplain #regionSize() region size}
     * @return true if the region is ready for data access, and false otherwise
     * @throws IllegalArgumentException if position is negative or not a multiple of {@link #regionSize()}
     */
    boolean moveTo(long position);
}
