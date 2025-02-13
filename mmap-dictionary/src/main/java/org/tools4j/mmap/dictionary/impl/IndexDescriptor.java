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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;

import static org.tools4j.mmap.dictionary.impl.SectorDescriptor.MAX_SECTORS;

/**
 * Describes the layout of the dictionary index file:
 * <pre>

    0         1         2         3         4         5         6
    0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |            (Unused)           |      First Sector Entry       |
    +=======+=======+=======+=======+=======+=======+=======+=======+
    |                     Sector Start Position                     |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                          Active Slots                         |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                           Used Slots                          |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |    Log2 Sector Slots (Bits)   |        Next Sector Entry      |
    +=======+=======+=======+=======+=======+=======+=======+=======+
    |                     Sector Start Position                     |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                          Active Slots                         |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |                           Used Slots                          |
    +-------+-------+-------+-------+-------+-------+-------+-------+
    |    Log2 Sector Slots (Bits)   |       Next Sector Entry       |
    +=======+=======+=======+=======+=======+=======+=======+=======+
    |                              ...                              |

 * </pre>
 */
public enum IndexDescriptor {
    ;
    //offsets from start of file
    public static final int UNUSED_OFFSET = 0;
    public static final int UNUSED_LENGTH = Integer.BYTES;
    public static final int FIRST_ENTRY_OFFSET = UNUSED_OFFSET + UNUSED_LENGTH;
    public static final int FIRST_ENTRY_LENGTH = Integer.BYTES;
    public static final int ENTRY_0_OFFSET = FIRST_ENTRY_OFFSET + FIRST_ENTRY_LENGTH;

    //offsets relative to sector entry offset
    public static final int START_POSITION_OFFSET = 0;
    public static final int START_POSITION_LENGTH = Long.BYTES;
    public static final int ACTIVE_SLOTS_OFFSET = START_POSITION_OFFSET + START_POSITION_LENGTH;
    public static final int ACTIVE_SLOTS_LENGTH = Long.BYTES;
    public static final int USED_SLOTS_OFFSET = ACTIVE_SLOTS_OFFSET + ACTIVE_SLOTS_LENGTH;
    public static final int USED_SLOTS_LENGTH = Long.BYTES;
    public static final int SECTOR_SLOTS_OFFSET = USED_SLOTS_OFFSET + USED_SLOTS_LENGTH;
    public static final int SECTOR_SLOTS_LENGTH = Integer.BYTES;
    public static final int NEXT_ENTRY_OFFSET = SECTOR_SLOTS_OFFSET + SECTOR_SLOTS_LENGTH;
    public static final int NEXT_ENTRY_LENGTH = Integer.BYTES;

    public static final int ENTRY_LENGTH = START_POSITION_LENGTH + ACTIVE_SLOTS_LENGTH + USED_SLOTS_LENGTH
                                            + SECTOR_SLOTS_LENGTH + NEXT_ENTRY_LENGTH;

    public static final int FILE_SIZE = ENTRY_0_OFFSET + MAX_SECTORS * ENTRY_LENGTH;

    static int firstSectorIndexVolatile(final AtomicBuffer buffer) {
        return buffer.getIntVolatile(FIRST_ENTRY_OFFSET);
    }

    static int sectorOffset(final int sector) {
        assert sector >= 0 : "invalid sector";
        return ENTRY_0_OFFSET + sector * ENTRY_LENGTH;
    }

    static long sectorStartPosition(final DirectBuffer buffer, final int sector) {
        return buffer.getLong(sectorOffset(sector) + START_POSITION_OFFSET);
    }

    static long sectorActiveSlotsVolatile(final AtomicBuffer buffer, final int sector) {
        return buffer.getLongVolatile(sectorOffset(sector) + ACTIVE_SLOTS_OFFSET);
    }

    static long sectorActiveSlotsIncrementAndGet(final AtomicBuffer buffer, final int sector) {
        final int offset = sectorOffset(sector) + ACTIVE_SLOTS_OFFSET;
        final long slots = CompareAndSet.incrementAndGet(buffer, offset);
        if (slots >= 0) {
            return slots;
        }
        throw new IllegalStateException("Cannot increment active slots for sector " + sector + " to " + slots);
    }

    static long sectorUsedSlotsVolatile(final AtomicBuffer buffer, final int sector) {
        return buffer.getLongVolatile(sectorOffset(sector) + USED_SLOTS_OFFSET);
    }

    static long sectorUsedSlotsIncrementAndGet(final AtomicBuffer buffer, final int sector) {
        final int offset = sectorOffset(sector) + USED_SLOTS_OFFSET;
        final long slots = CompareAndSet.incrementAndGet(buffer, offset);
        if (slots >= 0) {
            return slots;
        }
        throw new IllegalStateException("Cannot increment used slots for sector " + sector + " to " + slots);
    }

    static long sectorUsedSlotsDecrementAndGet(final AtomicBuffer buffer, final int sector) {
        final int offset = sectorOffset(sector) + USED_SLOTS_OFFSET;
        final long slots = CompareAndSet.decrementAndGet(buffer, offset);
        if (slots >= 0) {
            return slots;
        }
        throw new IllegalStateException("Cannot decrement used slots for sector " + sector + " to " + slots);
    }

    static int sectorSlotsInBits(final DirectBuffer buffer, final int sector) {
        return buffer.getInt(sectorOffset(sector) + SECTOR_SLOTS_OFFSET);
    }

    static int nextSectorVolatile(final AtomicBuffer buffer, final int sector) {
        return buffer.getIntVolatile(sectorOffset(sector) + NEXT_ENTRY_OFFSET);
    }

    static boolean sectorInit(final AtomicBuffer buffer,
                              final int sector,
                              final long startPosition,
                              final int sectorSlotsInBits,
                              final boolean performChecks) {
        //assert sector: done in sectorOffset(..)
        assert startPosition >= 0 : "invalid startPosition";
        assert sectorSlotsInBits >= 0 : "invalid sectorSlotsInBits";
        final int offset = sectorOffset(sector);
        if (performChecks) {
            long value;
            if ((value = buffer.getLongVolatile(offset + USED_SLOTS_OFFSET)) != 0) {
                throw new IllegalArgumentException("Used slots for sector " + sector + " should be zero but is " + value);
            }
            if ((value = buffer.getLongVolatile(offset + ACTIVE_SLOTS_OFFSET)) != 0) {
                throw new IllegalArgumentException("Active slots for sector " + sector + " should be zero but is " + value);
            }
            if ((value = buffer.getIntVolatile(offset + NEXT_ENTRY_OFFSET)) != 0) {
                throw new IllegalArgumentException("Next sector for sector " + sector + " should be zero but is " + value);
            }
        }
        buffer.putLong(offset + START_POSITION_OFFSET, startPosition);
        buffer.putLong(offset + SECTOR_SLOTS_OFFSET, sectorSlotsInBits);
        final int prevOffset = sector == 0 ? FIRST_ENTRY_OFFSET : (sectorOffset(sector - 1) + NEXT_ENTRY_OFFSET);

        //NOTE: - returning false below means another thread beat us to it
        //      - it is highly likely that it set exactly the same value that we wanted to set
        //      - if it is not the same value, then the next section was created and has already been outgrown
        final boolean prevSet = buffer.compareAndSetInt(prevOffset, 0, sector);
        if (!prevSet && performChecks) {
            final int current = buffer.getIntVolatile(prevOffset);
            if (current != sector) {
                throw new IllegalStateException("Expected previous sector to point to " + sector +
                        " but it points to " + current);
            }
        }
        return prevSet;
    }
}
