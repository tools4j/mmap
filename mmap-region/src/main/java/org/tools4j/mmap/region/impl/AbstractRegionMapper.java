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

import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.RegionMapper;

abstract class AbstractRegionMapper implements RegionMapper {
    protected final Supplier<? extends FileChannel> fileChannelSupplier;
    protected final FileSizeEnsurer fileSizeEnsurer;
    protected final FileChannel.MapMode mapMode;
    protected final int regionSize;

    protected long currentPosition = -1;
    protected long currentAddress = RegionMapper.NULL;

    public AbstractRegionMapper(final int regionSize,
                                final Supplier<? extends FileChannel> fileChannelSupplier,
                                final FileSizeEnsurer fileSizeEnsurer,
                                final FileChannel.MapMode mapMode) {
        this.fileChannelSupplier = Objects.requireNonNull(fileChannelSupplier);
        this.fileSizeEnsurer = Objects.requireNonNull(fileSizeEnsurer);
        this.mapMode = Objects.requireNonNull(mapMode);
        this.regionSize = regionSize;
    }

    @Override
    public int size() {
        return regionSize;
    }
}
