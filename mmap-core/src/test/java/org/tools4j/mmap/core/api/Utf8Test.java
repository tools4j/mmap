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
package org.tools4j.mmap.core.api;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Utf8}.
 */
class Utf8Test {

    private static final String GRINNING_FACE = "😀";

    @Test
    void lengthCountsBytesPerCharacter() {
        assertEquals(0, Utf8.length(""));
        assertEquals(3, Utf8.length("abc"));
        assertEquals(2, Utf8.length("é"));
        assertEquals(3, Utf8.length("€"));
        assertEquals(4, Utf8.length(GRINNING_FACE));
        assertEquals(1 + 4 + 1 + 3 + 2, Utf8.length("x" + GRINNING_FACE + "y€é"));
    }

    @Test
    void lengthCountsBoundariesOfEachEncodingLength() {
        assertEquals(1, Utf8.length("\u007F"));
        assertEquals(2, Utf8.length("\u0080"));
        assertEquals(2, Utf8.length("߿"));
        assertEquals(3, Utf8.length("ࠀ"));
        assertEquals(3, Utf8.length("￿"));
    }

    @Test
    void lengthCountsUnpairedSurrogatesAsOneByte() {
        assertEquals(1 + 1, Utf8.length("a\uD83D"));
        assertEquals(1 + 1, Utf8.length("\uDE00b"));
        assertEquals(1 + 4, Utf8.length("\uD83D" + GRINNING_FACE));
        assertEquals(1 + 1, Utf8.length("\uDE00\uD83D"));
    }

    @Test
    void lengthOfRangeCountsSurrogatePairSplitByRangeAsUnpaired() {
        final String value = "a" + GRINNING_FACE + "b";
        assertEquals(4, Utf8.length(value, 1, 2));
        assertEquals(1 + 1, Utf8.length(value, 0, 2));
        assertEquals(1 + 1, Utf8.length(value, 2, 2));
        assertEquals(0, Utf8.length(value, 4, 0));
    }

    @Test
    void lengthOfRangeRejectsRangeOutsideOfValue() {
        assertThrows(IndexOutOfBoundsException.class, () -> Utf8.length("abc", -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> Utf8.length("abc", 2, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> Utf8.length("abc", 0, -1));
    }

    @Test
    void lengthAcceptsAnyCharSequence() {
        assertEquals(1 + 4, Utf8.length(new StringBuilder("a").append(GRINNING_FACE)));
    }

    @Test
    void lengthEqualsLengthOfJdkEncoding() {
        final Random random = new Random(42);
        final StringBuilder value = new StringBuilder();
        for (int round = 0; round < 100_000; round++) {
            value.setLength(0);
            final int length = random.nextInt(8);
            for (int index = 0; index < length; index++) {
                value.append(randomChar(random));
            }
            final int offset = value.length() == 0 ? 0 : random.nextInt(value.length());
            final int rangeLength = random.nextInt(value.length() - offset + 1);
            final String range = value.substring(offset, offset + rangeLength);
            assertEquals(value.toString().getBytes(UTF_8).length, Utf8.length(value), value::toString);
            assertEquals(range.getBytes(UTF_8).length, Utf8.length(value, offset, rangeLength), range);
        }
    }

    private static char randomChar(final Random random) {
        switch (random.nextInt(4)) {
            case 0:
                return (char) random.nextInt(0x80);
            case 1:
                return (char) random.nextInt(0x800);
            case 2:
                return (char) (Character.MIN_SURROGATE + random.nextInt(Character.MAX_SURROGATE
                        - Character.MIN_SURROGATE + 1));
            default:
                return (char) random.nextInt(0x10000);
        }
    }
}
