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

import org.tools4j.mmap.core.function.LongLongConsumer;

import java.util.Arrays;

/**
 * Hash map from long keys to long values, without boxing, for the region mappers. A value chosen on construction, the
 * missing value, stands for "no entry": {@link #get(long)} returns it for absent keys, and it cannot be stored.
 * Allocates only when growing, so a map created with a large enough expected size allocates nothing once filled.
 * <p>
 * Not thread-safe.
 *
 * <h2>Layout</h2>
 * Open addressing with linear probing in one array of interleaved keys and values, {@code [k0, v0, k1, v1, ...]}, so
 * that a probe, which reads the value (is the slot empty?) and the key, usually touches one cache line rather than one
 * in each of two arrays. The number of slots is a power of two. Removing an entry moves the following entries of its
 * probe sequence back into the gap where they would otherwise end it early, so there are no deleted-entry markers
 * that would lengthen probe sequences over time.
 *
 * <h2>Hashing</h2>
 * The keys are mostly region positions, multiples of the region size, a power of two of at least the page size, so
 * their low bits are all zero: indexing with the low bits ({@code key & mask}) would put all of them into slot 0, and
 * folding ({@code key ^ key >>> 32}) leaves the low bits zero for regions below 4 GB. Fibonacci hashing multiplies by
 * 2<sup>64</sup>/&phi; and takes the top bits of the product, which depend on all bits of the key: one multiplication
 * and one shift. Consecutive multiples, the usual access pattern, land almost evenly spaced across the table, fewer
 * collisions than even random hashing; a full mixer such as Murmur3's finalizer would cost three multiply-xor rounds
 * for no benefit with these keys. Only key sets built against the multiplier collide, which positions in a file are
 * not.
 *
 * <h2>Load factor</h2>
 * The table grows when half full. With linear probing, probe counts rise steeply with the load factor &alpha;, and a
 * miss costs more than a hit; Knuth's estimates for uniform hashing:
 * <table>
 *     <caption>Expected probes with linear probing</caption>
 *     <tr><th>&alpha;</th><th>key found</th><th>key absent</th></tr>
 *     <tr><td>0.50</td><td>1.5</td><td>2.5</td></tr>
 *     <tr><td>0.65</td><td>1.9</td><td>4.6</td></tr>
 *     <tr><td>0.75</td><td>2.5</td><td>8.5</td></tr>
 * </table>
 * Misses are frequent here, as every cache miss and every insert searches for an absent key, and clusters grow fast
 * beyond about 0.7. The memory is irrelevant in comparison: 16 bytes per slot, 4 KB for 128 regions at 0.5.
 */
public final class LongLongMap {

    private static final int MIN_SLOTS = 8;
    /** The maximum number of slots, so that the interleaved array, two longs per slot, fits into an int length. */
    private static final int MAX_SLOTS = 1 << 29;
    /** The maximum expected size, half of the maximum number of slots. */
    public static final int MAX_SIZE = MAX_SLOTS / 2;
    /** 2<sup>64</sup>/&phi; = 2<sup>64</sup> &times; (&radic;5 &minus; 1) / 2, rounded down (an odd number). */
    private static final long FIBONACCI_MULTIPLIER = 0x9E3779B97F4A7C15L;

    private final long missingValue;
    /** Key of slot i at 2i, value at 2i + 1; the value is the missing value for an empty slot. */
    private long[] entries;
    private int mask;
    private int shift;
    private int size;

    /**
     * Creates a map holding up to an expected size without growing.
     *
     * @param expectedSize the number of entries the map holds without growing, at most {@link #MAX_SIZE}
     * @param missingValue the value standing for "no entry", which cannot be stored
     * @throws IllegalArgumentException if the expected size is negative or greater than {@link #MAX_SIZE}
     */
    public LongLongMap(final int expectedSize, final long missingValue) {
        if (expectedSize < 0 || expectedSize > MAX_SIZE) {
            throw new IllegalArgumentException("Expected size " + expectedSize + " must be in [0, " + MAX_SIZE + "]");
        }
        this.missingValue = missingValue;
        allocate(Math.max(MIN_SLOTS, Integer.highestOneBit(Math.max(1, expectedSize * 2 - 1)) << 1));
    }

    private void allocate(final int slots) {
        entries = new long[2 * slots];
        if (missingValue != 0) {
            for (int slot = 0; slot < slots; slot++) {
                entries[valueIndex(slot)] = missingValue;
            }
        }
        mask = slots - 1;
        shift = Long.SIZE - Integer.numberOfTrailingZeros(slots);
    }

    /**
     * Returns the value standing for "no entry".
     *
     * @return the missing value
     */
    public long missingValue() {
        return missingValue;
    }

