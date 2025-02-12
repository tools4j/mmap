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

import org.agrona.BitUtil;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.tools4j.mmap.dictionary.impl.Exceptions.mappingMoveException;

/**
 * Describes the layout of a sector:
 * <pre>

    0         1         2         3         4         5         6
    0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                          Key Header                           |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                         Value Header                          |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                          Key Header                           |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                         Value Header                          |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                              ...                              |
    +=======+=======+=======+=======+=======+=======+=======+=======+
    |                           Key Hash                            |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                           Key Hash                            |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                              ...                              |
    +=======+=======+=======+=======+=======+=======+=======+=======+

 * </pre>
 */
enum SectorDescriptor {
    ;
    /** KVH stands for key-value-hash */
    public static final String NAME = "kvh";
    public static final int KEY_OFFSET = 0;
    public static final int KEY_LENGTH = Long.BYTES;
    public static final int VALUE_OFFSET = KEY_OFFSET + KEY_LENGTH;
    public static final int VALUE_LENGTH = Long.BYTES;
    public static final int KEY_VALUE_LENGTH = KEY_LENGTH + VALUE_LENGTH;
    public static final int KEY_VALUE_SHIFT = 4;

    public static final int HASH_OFFSET = 0;
    public static final int HASH_LENGTH = Long.BYTES;
    public static final int HASH_SHIFT = 3;

    /**
     * Ignoring the power-of-two constraint, the maximum number of slots would be
     * <pre>
     *   slots =      HEADER_POSITION_MASK / (KEY_LENGTH + VALUE_LENGTH + HASH_LENGTH)
     *         = 9,223,372,036,854,775,800 / 24
     *         =   384,307,168,202,282,325
     * </pre>
     * The next smaller power of two is 2^58 = 288,230,376,151,711,744
     */
    public static final int MAX_SLOTS_BITS = 58;

    /**
     * Maximum number of slots is 2^58 = 288,230,376,151,711,744.
     * <p>
     * For the calculation see {@link #MAX_SLOTS_BITS}
     */
    public static final long MAX_SLOTS = 1L<<MAX_SLOTS_BITS;

    static long length(final long slots) {
        return (slots << KEY_VALUE_SHIFT) + (slots << HASH_SHIFT);
    }

    static long keyValueOffset(final long entry) {
        return entry << KEY_VALUE_SHIFT;
    }

    static long hashOffset(final long slots, final long entry) {
        assert entry < slots : "entry exceeds slots";
        return (slots << KEY_VALUE_SHIFT) + (entry << HASH_SHIFT);
    }

    static long entry(final long hash, final long slots) {
        assert BitUtil.isPowerOfTwo(slots) : "slots must be a power of two";
        return hash & (slots - 1);
    }

    static long moveToAndGetKeyHeaderVolatile(final OffsetMapping mapping, final long startPosition, final long entry) {
        final long position = startPosition + keyValueOffset(entry) + KEY_OFFSET;
        return moveToAndGetVolatile(mapping, position);
    }
    
    static long moveToAndGetValueHeaderVolatile(final OffsetMapping mapping, final long startPosition, final long entry) {
        final long position = startPosition + keyValueOffset(entry) + VALUE_OFFSET;
        return moveToAndGetVolatile(mapping, position);
    }

    static long moveToAndGetHash(final OffsetMapping mapping,
                                 final long startPosition,
                                 final long sectorSlots,
                                 final long entry) {
        final long position = startPosition + hashOffset(sectorSlots, entry) + HASH_OFFSET;
        if (mapping.moveTo(position)) {
            return mapping.buffer().getLong(0, LITTLE_ENDIAN);
        }
        throw mappingMoveException(NAME, position);
    }

    private static long moveToAndGetVolatile(final OffsetMapping mapping, final long position) {
        if (mapping.moveTo(position)) {
            return mapping.buffer().getLongVolatile(0);
        }
        throw mappingMoveException(NAME, position);
    }
}
