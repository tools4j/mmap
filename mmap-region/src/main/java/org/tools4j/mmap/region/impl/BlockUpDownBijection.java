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
package org.tools4j.mmap.region.impl;

import org.agrona.BitUtil;

/**
 * A bijection that uses blocks and column-wise enumeration to map indices to positions and back. It is defined by a
 * rectangular block which is enumerated column by column. When the block is full a new block is started and filled,
 * but this time in decrementing order.  The third block is incrementing order again and so on.
 * <p>
 * To illustrate this consider the following example:
 * <pre>
              +----+----+----+----+
    Block 0 : |  0 |  2 |  4 |  6 |
              |  1 |  3 |  5 |  7 |
              +----+----+----+----+
    Block 1 : | 15 | 13 | 11 |  9 |
              | 14 | 12 | 10 |  8 |
              +----+----+----+----+
    Block 2 : | 16 | 18 | 20 | 22 |
              | 17 | 19 | 21 | 23 |
              +----+----+----+----+
    Block 3 : | 31 | 29 | 27 | 25 |
              | 30 | 28 | 26 | 24 |
              +----+----+----+----+
    Block 4:  ...

    Reading row by row, the bijection is then:

    [0]=0, [1]=2, [2]=4, [3]=6, [4]=1, [5]=3, [6]=5, [7]=7, [8]=15, [9]=13, ...
 * </pre>
 * To make forward and backward calculation as efficient as possible, block dimensions have to be powers of two.
 * <p>
 * Block bijections can for instance be used to minimize updating of the same cache line when writing sequential
 * indices.
 */
public class BlockUpDownBijection implements IndexBijection {

    private final long blockMask;
    private final long widthMask;
    private final long heightMask;
    private final int blockBits;
    private final int widthBits;
    private final int heightBits;

    /**
     * Constructor for block bijection with a square block of the specified size.
     * @param size the block size, a power of two
     * @throws IllegalArgumentException if size is not a power of two
     */
    public BlockUpDownBijection(final int size) {
        this(size, size);
    }

    /**
     * Constructor for block bijection with a rectangular block of the specified with and height.
     * @param width the block width, a power of two
     * @param height the block height, a power of two
     * @throws IllegalArgumentException if width or height is not a power of two
     */
    public BlockUpDownBijection(final int width, final int height) {
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
        this.blockBits = widthBits + heightBits;
        this.blockMask = (1L << blockBits) - 1;
    }

    @Override
    public long positionToIndex(final long position) {
        final long mask = blockMask;
        final long x = (position >>> heightBits) & widthMask;
        final long y = position & heightMask;
        final long xo = mask & -(0x1 & (position >> blockBits));
        final long hi = position & ~mask;
        final long lo = x | (y << widthBits);
        return hi | (xo ^ lo);
    }

    @Override
    public long indexToPosition(final long index) {
        final long mask = blockMask;
        final long x = index & widthMask;
        final long y = (index >>> widthBits) & heightMask;
        final long xo = mask & -(0x1 & (index >> blockBits));
        final long hi = index & ~mask;
        final long lo = (x << heightBits) | y;
        return hi | (xo ^ lo);
    }

    @Override
    public String toString() {
        return "BlockBijection:width=" + (1 << widthBits) + "|height=" + (1 << heightBits);
    }
}
