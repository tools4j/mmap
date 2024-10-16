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
package org.tools4j.mmap.queue.impl;

import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.region.api.DynamicMapping;
import org.tools4j.mmap.region.impl.Word;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

enum Headers {
    ;
    private static final long PAYLOAD_POSITION_MASK = 0x00ffffffffffffffL;
    private static final int APPENDER_ID_SHIFT = Long.SIZE - Byte.SIZE;
    public static final int HEADER_LENGTH = Long.BYTES;
    public static final Word HEADER_WORD = new Word(HEADER_LENGTH);
    public static final long NULL_HEADER = 0;


    public static int appenderId(final long header) {
        return (int) (header >>> APPENDER_ID_SHIFT);
    }

    public static long payloadPosition(final long header) {
        return header & PAYLOAD_POSITION_MASK;
    }

    public static long header(final short appenderId, final long payloadPosition) {
        return  ((0xffL & appenderId) << APPENDER_ID_SHIFT) | payloadPosition;
    }

    public static boolean isValidHeaderAt(final DynamicMapping header, final long index) {
        if (index < Index.FIRST || index > Index.MAX) {
            return false;
        }
        final long originalPosition = header.position();
        final boolean result = _isValidHeaderAt(header, index);
        if (originalPosition != NULL_POSITION) {
            header.moveTo(originalPosition);
        }
        return result;
    }

    private static boolean _isValidHeaderAt(final DynamicMapping header, final long index) {
        final long position = HEADER_WORD.position(index);
        return header.moveTo(position) && header.buffer().getLongVolatile(0) != NULL_HEADER;
    }

    private static long mid(final long a, final long b) {
        return (a >>> 1) + (b >>> 1) + (a & b & 0x1L);
    }

    public static long binarySearchLastIndex(final DynamicMapping header, final long startIndex) {
        if (startIndex < Index.FIRST || startIndex > Index.MAX) {
            throw new IllegalArgumentException("Invalid start index: " + startIndex);
        }
        //1) initial low
        if (!isValidHeaderAt(header, startIndex)) {
            return Index.NULL;
        }
        final long originalPosition = header.position();
        long lowIndex = startIndex;
        long highIndex = Index.NULL;

        //2) find low + high
        while (highIndex == Index.NULL && lowIndex + 1 >= 0) {
            long increment = 1L;
            do {
                if (highIndex != Index.NULL) {
                    lowIndex = highIndex;
                }
                highIndex = lowIndex + increment;
                if (increment <= 0 || highIndex < 0) {
                    highIndex = Index.NULL;
                    break;
                }
                increment <<= 1;
            } while (_isValidHeaderAt(header, highIndex));
        }

        //3) find middle
        if (highIndex != Index.NULL) {
            while (lowIndex + 1L < highIndex) {
                final long midIndex = mid(lowIndex, highIndex);
                if (_isValidHeaderAt(header, midIndex)) {
                    lowIndex = midIndex;
                } else {
                    highIndex = midIndex;
                }
            }
        }
        header.moveTo(Math.max(originalPosition, Index.FIRST));
        return lowIndex;
    }

}
