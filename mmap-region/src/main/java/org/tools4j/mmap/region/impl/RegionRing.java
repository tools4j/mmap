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
package org.tools4j.mmap.region.impl;

import java.util.Objects;

import org.agrona.DirectBuffer;

import org.tools4j.mmap.region.api.AccessibleRegion;
import org.tools4j.mmap.region.api.Region;

import static org.tools4j.mmap.region.impl.SyncRegion.ensurePowerOfTwo;

public class RegionRing implements AccessibleRegion, AutoCloseable {
    private final Region[] regions;
    private final int regionSize;
    private final int regionsToMapAhead;
    private final int regionsLengthMask;

    private long currentAbsoluteIndex = -1;

    public RegionRing(final Region[] regions, final int regionsToMapAhead) {
        if (regions.length == 0) {
            throw new IllegalArgumentException("Empty region array");
        }
        ensurePowerOfTwo("Region length", regions.length);
        if (regionsToMapAhead > regions.length) {
            throw new IllegalArgumentException("Regions to map ahead is larger than regions: " + regionsToMapAhead +
                    " > " + regions.length);
        }
        for (int i = 0; i < regions.length; i++) {
            final Region r = Objects.requireNonNull(regions[i]);
            if (i == 0) {
                ensurePowerOfTwo("Region size", r.size());
            } else {
                if (r.size() != regions[0].size()) {
                    throw new IllegalArgumentException("Incompatible region sizes, all sizes must be " +
                            regions[0].size() + " but also found " + r.size());
                }
            }
        }
        this.regions = regions;
        this.regionSize = regions[0].size();
        this.regionsToMapAhead = regionsToMapAhead;
        this.regionsLengthMask = regions.length - 1;
     }

    @Override
    public boolean wrap(final long position, final DirectBuffer buffer) {
        final long absoluteIndex = position / regionSize;

        final boolean wrapped = regions[(int) (absoluteIndex & regionsLengthMask)].wrap(position, buffer);
        if (wrapped) {
            if (currentAbsoluteIndex < absoluteIndex) { // moving forward
                for (long mapIndex = absoluteIndex + 1; mapIndex <= absoluteIndex + regionsToMapAhead; mapIndex++) {
                    regions[(int) (mapIndex & regionsLengthMask)].map(mapIndex * regionSize);
                }
                if (currentAbsoluteIndex >= 0) regions[(int) (currentAbsoluteIndex & regionsLengthMask)].unmap();
            } else if (currentAbsoluteIndex > absoluteIndex) { // moving backward
                for (long mapIndex = absoluteIndex - 1; mapIndex >= 0 && mapIndex >= absoluteIndex - regionsToMapAhead; mapIndex--) {
                    regions[(int) (mapIndex & regionsLengthMask)].map(mapIndex * regionSize);
                }
                if (currentAbsoluteIndex >= 0) regions[(int) (currentAbsoluteIndex & regionsLengthMask)].unmap();
            }
        }
        currentAbsoluteIndex = absoluteIndex;
        return wrapped;
    }

    @Override
    public int size() {
        return regionSize;
    }

    @Override
    public void close() {
        for (final Region region : regions) {
            region.close();
        }
    }
}
