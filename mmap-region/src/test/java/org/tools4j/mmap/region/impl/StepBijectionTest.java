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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link StepBijection}
 */
public class StepBijectionTest {

    @CsvSource(delimiter = '|', value = {
            " 4 |   0  |   0",
            " 4 |   1  |   1",
            " 4 |   2  |   2",
            " 4 |   3  |   3",
            " 4 |   4  |   4",
            " 4 |   5  |   5",
            " 4 |   6  |   6",
            " 4 |   7  |   7",
            " 4 |   8  |   8",
            " 4 |   9  |   9",
            " 4 |  10  |  10",
            " 4 |  11  |  11",
            " 4 |  12  |  12",
            " 4 |  13  |  13",
            " 4 |  14  |  14",
            " 4 |  15  |  15",
            " 4 |  16  |  16",
            " 4 |  17  |  17",
            " 4 |  18  |  18",
            " 4 |  19  |  19",
            " 4 |  20  |  20",
            //--|------|------
            " 8 |   0  |   0",
            " 8 |   1  |   3",
            " 8 |   2  |   6",
            " 8 |   3  |   1",
            " 8 |   4  |   4",
            " 8 |   5  |   7",
            " 8 |   6  |   2",
            " 8 |   7  |   5",
            " 8 |   8  |   8",
            " 8 |   9  |  11",
            " 8 |  10  |  14",
            " 8 |  11  |   9",
            " 8 |  12  |  12",
            " 8 |  13  |  15",
            " 8 |  14  |  10",
            " 8 |  15  |  13",
            " 8 |  16  |  16",
            " 8 |  17  |  19",
            " 8 |  63  |  61",
            " 8 |  64  |  64",
            " 8 |  65  |  67",
            //--|------|------
            "16 |   0  |   0",
            "16 |   1  |   7",
            "16 |   2  |  14",
            "16 |   3  |   5",
            "16 |   4  |  12",
            "16 |   5  |   3",
            "16 |   6  |  10",
            "16 |   7  |   1",
            "16 |   8  |   8",
            "16 |   9  |  15",
            "16 |  10  |   6",
            "16 |  11  |  13",
            "16 |  12  |   4",
            "16 |  13  |  11",
            "16 |  14  |   2",
            "16 |  15  |   9",
            "16 |  16  |  16",
            "16 |  17  |  23",
            //--|------|------
            "64 |   0  |   0",
            "64 |   1  |  31",
            "64 |   2  |  62",
            "64 |   3  |  29",
            "64 |   4  |  60",
            "64 |   5  |  27",
            "64 |   6  |  58",
            "64 |   7  |  25",
            "64 |   8  |  56",
            "64 |   9  |  23",
            "64 |  10  |  54",
            "64 |  63  |  33",
            "64 |  64  |  64",
            "64 |  65  |  95",
    })
    @ParameterizedTest(name = "[{index}]: block={0}, index={1}, position={2}")
    public void square(final int block, final long index, final long position) {
        final StepBijection bijection = new StepBijection(block);
        assertEquals(position, bijection.indexToPosition(index), "indexToPosition(" + index + ")");
        assertEquals(index, bijection.positionToIndex(position), "positionToIndex(" + position + ")");
    }

