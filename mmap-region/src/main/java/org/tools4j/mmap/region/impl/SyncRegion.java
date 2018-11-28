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

import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.MappableRegion;
import org.tools4j.mmap.region.api.Region;

public class SyncRegion implements Region {
    private final Supplier<? extends FileChannel> fileChannelSupplier;
    private final IoMapper ioMapper;
    private final IoUnmapper ioUnmapper;
    private final FileSizeEnsurer fileSizeEnsurer;
    private final FileChannel.MapMode mapMode;
    private final int regionSize;

    private long currentAddress = NULL;
    private long currentPosition = -1;

    public SyncRegion(final Supplier<? extends FileChannel> fileChannelSupplier,
                      final IoMapper ioMapper,
                      final IoUnmapper ioUnmapper,
                      final FileSizeEnsurer fileSizeEnsurer,
                      final FileChannel.MapMode mapMode,
                      final int regionSize) {
        this.fileChannelSupplier = Objects.requireNonNull(fileChannelSupplier);
        this.ioMapper = Objects.requireNonNull(ioMapper);
        this.ioUnmapper = Objects.requireNonNull(ioUnmapper);
        this.fileSizeEnsurer = Objects.requireNonNull(fileSizeEnsurer);
        this.mapMode = Objects.requireNonNull(mapMode);
        this.regionSize = regionSize;
        ensurePowerOfTwo("Region size", regionSize);
    }

    @Override
    public boolean wrap(final long position, final DirectBuffer source) {
        final int regionOffset = (int)(position & (regionSize - 1));
        final long regionStartPosition = position - regionOffset;
        final long address = map(regionStartPosition);
        if (address != MappableRegion.NULL) {
            source.wrap(address + regionOffset, regionSize - regionOffset);
            return true;
        }
        return false;
    }
    @Override
    public long map(final long regionStartPosition) {
        if (regionStartPosition < 0) throw new IllegalArgumentException("Invalid regionStartPosition " + regionStartPosition);

        if (currentPosition == regionStartPosition) {
            return currentAddress;
        }

        if (currentAddress != NULL) {
            ioUnmapper.unmap(fileChannelSupplier.get(), currentAddress, regionSize);
            currentAddress = NULL;
            currentPosition = -1;
        }
        if (fileSizeEnsurer.ensureSize(regionStartPosition + regionSize)) {
            currentAddress = ioMapper.map(fileChannelSupplier.get(), mapMode, regionStartPosition, regionSize);
            currentPosition = regionStartPosition;
            return currentAddress;
        }
        return NULL;
    }

    @Override
    public boolean unmap() {
        if (currentAddress != NULL) {
            ioUnmapper.unmap(fileChannelSupplier.get(), currentAddress, regionSize);
            currentAddress = NULL;
            currentPosition = -1;
        }
        return true;
    }

    @Override
    public int size() {
        return regionSize;
    }

    static void ensurePowerOfTwo(final String name, final int value) {
        if ((value & (value - 1)) != 0) {
            //true ony for 0 and non-powers of true
            throw new IllegalArgumentException(name + " must be non zero and a power of two: " + value);
        }
    }}
