/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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

public class Word {
    private final int length;
    private final int lengthBits;
    private final int blockMask;
    private final int sectorMask;
    private final int blockSizeBits;
    private final int sectorSizeBits;

    public Word(int length, int sectorSize) { //8, 64
        this.length = length;

        lengthBits = Integer.numberOfTrailingZeros(length);
        final int blockSize = sectorSize * sectorSize; //number of words in one block
        blockMask = blockSize - 1;
        sectorMask = sectorSize - 1;
        blockSizeBits = Integer.numberOfTrailingZeros(blockSize);
        sectorSizeBits = Integer.numberOfTrailingZeros(sectorSize);
    }

    public long position(final long index) {
        final long block = index >> blockSizeBits;                 // index / blockSize
        final long indexInBlock = index & blockMask;               // index % blockSize
        final long sectorInBlock = indexInBlock >> sectorSizeBits; // indexInBlock / sectorSize
        final long indexInSector = indexInBlock & sectorMask;      // indexInBlock % sectorSize
        return ((block << blockSizeBits) +           //  (block * blockSize
                (indexInSector << sectorSizeBits) +  //   indexInSector * sectorSize
                sectorInBlock) << lengthBits;        //    + sectorInBlock ) * length
    }

    public int length() {
        return length;
    }
}