    @CsvSource(delimiter = '|', value = {
            " 16 |   3 |   0 |   0",
            " 16 |   3 |   1 |   3",
            " 16 |   3 |   2 |   6",
            " 16 |   3 |   3 |   9",
            " 16 |   3 |   4 |  12",
            " 16 |   3 |   5 |  15",
            " 16 |   3 |   6 |   2",
            " 16 |   3 |   7 |   5",
            " 16 |   3 |   8 |   8",
            " 16 |   3 |   9 |  11",
            " 16 |   3 |  10 |  14",
            " 16 |   3 |  11 |   1",
            " 16 |   3 |  12 |   4",
            " 16 |   3 |  13 |   7",
            " 16 |   3 |  14 |  10",
            " 16 |   3 |  15 |  13",
            " 16 |   3 |  16 |  16",
            " 16 |   3 |  17 |  19",
            " 16 |   3 |  18 |  22",
            " 16 |   3 |  19 |  25",
            " 16 |   3 |  20 |  28",
            " 16 |   3 |  21 |  31",
            " 16 |   3 |  22 |  18",
            //---|-----|-----|------
            " 16 |   7 |   0 |   0",
            " 16 |   7 |   1 |   7",
            " 16 |   7 |   2 |  14",
            " 16 |   7 |   3 |   5",
            " 16 |   7 |   4 |  12",
            " 16 |   7 |   5 |   3",
            " 16 |   7 |   6 |  10",
            " 16 |   7 |   7 |   1",
            " 16 |   7 |   8 |   8",
            " 16 |   7 |   9 |  15",
            " 16 |   7 |  10 |   6",
            " 16 |   7 |  11 |  13",
            " 16 |   7 |  12 |   4",
            " 16 |   7 |  13 |  11",
            " 16 |   7 |  14 |   2",
            " 16 |   7 |  15 |   9",
            " 16 |   7 |  16 |  16",
            " 16 |   7 |  17 |  23",
            " 16 |   7 |  18 |  30",
            " 16 |   7 |  19 |  21",
            " 16 |   7 |  20 |  28",
            " 16 |   7 |  21 |  19",
            " 16 |   7 |  22 |  26",
            //---|-----|-----|------
            " 64 |   7 |   0 |   0",
            " 64 |   7 |   1 |   7",
            " 64 |   7 |   2 |  14",
            " 64 |   7 |   3 |  21",
            " 64 |   7 |   4 |  28",
            " 64 |   7 |   5 |  35",
            " 64 |   7 |   6 |  42",
            " 64 |   7 |   7 |  49",
            " 64 |   7 |   8 |  56",
            " 64 |   7 |   9 |  63",
            " 64 |   7 |  10 |   6",
            " 64 |   7 |  11 |  13",
            " 64 |   7 |  12 |  20",
            " 64 |   7 |  63 |  57",
            " 64 |   7 |  64 |  64",
            " 64 |   7 |  65 |  71",
            " 64 |   7 | 127 | 121",
            " 64 |   7 | 128 | 128",
            " 64 |   7 | 129 | 135",
            //---|-----|-----|------
            " 64 |  15 |   0 |   0",
            " 64 |  15 |   1 |  15",
            " 64 |  15 |   2 |  30",
            " 64 |  15 |   3 |  45",
            " 64 |  15 |   4 |  60",
            " 64 |  15 |   5 |  11",
            " 64 |  15 |   6 |  26",
            " 64 |  15 |   7 |  41",
            " 64 |  15 |   8 |  56",
            " 64 |  15 |   9 |   7",
            " 64 |  15 |  10 |  22",
            " 64 |  15 |  11 |  37",
            " 64 |  15 |  12 |  52",
            " 64 |  15 |  63 |  49",
            " 64 |  15 |  64 |  64",
            " 64 |  15 |  65 |  79",
            " 64 |  15 | 127 | 113",
            " 64 |  15 | 128 | 128",
            " 64 |  15 | 129 | 143",
            //---|-----|-----|------
            " 64 |  31 |   0 |   0",
            " 64 |  31 |   1 |  31",
            " 64 |  31 |   2 |  62",
            " 64 |  31 |   3 |  29",
            " 64 |  31 |   4 |  60",
            " 64 |  31 |   5 |  27",
            " 64 |  31 |   6 |  58",
            " 64 |  31 |   7 |  25",
            " 64 |  31 |   8 |  56",
            " 64 |  31 |   9 |  23",
            " 64 |  31 |  10 |  54",
            " 64 |  31 |  11 |  21",
            " 64 |  31 |  12 |  52",
            " 64 |  31 |  63 |  33",
            " 64 |  31 |  64 |  64",
            " 64 |  31 |  65 |  95",
            " 64 |  31 | 127 |  97",
            " 64 |  31 | 128 | 128",
            " 64 |  31 | 129 | 159",
    })
    @ParameterizedTest(name = "[{index}]: width={0}, height={1}, index={2}, position={3}")
    public void rectangular(final int width, final int height, final long index, final long position) {
        final StepBijection bijection = new StepBijection(width, height);
        assertEquals(position, bijection.indexToPosition(index), "indexToPosition(" + index + ")");
        assertEquals(index, bijection.positionToIndex(position), "positionToIndex(" + position + ")");
    }

