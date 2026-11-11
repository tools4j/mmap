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
package org.tools4j.mmap.core.internal;

/**
 * Functions for powers of two.
 */
public enum PowersOfTwo {
    ;

    /** The largest power of two of type int. */
    public static final int MAX_INT_POWER_OF_TWO = 1 << 30;

    /**
     * Returns whether a value is a power of two.
     *
     * @param value the value
     * @return true if the value is positive and a power of two
     */
    public static boolean isPowerOfTwo(final long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    /**
     * Returns the smallest power of two greater than or equal to a value, 1 for values less than 1.
     *
     * @param value the value
     * @return the smallest power of two not less than value
     * @throws IllegalArgumentException if value is greater than {@link #MAX_INT_POWER_OF_TWO}
     */
    public static int ceilingPowerOfTwo(final int value) {
        if (value > MAX_INT_POWER_OF_TWO) {
            throw new IllegalArgumentException("No power of two of type int is greater than or equal to " + value);
        }
        return value <= 1 ? 1 : 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(value - 1));
    }
}
