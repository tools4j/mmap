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
package org.tools4j.mmap.core.collections;

import org.tools4j.mmap.core.function.LongLongConsumer;

import java.util.Arrays;

/**
 * Hash map from long keys to long values, without boxing: open addressing with linear probing in two arrays, which
 * grow when half full. A value chosen on construction, the missing value, stands for "no entry": {@link #get(long)}
 * returns it for absent keys, and it cannot be stored.
 * <p>
 * Allocates only when growing, so a map created with a large enough expected size allocates nothing once filled.
 * Keys that are multiples of a large power of two, such as region positions, are spread by Fibonacci hashing.
 * <p>
 * Not thread-safe.
 */
public final class LongLongMap {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 1 << 30;
    /** The maximum expected size, half of the maximum length. */
    public static final int MAX_SIZE = MAX_LENGTH / 2;
    private static final long FIBONACCI_MULTIPLIER = 0x9E3779B97F4A7C15L;

    private final long missingValue;
    private long[] keys;
    private long[] values;
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
        allocate(Math.max(MIN_LENGTH, Integer.highestOneBit(Math.max(1, expectedSize * 2 - 1)) << 1));
    }

    private void allocate(final int length) {
        keys = new long[length];
        values = new long[length];
        if (missingValue != 0) {
            Arrays.fill(values, missingValue);
        }
        mask = length - 1;
        shift = Long.SIZE - Integer.numberOfTrailingZeros(length);
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
        final long[] keys = this.keys;
        final long[] values = this.values;
        for (int index = home(key); ; index = next(index)) {
            final long value = values[index];
            if (value == missingValue || keys[index] == key) {
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
        int index = home(key);
        for (long current; (current = values[index]) != missingValue; index = next(index)) {
            if (keys[index] == key) {
                values[index] = value;
                return current;
            }
        }
        if (size == (mask + 1) / 2) {
            grow();
            index = freeIndex(key);
        }
        keys[index] = key;
        values[index] = value;
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
        int index = home(key);
        for (long value; (value = values[index]) != missingValue; index = next(index)) {
            if (keys[index] == key) {
                values[index] = missingValue;
                size--;
                closeGap(index);
                return value;
            }
        }
        return missingValue;
    }

    /**
     * Moves entries after a removed one back into the gap, where their probe sequence would otherwise end early.
     */
    private void closeGap(final int removed) {
        int gap = removed;
        for (int index = next(gap); values[index] != missingValue; index = next(index)) {
            final int home = home(keys[index]);
            if (((index - home) & mask) >= ((index - gap) & mask)) {
                keys[gap] = keys[index];
                values[gap] = values[index];
                values[index] = missingValue;
                gap = index;
            }
        }
    }

    /**
     * Removes all entries, keeping the capacity.
     */
    public void clear() {
        if (size > 0) {
            Arrays.fill(values, missingValue);
            size = 0;
        }
    }

    /**
     * Performs an action for each entry, in no particular order; the action must not modify this map.
     *
     * @param action the action accepting key and value
     */
    public void forEach(final LongLongConsumer action) {
        final long[] keys = this.keys;
        final long[] values = this.values;
        for (int index = 0; index < values.length; index++) {
            final long value = values[index];
            if (value != missingValue) {
                action.accept(keys[index], value);
            }
        }
    }

    private int home(final long key) {
        return (int) ((key * FIBONACCI_MULTIPLIER) >>> shift);
    }

    private int next(final int index) {
        return (index + 1) & mask;
    }

    private int freeIndex(final long key) {
        int index = home(key);
        while (values[index] != missingValue) {
            index = next(index);
        }
        return index;
    }

    private void grow() {
        final int length = mask + 1;
        if (length == MAX_LENGTH) {
            throw new IllegalStateException("Map cannot grow beyond " + MAX_SIZE + " entries");
        }
        final long[] oldKeys = keys;
        final long[] oldValues = values;
        allocate(length * 2);
        for (int index = 0; index < oldValues.length; index++) {
            final long value = oldValues[index];
            if (value != missingValue) {
                final int free = freeIndex(oldKeys[index]);
                keys[free] = oldKeys[index];
                values[free] = value;
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
