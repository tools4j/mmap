/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.spockito.jupiter.TableSource;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for {@link BlockMapping}
 */
public class BlockMappingTest {

    @TableSource({
            "| size | index | position |",
            "|======|=======|==========|",
            "|   8  |    0  |     0    |",
            "|   8  |    1  |     8    |",
            "|   8  |    2  |    16    |",
            "|   8  |    3  |    24    |",
            "|   8  |    4  |    32    |",
            "|   8  |    5  |    40    |",
            "|   8  |    6  |    48    |",
            "|   8  |    7  |    56    |",
            "|   8  |    8  |     1    |",
            "|   8  |    9  |     9    |",
            "|   8  |   10  |    17    |",
            "|   8  |   11  |    25    |",
            "|   8  |   12  |    33    |",
            "|   8  |   13  |    41    |",
            "|   8  |   14  |    49    |",
            "|   8  |   15  |    57    |",
            "|   8  |   16  |     2    |",
            "|   8  |   17  |    10    |",
            "|   8  |   63  |    63    |",
            "|   8  |   64  |    64    |",
            "|------|-------|----------|",
    })
    @ParameterizedTest(name = "[{index}]: size={0}, index={1}, position={2}")
    public void square(final int size, final long index, final long position) {
        final BlockMapping mapping = new BlockMapping(size);
        assertEquals(position, mapping.indexToPosition(index));
        assertEquals(index, mapping.positionToIndex(position));
    }

    @TableSource({
            "| width | height | index | position |",
            "|=======|========|=======|==========|",
            "|   4   |    2   |    0  |     0    |",
            "|   4   |    2   |    1  |     2    |",
            "|   4   |    2   |    2  |     4    |",
            "|   4   |    2   |    3  |     6    |",
            "|   4   |    2   |    4  |     1    |",
            "|   4   |    2   |    5  |     3    |",
            "|   4   |    2   |    6  |     5    |",
            "|   4   |    2   |    7  |     7    |",
            "|   4   |    2   |    8  |     8    |",
            "|   4   |    2   |    9  |    10    |",
            "|-------|--------|-------|----------|",
            "|   2   |    4   |    0  |     0    |",
            "|   2   |    4   |    1  |     4    |",
            "|   2   |    4   |    2  |     1    |",
            "|   2   |    4   |    3  |     5    |",
            "|   2   |    4   |    4  |     2    |",
            "|   2   |    4   |    5  |     6    |",
            "|   2   |    4   |    6  |     3    |",
            "|   2   |    4   |    7  |     7    |",
            "|   2   |    4   |    8  |     8    |",
            "|   2   |    4   |    9  |    12    |",
            "|-------|--------|-------|----------|",
            "|  64   |   16   |    0  |     0    |",
            "|  64   |   16   |    1  |    16    |",
            "|  64   |   16   |    2  |    32    |",
            "|  64   |   16   |    3  |    48    |",
            "|  64   |   16   |    4  |    64    |",
            "|  64   |   16   |    5  |    80    |",
            "|  64   |   16   |    6  |    96    |",
            "|  64   |   16   |    7  |   112    |",
            "|  64   |   16   |    8  |   128    |",
            "|  64   |   16   |    9  |   144    |",
            "|  64   |   16   |   15  |   240    |",
            "|  64   |   16   |   16  |   256    |",
            "|  64   |   16   |   17  |   272    |",
            "|  64   |   16   |   63  |  1008    |",
            "|  64   |   16   |   64  |     1    |",
            "|  64   |   16   |   65  |    17    |",
            "|  64   |   16   | 1023  |  1023    |",
            "|  64   |   16   | 1024  |  1024    |",
            "|-------|--------|-------|----------|",
    })
    @ParameterizedTest(name = "[{index}]: width={0}, height={1}, index={2}, position={3}")
    public void rectangular(final int width, final int height, final long index, final long position) {
        final BlockMapping mapping = new BlockMapping(width, height);
        assertEquals(position, mapping.indexToPosition(index));
        assertEquals(index, mapping.positionToIndex(position));
    }

    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256})
    @ParameterizedTest(name = "[{index}]: size={0}")
    public void iterate(final int size) {
        //given
        final BlockMapping mapping = new BlockMapping(size);
        final int count = size * size;
        final BitSet bitSet = new BitSet(count);

        //when
        for (int i = 0; i < count; i++) {
            final long position = mapping.indexToPosition(i);
            final int bit = (int)position;

            //then
            assertEquals(position, bit);
            assertEquals(i, mapping.positionToIndex(position));
            assertFalse(bitSet.get(bit));

            bitSet.set(bit);
            //System.out.println(i + ": " + position);
        }

        assertEquals(count, bitSet.length());
    }
}
