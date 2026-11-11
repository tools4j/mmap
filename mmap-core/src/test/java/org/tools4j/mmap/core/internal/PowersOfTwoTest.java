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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link PowersOfTwo}.
 */
class PowersOfTwoTest {

    @Test
    void isPowerOfTwo() {
        for (int bit = 0; bit < Long.SIZE - 1; bit++) {
            assertTrue(PowersOfTwo.isPowerOfTwo(1L << bit), "2^" + bit);
            if (bit > 1) {
                assertFalse(PowersOfTwo.isPowerOfTwo((1L << bit) + 1), "2^" + bit + "+1");
                assertFalse(PowersOfTwo.isPowerOfTwo((1L << bit) - 1), "2^" + bit + "-1");
            }
        }
        assertFalse(PowersOfTwo.isPowerOfTwo(0));
        assertFalse(PowersOfTwo.isPowerOfTwo(-2));
        assertFalse(PowersOfTwo.isPowerOfTwo(Long.MIN_VALUE));
    }

    @Test
    void ceilingPowerOfTwo() {
        assertEquals(1, PowersOfTwo.ceilingPowerOfTwo(Integer.MIN_VALUE));
        assertEquals(1, PowersOfTwo.ceilingPowerOfTwo(0));
        assertEquals(1, PowersOfTwo.ceilingPowerOfTwo(1));
        assertEquals(2, PowersOfTwo.ceilingPowerOfTwo(2));
        assertEquals(4, PowersOfTwo.ceilingPowerOfTwo(3));
        for (int bit = 2; bit <= 30; bit++) {
            assertEquals(1 << bit, PowersOfTwo.ceilingPowerOfTwo(1 << bit));
            assertEquals(1 << bit, PowersOfTwo.ceilingPowerOfTwo((1 << bit) - 1));
            if (bit < 30) {
                assertEquals(1 << (bit + 1), PowersOfTwo.ceilingPowerOfTwo((1 << bit) + 1));
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> PowersOfTwo.ceilingPowerOfTwo(PowersOfTwo.MAX_INT_POWER_OF_TWO + 1));
    }
}
