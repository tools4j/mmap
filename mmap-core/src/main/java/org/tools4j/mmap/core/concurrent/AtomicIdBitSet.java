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
package org.tools4j.mmap.core.concurrent;

import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

import static java.util.Objects.requireNonNull;

/**
 * Lock-free bit set of IDs in a {@link MutableMemoryBuffer}, acquiring the lowest free ID with compare-and-set. The
 * buffer can be a mapped file shared across processes, as used by the appender ID pool of a queue, or memory
 * {@linkplain #allocate(int) allocated} for use within this process. Bit sets with different max IDs may share a
 * buffer, each acquiring only IDs below its own max.
 */
public final class AtomicIdBitSet {
    /** Returned by {@link #acquire()} if no ID is free */
    public static final int NO_ID = -1;

    private final MutableMemoryBuffer buffer;
    private final int maxIds;
    private final int lastWordIndex;
    private final long lastWordMask;

    /**
     * Creates a bit set in the given buffer.
     *
     * @param buffer the buffer with at least {@link #bytes(int) bytes(maxIds)} bytes, supporting ordered access, i.e.
     *               direct memory aligned to 8 bytes or {@code long[]}-backed heap memory
     * @param maxIds the max IDs, only IDs below this value are acquired
     * @throws IllegalArgumentException if maxIds is not positive or the buffer is too small
     */
    public AtomicIdBitSet(final MutableMemoryBuffer buffer, final int maxIds) {
        if (maxIds < 1) {
            throw new IllegalArgumentException("Max IDs must be positive but was " + maxIds);
        }
        if (buffer.capacity() < bytes(maxIds)) {
            throw new IllegalArgumentException("Buffer capacity " + buffer.capacity() + " is too small for " +
                    maxIds + " IDs, at least " + bytes(maxIds) + " bytes are required");
        }
        this.buffer = requireNonNull(buffer);
        this.maxIds = maxIds;
        this.lastWordIndex = (maxIds - 1) / Long.SIZE * Long.BYTES;
        final int lastWordIds = maxIds - lastWordIndex / Long.BYTES * Long.SIZE;
        this.lastWordMask = -1L >>> (Long.SIZE - lastWordIds);
    }

    /**
     * Creates a bit set in newly allocated memory, for use within this process; allocates a heap buffer backed by a
     * {@code long[]}, once per bit set.
     *
     * @param maxIds the max IDs
     * @return a new bit set with no acquired IDs
     */
    public static AtomicIdBitSet allocate(final int maxIds) {
        return new AtomicIdBitSet(MemoryBuffers.allocate(bytes(Math.max(1, maxIds))), maxIds);
    }

    /**
     * Returns the bytes needed for a bit set with the given max IDs.
     *
     * @param maxIds the max IDs
     * @return the bytes, a multiple of 8
     */
    public static int bytes(final int maxIds) {
        return (maxIds + Long.SIZE - 1) / Long.SIZE * Long.BYTES;
    }

    /**
     * Returns the max IDs: only IDs below this value are acquired.
     *
     * @return the max IDs
     */
    public int maxIds() {
        return maxIds;
    }

    /**
     * Acquires the lowest free ID.
     *
     * @return the acquired ID, or {@link #NO_ID} if all IDs below max IDs are acquired
     */
    public int acquire() {
        for (int index = 0; index <= lastWordIndex; index += Long.BYTES) {
            final long idMask = index == lastWordIndex ? lastWordMask : -1L;
            long idBitSet;
            long idBit;
            do {
                idBitSet = buffer.getLongAcquire(index);
                idBit = Long.lowestOneBit(~idBitSet & idMask);
            } while (idBit != 0 && !buffer.compareAndSetLong(index, idBitSet, idBitSet | idBit));
            if (idBit != 0) {
                return index / Long.BYTES * Long.SIZE + Long.numberOfTrailingZeros(idBit);
            }
        }
        return NO_ID;
    }

    /**
     * Releases the given ID.
     *
     * @param id the ID to release
     * @return true if the ID was released, and false if it was not acquired
     * @throws IllegalArgumentException if the ID is not in {@code [0, maxIds)}
     */
    public boolean release(final int id) {
        if (id < 0 || id >= maxIds) {
            throw new IllegalArgumentException("Invalid ID: " + id);
        }
        final int index = (id / Long.SIZE) * Long.BYTES;
        final long mask = ~(1L << id);
        long curBitSet;
        long newBitSet;
        do {
            curBitSet = buffer.getLongAcquire(index);
            newBitSet = curBitSet & mask;
        } while (curBitSet != newBitSet && !buffer.compareAndSetLong(index, curBitSet, newBitSet));
        return curBitSet != newBitSet;
    }

    /**
     * Returns the number of acquired IDs in the whole buffer, including IDs acquired by bit sets with a larger max
     * sharing the buffer.
     *
     * @return the acquired IDs
     */
    public int acquired() {
        final int words = buffer.capacity() / Long.BYTES;
        int count = 0;
        for (int index = 0; index < words * Long.BYTES; index += Long.BYTES) {
            count += Long.bitCount(buffer.getLongAcquire(index));
        }
        return count;
    }
}
