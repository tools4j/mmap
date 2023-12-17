/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;

/**
 * Extension of {@link RegionMapper} with map operations to unmap and re-map a region if necessary.
 */
interface RegionManager extends RegionMapper {
    /**
     * Same as {@link #map(long)} but with additional information of previous region from which the map request
     * originated.  Region mapper implementations may use this information to predict the mapping direction and pre-map
     * additional pages other than the one requested here.
     *
     * @param position start position of the region, or of viewport within the region if not aligned with region size
     * @param from the region from which the map request was initiated
     * @return the region, guaranteed to be immediately mapped for synchronous region
     */
    Region mapFrom(long position, MutableRegion from);
}
