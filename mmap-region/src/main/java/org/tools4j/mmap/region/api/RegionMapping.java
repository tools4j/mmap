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
package org.tools4j.mmap.region.api;


import org.tools4j.mmap.region.unsafe.RegionMapper;

/**
 * A region mapping is a {@link DynamicMapping} that always maps a whole region. As a consequence, move operations are
 * only permitted to positions that are multiples the {@linkplain #regionSize() region size}.
 * <p>
 * Moving to a new position triggers mapping and unmapping operations if necessary which are performed through a
 * {@link RegionMapper}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 */
public interface RegionMapping extends DynamicMapping {
    /**
     * Returns the buffer's offset from the {@linkplain #regionStartPosition() region start position}, which is always
     * zero for a <code>RegionPosition</code>.
     *
     * @return the offset from the region start position, always zero for a region position
     */
    default int regionOffset() {
        return 0;
    }

    /**
     * The step size (or minimum increment) of position values passed to the moveTo(long) method, which is the same as
     * {@link #regionSize()} for a <code>RegionPosition</code>.
     *
     * @return the step size for position values, same as region size
     */
    @Override
    default int positionStepSize() {
        return regionSize();
    }
}
