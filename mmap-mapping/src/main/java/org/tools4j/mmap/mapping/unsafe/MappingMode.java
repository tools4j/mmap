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

import org.tools4j.mmap.core.unsafe.Unsafe;

/**
 * How a {@link FileMapper} or {@link RegionMapper} maps a region: whether it may create or extend a file to do so, and
 * whether it touches the mapped pages. Read-only mappers ignore it, as they map only what exists and never touch.
 */
@Unsafe
public enum MappingMode {
    /**
     * Maps the region only if it exists already: if the file does not exist or is not long enough, or the region is
     * beyond the mapping limit, mapping yields {@code NULL_ADDRESS} rather than creating or extending the file, as for
     * a read-only mapper.
     */
    MAP_IF_EXISTS,
    /** Maps the region, creating or extending the file if necessary */
    MAP_OR_CREATE,
    /**
     * Maps the region like {@link #MAP_OR_CREATE}, and then touches every page the first time a position is mapped,
     * so that it faults in writable and its file blocks are allocated; to be used only when mapping ahead on a thread
     * other than the one using the region, as touching on the using thread only bunches the page faults it would take
     * anyway into one stall.
     */
    MAP_AND_TOUCH
}
