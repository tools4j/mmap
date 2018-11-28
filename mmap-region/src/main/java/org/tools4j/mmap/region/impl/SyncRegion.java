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
import org.tools4j.mmap.region.api.Region;

public class SyncRegion implements Region {
    private static final long NULL = -1;

    private final Supplier<? extends FileChannel> fileChannelSupplier;
    private final IoMapper ioMapper;
    private final IoUnmapper ioUnmapper;
    private final FileSizeEnsurer fileSizeEnsurer;
    private final FileChannel.MapMode mapMode;
    private final int length;

    private long currentPosition = NULL;
    private long currentAddress = NULL;

    public SyncRegion(final Supplier<? extends FileChannel> fileChannelSupplier,
                      final IoMapper ioMapper,
                      final IoUnmapper ioUnmapper,
                      final FileSizeEnsurer fileSizeEnsurer,
                      final FileChannel.MapMode mapMode,
                      final int length) {
        this.fileChannelSupplier = Objects.requireNonNull(fileChannelSupplier);
        this.ioMapper = Objects.requireNonNull(ioMapper);
        this.ioUnmapper = Objects.requireNonNull(ioUnmapper);
        this.fileSizeEnsurer = Objects.requireNonNull(fileSizeEnsurer);
        this.mapMode = Objects.requireNonNull(mapMode);
        this.length = length;
    }

    @Override
    public boolean wrap(final long position, final DirectBuffer source) {
        final int regionOffset = (int) (position & (this.length - 1));
        final long regionStartPosition = position - regionOffset;
        if (map(regionStartPosition)) {
            source.wrap(currentAddress + regionOffset, this.length - regionOffset);
            return true;
        }
        return false;
    }

    @Override
    public boolean map(final long regionStartPosition) {
        if (regionStartPosition < 0) throw new IllegalArgumentException("Invalid regionStartPosition " + regionStartPosition);

        if (currentPosition == regionStartPosition) return true;

        if (currentAddress != NULL) {
            ioUnmapper.unmap(fileChannelSupplier.get(), currentAddress, length);
            currentAddress = NULL;
        }
        if (fileSizeEnsurer.ensureSize(regionStartPosition + length)) {
            currentAddress = ioMapper.map(fileChannelSupplier.get(), mapMode, regionStartPosition, length);
            currentPosition = regionStartPosition;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean unmap() {
        if (currentAddress != NULL) {
            ioUnmapper.unmap(fileChannelSupplier.get(), currentAddress, length);
            currentAddress = NULL;
            currentPosition = NULL;
        }
        return true;
    }

    @Override
    public void close() {
        unmap();
    }

    @Override
    public int size() {
        return length;
    }

}
