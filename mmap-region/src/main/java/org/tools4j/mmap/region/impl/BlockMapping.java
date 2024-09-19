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

import org.agrona.BitUtil;

/**
 * A block mapping can be used as an index mapping to minimize updating of the same cache line when writing sequential
 * indices. To make forward and backward mapping as efficient as possible, block dimensions have to be powers of two.
 * <p>
 * A block mapping is defined by a rectangular block which is enumerated column by column. When the block is full a next
 * block is started, for instance
 * <pre>
              +----+----+----+----+
    Block 0 : |  0 |  2 |  4 |  6 |
              |  1 |  3 |  5 |  7 |
              +----+----+----+----+
    Block 1 : |  8 | 10 | 12 | 14 |
              |  9 | 11 | 13 | 15 |
              +----+----+----+----+

    The mapping is (row by row): [0]=0, [1]=2, [2]=4, [3]=6, [4]=1, [5]=3, [6]=5, [7]=7, [8]=8, [9]=10, ...
 * </pre>
 */
public class BlockMapping implements IndexMapping {

    private final long blockMaskInverted;
    private final long widthMask;
    private final int widthBits;
    private final long heightMask;
    private final int heightBits;

    /**
     * Constructor for block mapping with a square block of the specified size.
     * @param size the block size, a power of two
     * @throws IllegalArgumentException if size is not a power of two
     */
    public BlockMapping(final int size) {
        this(size, size);
    }

    /**
     * Constructor for block mapping with a rectangular block of the specified with and height.
     * @param width the block width, a power of two
     * @param height the block height, a power of two
     * @throws IllegalArgumentException if width or height is not a power of two
     */
    public BlockMapping(final int width, final int height) {
        if (!BitUtil.isPowerOfTwo(width)) {
            throw new IllegalArgumentException("Block width must be a power of 2: " + width);
        }
        if (!BitUtil.isPowerOfTwo(height)) {
            throw new IllegalArgumentException("Block height must be a power of 2: " + height);
        }
        this.widthMask = width - 1;
        this.heightMask = height - 1;
        this.widthBits = Integer.SIZE - Integer.numberOfLeadingZeros(width - 1);
        this.heightBits = Integer.SIZE - Integer.numberOfLeadingZeros(height - 1);
        this.blockMaskInverted = -(1L << (widthBits + heightBits));
    }

    @Override
    public long positionToIndex(final long position) {
        final long x = (position >>> heightBits) & widthMask;
        final long y = position & heightMask;
        final long block = position & blockMaskInverted;
        return block | x | (y << widthBits);
    }

    @Override
    public long indexToPosition(final long index) {
        final long x = index & widthMask;
        final long y = (index >>> widthBits) & heightMask;
        final long block = index & blockMaskInverted;
        return block | (x << heightBits) | y;
    }

    @Override
    public String toString() {
        return "BlockMapping:width=" + (1 << widthBits) + "|height=" + (1 << heightBits);
    }
}
