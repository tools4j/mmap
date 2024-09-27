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

import org.junit.jupiter.params.ParameterizedTest;
import org.tools4j.spockito.jupiter.TableSource;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class WordTest {

    @TableSource({
            "| wordLength      | sectorSize      | index           | position       |",
            "|-----------------|-----------------|-----------------|----------------|",
            "| 8               | 8               | 0               | 0              |",
            "| 8               | 8               | 1               | 64             |",
            "| 8               | 8               | 2               | 128            |",
            "| 8               | 8               | 3               | 192            |",
            "| 8               | 8               | 4               | 256            |",
            "| 8               | 8               | 5               | 320            |",
            "| 8               | 8               | 6               | 384            |",
            "| 8               | 8               | 7               | 448            |",
            "| 8               | 8               | 8               | 8              |",
            "| 8               | 8               | 9               | 72             |",
            "| 8               | 8               | 10              | 136            |",
            "| 8               | 8               | 11              | 200            |",
            "| 8               | 8               | 12              | 264            |",
            "| 8               | 8               | 13              | 328            |",
            "| 8               | 8               | 14              | 392            |",
            "| 8               | 8               | 15              | 456            |",
            "| 8               | 8               | 16              | 16             |",
            "| 8               | 8               | 17              | 80             |",
            "| 8               | 8               | 63              | 504            |",
            "| 8               | 8               | 64              | 512            |",
    })
    @ParameterizedTest(name = "[{index}]: wordLength={0}, sectorSize={1},  index={2}, position={3}")
    public void wordPosition(final int wordLength, final int sectorSize, final long index, final long position) {
        Word word = new Word(wordLength, sectorSize, sectorSize);
        assertEquals(position, word.position(index));
    }

    @TableSource({
            "| wordLength | sectorSize |",
            "|------------|------------|",
            "|      1     |      4     |",
            "|      2     |      4     |",
            "|      4     |      4     |",
            "|      1     |      8     |",
            "|      2     |      8     |",
            "|      4     |      8     |",
            "|      8     |      8     |",
            "|      1     |     16     |",
            "|      2     |     16     |",
            "|      4     |     16     |",
            "|      8     |     16     |",
    })
    @ParameterizedTest(name = "[{index}]: wordLength={0}, sectorSize={1}")
    public void wordPosition(final int wordLength, final int sectorSize) {
        //given
        final Word word = new Word(wordLength, sectorSize, sectorSize);
        final int count = wordLength * sectorSize * sectorSize;
        final BitSet bitSet = new BitSet(count * wordLength);

        //when
        for (int i = 0; i < count; i++) {
            final long position = word.position(i);
            final int bit = (int)position;

            //then
            assertEquals(position, bit);
            assertFalse(bitSet.get(bit));

            bitSet.set(bit);
            //System.out.println(i + ": " + position);
        }

        assertEquals((count - 1) * wordLength + 1, bitSet.length());
    }
}
