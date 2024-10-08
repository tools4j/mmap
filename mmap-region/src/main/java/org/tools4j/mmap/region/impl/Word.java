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

import static org.tools4j.mmap.region.impl.Constants.CACHE_LINE_BYTES;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;

public class Word {
    private final int length;
    private final int bits;
    private final IndexMapping mapping;

    public Word(final int length) {
        this(length, CACHE_LINE_BYTES / length, (int)(REGION_SIZE_GRANULARITY / CACHE_LINE_BYTES));
    }

    public Word(final int length, final int width, final int height) {
        if (!BitUtil.isPowerOfTwo(length)) {
            throw new IllegalArgumentException("Word length must be a power of 2: " + length);
        }
        this.length = length;
        this.bits = Long.SIZE - Long.numberOfLeadingZeros(length - 1);
        this.mapping = new BlockMapping(width, height);
    }

    public long position(final long index) {
        return mapping.indexToPosition(index) << bits;
    }

    public long index(final long position) {
        return mapping.positionToIndex(position >>> bits);
    }

    public int length() {
        return length;
    }
}
