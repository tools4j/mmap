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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionState;

import static java.util.Objects.requireNonNull;

final class DefaultRegion implements MutableRegion {
    private final RegionManager regionManager;
    private final MappingState mappingState;

    DefaultRegion(final RegionManager regionManager, final MappingState mappingState) {
        this.regionManager = requireNonNull(regionManager);
        this.mappingState = requireNonNull(mappingState);
    }

    @Override
    public RegionMapper regionMapper() {
        return regionManager;
    }

    @Override
    public MappingState mappingState() {
        return mappingState;
    }

    @Override
    public RegionState regionState() {
        return mappingState().state();
    }

    @Override
    public AtomicBuffer buffer() {
        return mappingState().buffer();
    }

    @Override
    public long position() {
        return mappingState().position();
    }

    @Override
    public Region map(final long position) {
        return regionManager.mapFrom(position, this);
    }

    @Override
    public void close() {
        if (!isClosed()) {
            //NOTE: we close all regions here -- having some regions open and others closed makes not much sense
            regionManager.close();
        }
    }

    @Override
    public String toString() {
        return "Region:state=" + regionState() +
                "|start=" + regionStartPosition() +
                "|offset=" + offset() +
                "|bytesAvailable=" + bytesAvailable();
    }
}
