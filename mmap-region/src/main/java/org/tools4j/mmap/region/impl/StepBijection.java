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
 * A bijective function that generates sequences of values that are regular incremental steps apart. For a given step
 * size, two consecutive indices map to values that are always at least the given step size apart.
 * <p>
 * Besides the step size parameter, a power-of-two block length determines how many values to fully enumerate (with
 * wrap-around) before moving forward to enumerate the next block.
 * <p>
 * An example with block length 8 and step size 3 yields the following mapped value sequence:
 * <pre>
   o------------------------o------------------------------o----- - -
   | 0, 3, 6, 1, 4, 7, 2, 5 ; 8, 11, 14, 9, 12, 15, 10, 13 ; 16, ...
   o------------------------o------------------------------o----- - -
             Block 0                     Block 1               ...
 * </pre>
 * Step sizes must be uneven and less than half the block length. The backward mappings move at their own
 * {@linkplain #back() back} step size but use the same block length. Forward and backward step size are the same if
 * chosen exactly one less than half the block length.
 */
public class StepBijection implements IndexBijection {

    private final int block;
    private final long blockMask;
    private final int step;
    private final int back;

    public StepBijection(final int block) {
        this(block, block/2 - 1);
    }

    public StepBijection(final int block, final int step) {
        if (!BitUtil.isPowerOfTwo(block)) {
            throw new IllegalArgumentException("Block length must be a power of 2: " + block);
        }
        if (step <= 0) {
            throw new IllegalArgumentException("Step size must be at least 1: " + step);
        }
        if (block < 2*(step + 1)) {
            throw new IllegalArgumentException("Step size must be less than half the block length : block=" + block + ", step=" + step);
        }
        this.block = block;
        this.blockMask = block - 1;
        this.step = step;
        this.back = modInverse(step, blockMask);
    }

    public int block() {
        return block;
    }

    public int step() {
        return step;
    }

    public int back() {
        return back;
    }

    private static int modInverse(final int factor, final long mask) {
        final long n = mask + 1;
        long t = 0, newt = 1;
        long r = n, newr = factor;

        while (newr != 0) {
            final long quotient = r / newr;

            final long oldt = t;
            t = newt;
            newt = oldt - quotient * newt;

            final long oldr = r;
            r = newr;
            newr = oldr - quotient * newr;
        }

        if (r > 1) {
            throw new ArithmeticException("Not invertible: " + factor + " mod " + n);
        }
        return Math.toIntExact(t < 0 ? t + n : t);
    }

    @Override
    public long positionToIndex(final long position) {
        return walk(position, back, blockMask);
    }

    @Override
    public long indexToPosition(final long index) {
        return walk(index, step, blockMask);
    }

    private static long walk(final long index, final int increment, final long mask) {
        final long product = index * increment;
        return (index & ~mask) | (product & mask);
    }

    @Override
    public String toString() {
        return "StepBijection:block=" + block + "|step=" + step + "|back=" + back;
    }
}
