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
import org.tools4j.mmap.region.api.OffsetMapping;
import org.tools4j.mmap.region.impl.Word;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

enum Headers {
    ;
    public static final int PAYLOAD_GRANULARITY_BITS = 3;

    /**
     * Payload granularity is 8 bytes, which gives good word alignment and very little wasted storage.
     * Note that we always store an integer for the payload size. This means that at most bytes are wasted to padding
     * per entry which only happens for zero payload entries.
     */
    public static final long PAYLOAD_GRANULARITY = 1L << PAYLOAD_GRANULARITY_BITS;
    private static final long PAYLOAD_GRANULARITY_MASK = PAYLOAD_GRANULARITY - 1;
    public static final int APPENDER_ID_BITS = Byte.SIZE;
    public static final int MAX_APPENDERS = 1 << APPENDER_ID_BITS;
    public static final int MAX_APPENDER_ID = MAX_APPENDERS - 1;
    public static final int MIN_APPENDER_ID = 0;
    private static final int APPENDER_ID_MASK = MAX_APPENDERS - 1;
    private static final long APPENDER_ID_HEADER_MASK = APPENDER_ID_MASK;
    private static final int ADJUSTED_POSITION_SHIFT = APPENDER_ID_BITS - PAYLOAD_GRANULARITY_BITS;
    private static final long ADJUSTED_POSITION_HEADER_MASK = ~APPENDER_ID_HEADER_MASK;
    private static final long ADJUSTED_POSITION_MASK = ADJUSTED_POSITION_HEADER_MASK >>> ADJUSTED_POSITION_SHIFT;

    private static final long PAYLOAD_POSITION_ADJUSTMENT = PAYLOAD_GRANULARITY;
    /**
     * Max payload position is 576,460,752,303,423,472, which is > 500,000 terabytes.
     */
    public static final long MAX_PAYLOAD_POSITION = ADJUSTED_POSITION_MASK - PAYLOAD_POSITION_ADJUSTMENT;
    public static final int HEADER_LENGTH = Long.BYTES;
    public static final Word HEADER_WORD = new Word(HEADER_LENGTH);
    public static final long NULL_HEADER = 0;


    public static int appenderId(final long header) {
        return (int)(header & APPENDER_ID_HEADER_MASK);
    }

    public static long payloadPosition(final long header) {
        return ((header & ADJUSTED_POSITION_HEADER_MASK) >>> ADJUSTED_POSITION_SHIFT) - PAYLOAD_POSITION_ADJUSTMENT;
    }

    public static long nextPayloadPosition(final long currentPayloadPosition, final int currentPayloadBytes) {
        assert validPayloadPosition(currentPayloadPosition) : "currentPayloadPosition must be valid";
        assert currentPayloadBytes >= 0 : "currentPayloadBytes must not be negative";
        return currentPayloadPosition + ((currentPayloadBytes + PAYLOAD_GRANULARITY - 1) >>> PAYLOAD_GRANULARITY_BITS);
    }

    public static boolean validAppenderId(final int appenderId) {
        return appenderId == (appenderId & APPENDER_ID_MASK);
    }

    public static boolean validPayloadPosition(final long payloadPosition) {
        final long adjustedPosition = payloadPosition + PAYLOAD_POSITION_ADJUSTMENT;
        return payloadPosition >= 0 && adjustedPosition == (adjustedPosition & ADJUSTED_POSITION_MASK);
    }

    public static int validateAppenderId(final int appenderId) {
        if (validAppenderId(appenderId)) {
            return appenderId;
        }
        if (appenderId > MAX_APPENDER_ID) {
            throw new IllegalArgumentException("Appender ID " + appenderId + " exceeds max allowed value "
                    + MAX_APPENDER_ID);
        }
        //should never happen: negative value
        throw new IllegalArgumentException("Invalid appender ID " + appenderId);
    }
    public static long validatePayloadPosition(final long payloadPosition) {
        if (validPayloadPosition(payloadPosition)) {
            return payloadPosition;
        }
        if (payloadPosition > MAX_PAYLOAD_POSITION) {
            throw new IllegalArgumentException("Payload position " + payloadPosition + " exceeds max allowed value "
                    + MAX_PAYLOAD_POSITION);
        }
        //should never happen: negative or not multiple of PAYLOAD_GRANULARITY
        throw new IllegalArgumentException("Invalid payload position " + payloadPosition);
    }

    public static long header(final int appenderId, final long payloadPosition) {
        assert validAppenderId(appenderId) : "appenderId must be valid";
        assert validPayloadPosition(payloadPosition) : "payloadPosition must be valid";
        final long adjustedPosition = payloadPosition + PAYLOAD_POSITION_ADJUSTMENT;
        return (appenderId & APPENDER_ID_HEADER_MASK) | ((adjustedPosition << ADJUSTED_POSITION_SHIFT) & ADJUSTED_POSITION_HEADER_MASK);
    }

    public static boolean hasNonEmptyHeaderAt(final OffsetMapping header, final long index) {
        if (index < Index.FIRST || index > Index.MAX) {
            return false;
        }
        final long originalPosition = header.position();
        final boolean result = moveAndHeaderNonEmpty(header, index);
        if (originalPosition != NULL_POSITION) {
            header.moveTo(originalPosition);
        }
        return result;
    }

    private static boolean moveAndHeaderNonEmpty(final OffsetMapping header, final long index) {
        final long position = HEADER_WORD.position(index);
        return header.moveTo(position) && header.buffer().getLongVolatile(0) != NULL_HEADER;
    }

    private static long mid(final long a, final long b) {
        return (a >>> 1) + (b >>> 1) + (a & b & 0x1L);
    }

    public static long binarySearchLastIndex(final OffsetMapping header, final long startIndex) {
        if (startIndex < Index.FIRST || startIndex > Index.MAX) {
            throw new IllegalArgumentException("Invalid start index: " + startIndex);
        }
        //1) initial low
        if (!hasNonEmptyHeaderAt(header, startIndex)) {
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
            } while (moveAndHeaderNonEmpty(header, highIndex));
        }

        //3) find middle
        if (highIndex != Index.NULL) {
            while (lowIndex + 1L < highIndex) {
                final long midIndex = mid(lowIndex, highIndex);
                if (moveAndHeaderNonEmpty(header, midIndex)) {
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
