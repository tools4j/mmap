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
package org.tools4j.mmap.mapping.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link CpuLists}.
 */
class CpuListsTest {

    @Test
    void parseKeepsListOrder() {
        assertArrayEquals(new int[]{0, 1, 2, 3, 8, 10, 11}, CpuLists.parse(" 0-3,8, 10 - 11\n"));
        assertArrayEquals(new int[]{6, 0, 2, 4}, CpuLists.parse("6,0,2,4"));
        assertArrayEquals(new int[]{5}, CpuLists.parse("5"));
        final int[] toMax = CpuLists.parse("0-");
        assertEquals(SystemCpus.maxOnlineCpu() + 1, toMax.length);
    }

    @Test
    void parseRejectsInvalidList() {
        for (final String invalid : new String[]{"", " ", "x", "-1", "3-2", "0,,1", "1,1", "0-2,1", "8192",
                "0-8192"}) {
            assertThrows(IllegalArgumentException.class, () -> CpuLists.parse(invalid), invalid);
        }
    }

    @Test
    void formatJoinsAscendingRuns() {
        assertEquals("0-3,8,10-11", CpuLists.format(new int[]{0, 1, 2, 3, 8, 10, 11}));
        assertEquals("6,0,2,4", CpuLists.format(new int[]{6, 0, 2, 4}));
        assertEquals("3,2,1", CpuLists.format(new int[]{3, 2, 1}));
        assertEquals("", CpuLists.format(new int[0]));
    }

    @Test
    void rangeAndValidate() {
        assertArrayEquals(new int[]{2, 3, 4}, CpuLists.range(2, 4));
        assertThrows(IllegalArgumentException.class, () -> CpuLists.range(4, 2));
        assertThrows(IllegalArgumentException.class, () -> CpuLists.range(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> CpuLists.validate(new int[0]));
        assertThrows(IllegalArgumentException.class, () -> CpuLists.validate(new int[]{1, 2, 1}));
    }
}
