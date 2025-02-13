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
package org.tools4j.mmap.dictionary.impl;

import org.tools4j.mmap.region.impl.IdPool256;

/**
 * Methods to encode and decode header values, and related methods.
 */
enum Headers {
    ;
    public static final int HEADER_LENGTH = Long.BYTES;
    private static final int HEADER_SHIFT = 3;

    public static final long NULL_HEADER = 0L;
    public static final long TOMBSTONE_BIT = 1L<<63;
    public static final long TOMBSTONE_HEADER = -1L;

    /**
     * Header position cannot be negative and must be a multiple of {@link #HEADER_LENGTH}
     */
    private static final long HEADER_POSITION_MASK = Long.MAX_VALUE & -HEADER_LENGTH;

    /**
     * This is the logarithm base 2 of {@link #PAYLOAD_GRANULARITY} used to shift the payload position to erase the
     * unused zero bits.
     */
    public static final int PAYLOAD_GRANULARITY_BITS = 3;

    /**
     * Payload granularity is 8 bytes, which gives good word alignment and very little wasted storage.
     * Note that we always store an integer for the payload size. This means that at most 4 bytes are wasted to padding
     * which only happens for zero payload entries.
     */
    public static final long PAYLOAD_GRANULARITY = 1L << PAYLOAD_GRANULARITY_BITS;
    /**
     * One byte for updater ID
     */
    public static final int UPDATER_ID_BITS = Byte.SIZE;
    /**
     * At most 256 updaters, whose IDs can for instance be managed by {@link IdPool256}
     */
    public static final int MAX_UPDATERS = 1 << UPDATER_ID_BITS;
    /**
     * Maximum allowed updater ID is 255.
     */
    public static final int MAX_UPDATER_ID = MAX_UPDATERS - 1;
    /**
     * Minimum allowed updater ID is 0.
     */
    public static final int MIN_UPDATER_ID = 0;

    private static final long UPDATER_ID_MASK = MAX_UPDATERS - 1;
    private static final long UPDATER_ID_HEADER_MASK = UPDATER_ID_MASK;
    private static final int ADJUSTED_POSITION_SHIFT = UPDATER_ID_BITS - PAYLOAD_GRANULARITY_BITS;
    private static final long ADJUSTED_POSITION_HEADER_MASK = ~UPDATER_ID_HEADER_MASK;
    private static final long ADJUSTED_POSITION_MASK = ADJUSTED_POSITION_HEADER_MASK >>> ADJUSTED_POSITION_SHIFT;

    private static final long PAYLOAD_POSITION_ADJUSTMENT = PAYLOAD_GRANULARITY;
    /**
     * Max payload position is 576,460,752,303,423,472, which is > 500,000 terabytes.
     */
    public static final long MAX_PAYLOAD_POSITION = ADJUSTED_POSITION_MASK - PAYLOAD_POSITION_ADJUSTMENT;

    public static int updaterId(final long header) {
        return (int) (header & UPDATER_ID_HEADER_MASK);
    }

    public static long payloadPosition(final long header) {
        return ((header & ADJUSTED_POSITION_HEADER_MASK) >>> ADJUSTED_POSITION_SHIFT) - PAYLOAD_POSITION_ADJUSTMENT;
    }

    private static long payloadBytesWithPadding(final int payloadBytes) {
        return ((payloadBytes + PAYLOAD_GRANULARITY - 1) >>> PAYLOAD_GRANULARITY_BITS) << PAYLOAD_GRANULARITY_BITS;
    }

    public static long nextPayloadPosition(final long currentPayloadPosition, final int currentPayloadBytes) {
        assert validPayloadPosition(currentPayloadPosition) : "currentPayloadPosition must be valid";
        assert currentPayloadBytes >= 0 : "currentPayloadBytes must not be negative";
        return currentPayloadPosition + payloadBytesWithPadding(currentPayloadBytes);
    }

    public static boolean validUpdaterId(final int updaterId) {
        return updaterId == (updaterId & UPDATER_ID_MASK);
    }

    public static boolean validPayloadPosition(final long payloadPosition) {
        final long adjustedPosition = payloadPosition + PAYLOAD_POSITION_ADJUSTMENT;
        return payloadPosition >= 0 && adjustedPosition == (adjustedPosition & ADJUSTED_POSITION_MASK);
    }

    public static boolean validHeaderPosition(final long headerPosition) {
        return headerPosition == (headerPosition & HEADER_POSITION_MASK);
    }

    public static int validateUpdaterId(final int updaterId) {
        if (validUpdaterId(updaterId)) {
            return updaterId;
        }
        if (updaterId > MAX_UPDATER_ID) {
            throw new IllegalArgumentException("Updater ID " + updaterId + " exceeds max allowed value "
                    + MAX_UPDATER_ID);
        }
        //should never happen: negative value
        throw new IllegalArgumentException("Invalid updater ID " + updaterId);
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

    public static long header(final int updaterId, final long payloadPosition) {
        assert validUpdaterId(updaterId) : "updaterId is invalid";
        assert validPayloadPosition(payloadPosition) : "payloadPosition is invalid";
        final long adjustedPosition = payloadPosition + PAYLOAD_POSITION_ADJUSTMENT;
        return (updaterId & UPDATER_ID_HEADER_MASK) | ((adjustedPosition << ADJUSTED_POSITION_SHIFT) & ADJUSTED_POSITION_HEADER_MASK);
    }
}
