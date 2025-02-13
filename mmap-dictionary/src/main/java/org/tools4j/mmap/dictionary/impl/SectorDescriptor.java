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
import org.tools4j.mmap.region.impl.Constants;

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
     * Ignoring the power-of-two constraint, the maximum number of sectorSlots would be
     * <pre>
     *   sectorSlots =      HEADER_POSITION_MASK / (KEY_LENGTH + VALUE_LENGTH + HASH_LENGTH)
     *         = 9,223,372,036,854,775,800 / 24
     *         =   384,307,168,202,282,325
     * </pre>
     * The next smaller power of two is 2^58 = 288,230,376,151,711,744
     */
    public static final int MAX_SLOTS_BITS = 58;

    /**
     * Maximum number of sectorSlots is 2^58 = 288,230,376,151,711,744.
     * <p>
     * For calculation details see {@link #MAX_SLOTS_BITS}
     */
    public static final long MAX_SLOTS = 1L<<MAX_SLOTS_BITS;

    /**
     * Minimum mapping size is typically 4K (see {@link Constants#REGION_SIZE_GRANULARITY}).
     * <p>
     * As have to store key, value and hash we need at least 3x the minimum region size.
     * This translates to 512 x 3 x 8 = 12K bytes, which means we have 2^9 = 512 sectorSlots.
     */
    public static final int MIN_SLOTS_BITS = 9;

    /**
     * Minimum number of sectorSlots is 2^9 = 512.
     * <p>
     * For calculation details see {@link #MIN_SLOTS_BITS}
     */
    public static final long MIN_SLOTS = 1L << MIN_SLOTS_BITS;

    /**
     * Minimum file size for sector data file is MIN_SLOTS * 3 * 8 = 12K bytes.
     * <p>
     * For calculation details see {@link #MIN_SLOTS_BITS}
     */
    public static final long MIN_SECTOR_SIZE = (MIN_SLOTS << KEY_VALUE_SHIFT) + (MIN_SLOTS << HASH_SHIFT);

    /**
     * Minimum file size for sector data file is MAX_SLOTS * 3 * 8, which results in the astronomical number of
     * 6,917,529,027,641,081,856 bytes, or almost 7 million terabytes.
     * <p>
     * For calculation details see {@link #MAX_SLOTS_BITS}.
     */
    public static final long MAX_SECTOR_SIZE = (MAX_SLOTS << KEY_VALUE_SHIFT) + (MAX_SLOTS << HASH_SHIFT);

    /**
     * Maximum number of sectors is 50.
     */
    public static final int MAX_SECTORS = MAX_SLOTS_BITS - MIN_SLOTS_BITS + 1;

    static long length(final long sectorSlots) {
        return (sectorSlots << KEY_VALUE_SHIFT) + (sectorSlots << HASH_SHIFT);
    }

    static long keyValueOffset(final long slot) {
        return slot << KEY_VALUE_SHIFT;
    }

    static long hashOffset(final long sectorSlots, final long slot) {
        assert slot < sectorSlots : "slot exceeds sectorSlots";
        return (sectorSlots << KEY_VALUE_SHIFT) + (slot << HASH_SHIFT);
    }

    static long slot(final long hash, final long sectorSlots) {
        assert BitUtil.isPowerOfTwo(sectorSlots) : "sectorSlots must be a power of two";
        return hash & (sectorSlots - 1);
    }

    static long moveToAndGetKeyHeaderVolatile(final OffsetMapping mapping, final long startPosition, final long slot) {
        final long position = startPosition + keyValueOffset(slot) + KEY_OFFSET;
        return moveToAndGetVolatile(mapping, position);
    }
    
    static long moveToAndGetValueHeaderVolatile(final OffsetMapping mapping, final long startPosition, final long slot) {
        final long position = startPosition + keyValueOffset(slot) + VALUE_OFFSET;
        return moveToAndGetVolatile(mapping, position);
    }

    static long moveToAndGetHash(final OffsetMapping mapping,
                                 final long startPosition,
                                 final long sectorSlots,
                                 final long slot) {
        final long position = startPosition + hashOffset(sectorSlots, slot) + HASH_OFFSET;
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
