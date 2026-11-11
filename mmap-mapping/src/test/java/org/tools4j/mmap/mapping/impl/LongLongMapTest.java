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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link LongLongMap}.
 */
class LongLongMapTest {

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void behavesLikeAHashMap(final long missingValue) {
        final Random random = new Random(missingValue);
        final LongLongMap map = new LongLongMap(4, missingValue);
        final Map<Long, Long> expected = new HashMap<>();
        for (int round = 0; round < 200_000; round++) {
            //few distinct keys, so that removals hit existing entries and probe sequences overlap
            final long key = randomKey(random, 64);
            switch (random.nextInt(3)) {
                case 0:
                    final long value = randomValue(random, missingValue);
                    assertEquals(expected.getOrDefault(key, missingValue), map.put(key, value));
                    expected.put(key, value);
                    break;
                case 1:
                    assertEquals(expected.getOrDefault(key, missingValue), map.remove(key));
                    expected.remove(key);
                    break;
                default:
                    assertEquals(expected.getOrDefault(key, missingValue), map.get(key));
                    assertEquals(expected.containsKey(key), map.containsKey(key));
                    break;
            }
            assertEquals(expected.size(), map.size());
        }
        final Map<Long, Long> visited = new HashMap<>();
        map.forEach((key, value) -> assertEquals(null, visited.put(key, value), "visited once"));
        assertEquals(expected, visited);
    }

    private static long randomKey(final Random random, final int distinctKeys) {
        final long key = random.nextInt(distinctKeys);
        switch ((int) (key % 3)) {
            case 0:
                return key << 30;//like region positions
            case 1:
                return -key;
            default:
                return key * 0x9E3779B97F4A7C15L;
        }
    }

    private static long randomValue(final Random random, final long missingValue) {
        final long value = random.nextInt(3) == 0 ? random.nextLong() : random.nextInt(5) - 2;
        return value == missingValue ? value + 1 : value;
    }

    @Test
    void growsBeyondTheExpectedSize() {
        final LongLongMap map = new LongLongMap(0, -1);
        for (long key = 0; key < 100_000; key++) {
            assertEquals(-1, map.put(key << 21, key));
        }
        assertEquals(100_000, map.size());
        for (long key = 0; key < 100_000; key++) {
            assertEquals(key, map.get(key << 21));
        }
        assertEquals(-1, map.get(1));
    }

    @Test
    void rejectsTheMissingValueAndInvalidSizes() {
        final LongLongMap map = new LongLongMap(4, 7);
        assertThrows(IllegalArgumentException.class, () -> map.put(1, 7));
        assertTrue(map.isEmpty());
        assertEquals(7, map.missingValue());
        assertThrows(IllegalArgumentException.class, () -> new LongLongMap(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LongLongMap(LongLongMap.MAX_SIZE + 1, 0));
    }

    @Test
    void clearRemovesAllEntries() {
        final LongLongMap map = new LongLongMap(4, Long.MIN_VALUE);
        map.put(1, 2);
        map.put(3, 4);
        assertFalse(map.isEmpty());
        assertEquals("LongLongMap{1=2, 3=4}", map.toString().replace("3=4, 1=2", "1=2, 3=4"));
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(Long.MIN_VALUE, map.get(1));
        assertEquals("LongLongMap{}", map.toString());
    }

    @Test
    void doesNotAllocateWithinTheExpectedSize() {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final LongLongMap map = new LongLongMap(64, -1);
        final Runnable rounds = () -> {
            for (int round = 0; round < 10_000; round++) {
                for (long key = 0; key < 64; key++) {
                    map.put(key << 20, round);
                }
                for (long key = 0; key < 64; key += 2) {
                    map.remove(key << 20);
                }
                map.get(round);
            }
        };
        rounds.run();
        final long before = threads.getCurrentThreadAllocatedBytes();
        rounds.run();
        //NOTE: tolerate sporadic bytes, e.g. objects rematerialized when the JIT deoptimizes
        assertThat(threads.getCurrentThreadAllocatedBytes() - before).isLessThan(10_000);
    }
}