    /**
     * Returns the number of entries.
     *
     * @return the number of entries
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether this map has no entries.
     *
     * @return true if this map is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the value for a key.
     *
     * @param key the key
     * @return the value, or the {@linkplain #missingValue() missing value} if there is no entry for the key
     */
    public long get(final long key) {
        final long[] entries = this.entries;
        for (int slot = home(key); ; slot = next(slot)) {
            final long value = entries[valueIndex(slot)];
            if (value == missingValue || entries[keyIndex(slot)] == key) {
                return value;
            }
        }
    }

    /**
     * Returns whether there is an entry for a key.
     *
     * @param key the key
     * @return true if there is an entry for the key
     */
    public boolean containsKey(final long key) {
        return get(key) != missingValue;
    }

    /**
     * Stores a value for a key, replacing the previous value if there was one.
     *
     * @param key   the key
     * @param value the value, not the {@linkplain #missingValue() missing value}
     * @return the previous value, or the missing value if there was no entry for the key
     * @throws IllegalArgumentException if the value is the missing value
     * @throws IllegalStateException    if the map would have to grow beyond {@link #MAX_SIZE} entries
     */
    public long put(final long key, final long value) {
        if (value == missingValue) {
            throw new IllegalArgumentException("Cannot store the missing value " + value + " for key " + key);
        }
        int slot = home(key);
        for (long current; (current = entries[valueIndex(slot)]) != missingValue; slot = next(slot)) {
            if (entries[keyIndex(slot)] == key) {
                entries[valueIndex(slot)] = value;
                return current;
            }
        }
        if (size == (mask + 1) / 2) {
            grow();
            slot = freeSlot(key);
        }
        entries[keyIndex(slot)] = key;
        entries[valueIndex(slot)] = value;
        size++;
        return missingValue;
    }

    /**
     * Removes the entry for a key.
     *
     * @param key the key
     * @return the removed value, or the {@linkplain #missingValue() missing value} if there was no entry for the key
     */
    public long remove(final long key) {
        int slot = home(key);
        for (long value; (value = entries[valueIndex(slot)]) != missingValue; slot = next(slot)) {
            if (entries[keyIndex(slot)] == key) {
                entries[valueIndex(slot)] = missingValue;
                size--;
                closeGap(slot);
                return value;
            }
        }
        return missingValue;
    }

    /**
     * Moves entries after a removed one back into the gap, where their probe sequence would otherwise end early: an
     * entry can move if the gap lies between its home slot and its slot, cyclically.
     */
    private void closeGap(final int removed) {
        int gap = removed;
        for (int slot = next(gap); entries[valueIndex(slot)] != missingValue; slot = next(slot)) {
            final int home = home(entries[keyIndex(slot)]);
            if (((slot - home) & mask) >= ((slot - gap) & mask)) {
                entries[keyIndex(gap)] = entries[keyIndex(slot)];
                entries[valueIndex(gap)] = entries[valueIndex(slot)];
                entries[valueIndex(slot)] = missingValue;
                gap = slot;
            }
        }
    }

    /**
     * Removes all entries, keeping the capacity.
     */
    public void clear() {
        if (size > 0) {
            if (missingValue == 0) {
                Arrays.fill(entries, 0);
            } else {
                for (int slot = 0; slot <= mask; slot++) {
                    entries[valueIndex(slot)] = missingValue;
                }
            }
            size = 0;
        }
    }

    /**
     * Performs an action for each entry, in no particular order; the action must not modify this map.
     *
     * @param action the action accepting key and value
     */
    public void forEach(final LongLongConsumer action) {
        final long[] entries = this.entries;
        for (int index = 0; index < entries.length; index += 2) {
            final long value = entries[index + 1];
            if (value != missingValue) {
                action.accept(entries[index], value);
            }
        }
    }

    private static int keyIndex(final int slot) {
        return slot << 1;
    }

    private static int valueIndex(final int slot) {
        return (slot << 1) + 1;
    }

    private int home(final long key) {
        return (int) ((key * FIBONACCI_MULTIPLIER) >>> shift);
    }

    private int next(final int slot) {
        return (slot + 1) & mask;
    }

    private int freeSlot(final long key) {
        int slot = home(key);
        while (entries[valueIndex(slot)] != missingValue) {
            slot = next(slot);
        }
        return slot;
    }

    private void grow() {
        final int slots = mask + 1;
        if (slots == MAX_SLOTS) {
            throw new IllegalStateException("Map cannot grow beyond " + MAX_SIZE + " entries");
        }
        final long[] oldEntries = entries;
        allocate(slots * 2);
        for (int index = 0; index < oldEntries.length; index += 2) {
            final long value = oldEntries[index + 1];
            if (value != missingValue) {
                final int free = freeSlot(oldEntries[index]);
                entries[keyIndex(free)] = oldEntries[index];
                entries[valueIndex(free)] = value;
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder string = new StringBuilder("LongLongMap{");
        forEach((key, value) -> string.append(string.length() > 12 ? ", " : "").append(key).append('=').append(value));
        return string.append('}').toString();
    }
}
