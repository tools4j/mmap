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


import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.FixedRegion;
import org.tools4j.mmap.region.impl.FixedSizeFileMapper;

import java.io.File;

/**
 * A mapped region is a file block directly mapped into memory. The file data is accessible through the
 * {@link #buffer()}.
 */
public interface MappedRegion extends Mapping {

    static MappedRegion createFixed(final File file,
                                    final int fileSize,
                                    final MapMode mapMode) {
        final FileInitialiser initialiser = FileInitialiser.zeroBytes(mapMode, fileSize);
        return createFixed(new FixedSizeFileMapper(file, fileSize, mapMode, initialiser), true);
    }

    static MappedRegion createFixed(final FixedSizeFileMapper fileMapper, final boolean closeFileMapperOnClose) {
        return new FixedRegion(fileMapper, closeFileMapperOnClose);
    }

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.
     *
     * @return the region's start position, a multiple of region size, or -1 if unavailable
     */
    @Override
    long position();

    /**
     * Returns the start position of the region, a multiple of the {@linkplain #regionSize() region size}, or
     * {@link NullValues#NULL_POSITION NULL_POSITION} if no position has been mapped yet.
     * <p>
     * For a mapped region this is always equal to the mapped {@link #position()}
     *
     * @return the region's start position, a multiple of region size, or -1 if unavailable
     */
    @Override
    default long regionStartPosition() {
        return position();
    }

    /**
     * Closes this mapped region.
     */
    @Override
    void close();
}