    @CsvSource(delimiter = '|', value = {
            "    4 |    1",
            "    8 |    3",
            "   16 |    3",
            "   16 |    5",
            "   16 |    7",
            "   32 |    3",
            "   32 |    5",
            "   32 |    7",
            "   32 |    9",
            "   32 |   11",
            "   32 |   13",
            "   32 |   15",
            "   64 |    7",
            "   64 |   15",
            "   64 |   29",
            "   64 |   31",
            "  128 |   15",
            "  128 |   31",
            "  128 |   61",
            "  128 |   63",
            "  256 |   31",
            "  256 |  125",
            "  256 |  127",
            "  256 |   63",
            "  512 |  127",
            "  512 |  253",
            "  512 |  255",
            " 1024 |  127",
            " 1024 |  255",
            " 1024 |  509",
            " 1024 |  511",
            " 2048 |  511",
            " 2048 | 1021",
            " 2048 | 1023",
            " 4096 | 1023",
            " 4096 | 2045",
            " 4096 | 2047",
            " 8192 | 1023",
            " 8192 | 2047",
            " 8192 | 4093",
            " 8192 | 4095",
            "16384 | 2047",
            "16384 | 4095",
            "16384 | 8189",
            "16384 | 8191",
    })
    @ParameterizedTest(name = "[{index}]: block={0}, step={1}")
    public void iterate(final int block, final int step) {
        //given
        final int rounds = Math.min(3 * block, (1<<26)/block);
        final StepBijection bijection = new StepBijection(block, step);

        //when + then
        iterate(bijection, rounds);
    }

    @ValueSource(ints = {4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384})
    @ParameterizedTest(name = "[{index}]: block={0}")
    public void iterate(final int block) {
        //given
        final int rounds = Math.min(3 * block, (1<<24)/block);
        final StepBijection bijection = new StepBijection(block);

        //when + then
        iterate(bijection, rounds);
    }

    private void iterate(final StepBijection bijection, final int rounds) {
        //given
        final int block = bijection.block();
        final int step = bijection.step();
        final int count = block * rounds;
        final BitSet bitSet = new BitSet(count);

        //when
        for (int i = 0; i < count; i++) {
            final long position = bijection.indexToPosition(i);
            final int bit = (int)position;

            //then
            assertEquals(position, bit);
            assertEquals(i, bijection.positionToIndex(position));
            assertFalse(bitSet.get(bit));
            if (i > 0) {
                final long previous = bijection.indexToPosition(i - 1);
                final long distance = position >= previous ? position - previous : previous - position;
                if (distance < step) {
                    fail("distance [" + previous + ".." + position + "] should be at least " + step);
                }
            }

            bitSet.set(bit);
        }

        assertEquals(count, bitSet.length());
        System.out.println(bijection + " --> rounds=" + rounds + ", count=" + count);
    }

    @CsvSource(delimiter = '|', value = {
            "    8 |    2",
            "   16 |    2",
            "   16 |    4",
            "   16 |    6",
            "   32 |    2",
            "   32 |    4",
            "   32 |    6",
            "   32 |    8",
            "   32 |   14",
            "   64 |   30",
            "  128 |   62",
            "  256 |  126",
            "  512 |  200",
            "  512 |  254",
            " 1024 |  500",
            " 1024 |  510",
            " 2048 | 1000",
            " 2048 | 1022",
            " 4096 | 1000",
            " 4096 | 2000",
            " 4096 | 2046",
            " 8192 | 4000",
            " 8192 | 4094",
            "16384 | 8000",
            "16384 | 8190",
    })
    @ParameterizedTest(name = "[{index}]: block={0}, step={1}")
    public void notInvertible(final int block, final int step) {
        assertThrowsExactly(ArithmeticException.class, () -> new StepBijection(block, step),
                "StepBijection(" + block+ ", " + step + ")");
    }
}
