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

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link DirectBuffers#equals(org.agrona.DirectBuffer, int, org.agrona.DirectBuffer, int, int)}.
 */
class DirectBuffersTest {

    //long-sized, int-sized and byte-sized comparison steps, at unaligned offsets
    private static final int MAX_LENGTH = 2 * Long.BYTES + Integer.BYTES + 3;
    private static final int OFFSET1 = 3;
    private static final int OFFSET2 = 5;

    private final UnsafeBuffer buffer1 = new UnsafeBuffer(new byte[OFFSET1 + MAX_LENGTH]);
    private final UnsafeBuffer buffer2 = new UnsafeBuffer(new byte[OFFSET2 + MAX_LENGTH]);

    @Test
    void identicalBytesAreEqualForAnyLength() {
        fillWithSameBytes();
        for (int length = 0; length <= MAX_LENGTH; length++) {
            assertTrue(DirectBuffers.equals(buffer1, OFFSET1, buffer2, OFFSET2, length), "length=" + length);
        }
    }

    @Test
    void differenceIsDetectedOnlyWithinLength() {
        fillWithSameBytes();
        for (int diffIndex = 0; diffIndex < MAX_LENGTH; diffIndex++) {
            buffer2.putByte(OFFSET2 + diffIndex, (byte) ~diffIndex);
            for (int length = 0; length <= MAX_LENGTH; length++) {
                assertEquals(length <= diffIndex, DirectBuffers.equals(buffer1, OFFSET1, buffer2, OFFSET2, length),
                        "diffIndex=" + diffIndex + ", length=" + length);
            }
            buffer2.putByte(OFFSET2 + diffIndex, (byte) diffIndex);
        }
    }

    private void fillWithSameBytes() {
        for (int i = 0; i < MAX_LENGTH; i++) {
            buffer1.putByte(OFFSET1 + i, (byte) i);
            buffer2.putByte(OFFSET2 + i, (byte) i);
        }
    }
}
