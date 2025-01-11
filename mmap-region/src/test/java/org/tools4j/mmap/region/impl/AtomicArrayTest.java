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
package org.tools4j.mmap.region.impl;

import org.agrona.BitUtil;
import org.agrona.collections.IntObjConsumer;
import org.agrona.collections.IntObjPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link AtomicArray}
 */
class AtomicArrayTest {

    @Test
    void initialLength() {
        assertEquals(1, new AtomicArray<>(1).length());
        assertEquals(2, new AtomicArray<>(2).length());
        assertEquals(4, new AtomicArray<>(4).length());
        assertEquals(8, new AtomicArray<>(8).length());
        assertEquals(64, new AtomicArray<>(64).length());
        assertEquals(1024, new AtomicArray<>(1024).length());
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(-1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(0));
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(3));
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void setAndGet(final int initialLength) {
        //given
        final int n = 10000;
        final int bits = bits(n);
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then
        for (int i = 0; i < n; i++) {
            assertNull(array.get(i));
            array.set(i, i);
            assertEquals(i, array.get(i));
        }
        assertEquals(1 << bits, array.length());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void getAndGet(final int initialLength) {
        //given
        final int n = 10000;
        final int bits = bits(n);
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then
        for (int i = 0; i < n; i++) {
            assertNull(array.getAndSet(i, i));
            assertEquals(i, array.get(i));
        }
        assertEquals(1 << bits, array.length());
        for (int i = 0; i < n; i++) {
            assertEquals(i, array.getAndSet(i, i*i));
            assertEquals(i*i, array.get(i));
        }
        assertEquals(1 << bits, array.length());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void compareAndSet(final int initialLength) {
        //given
        final int n = 10_000;
        final int nBits = bits(n);
        final int million = 1_000_000;
        final int millionBits = bits(million);
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then
        for (int i = 0; i < n; i++) {
            assertFalse(array.compareAndSet(i, i, i));
            assertEquals(initialLength, array.length());
        }
        for (int i = 0; i < n; i++) {
            assertTrue(array.compareAndSet(i, null, i));
            final int len = 1 << bits(i + 1, initialLength);
            assertEquals(len, array.length());
        }
        assertEquals(1 << nBits, array.length());
        assertFalse(array.compareAndSet(million, 0, 0));
        assertEquals(1 << nBits, array.length());
        assertTrue(array.compareAndSet(million, null, 0));
        assertEquals(1 << millionBits, array.length());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void computeIfAbsent(final int initialLength) {
        //given
        final int n = 10_000;
        final int nBits = bits(n);
        final AtomicArray<String> array = new AtomicArray<>(initialLength);

        //when + then
        for (int i = 0; i < n; i++) {
            final String s = "s:" + i;
            assertSame(s, array.computeIfAbsent(i, index -> s));
            final int len = 1 << bits(i + 1, initialLength);
            assertEquals(len, array.length());
        }
        for (int i = 0; i < n; i++) {
            final String s2 = "s:" + i;
            final String s1 = array.computeIfAbsent(i, index -> s2);
            assertNotSame(s1, s2);
            assertEquals(s1, s2);
            assertEquals(1 << nBits, array.length());
        }
        assertEquals(1 << nBits, array.length());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void forEach(final int initialLength) {
        //given
        final int n = 10000;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);
        final BitSet values = new BitSet(n);
        final BitSet indices = new BitSet(n);
        final Consumer<Integer> valueCaptor = value -> {
            if (value != null) {
                values.set(value);
            }
        };
        final IntObjConsumer<Object> indexCaptor = (index, obj) -> {
            indices.set(index);
            if (obj != null) {
                values.set(index);
            }
        };
        int length;

        //when
        length = array.forEach(valueCaptor);

        //then
        assertEquals(0, values.length());
        assertEquals(initialLength, length);

        //when
        length = array.forEach(indexCaptor);

        //then
        assertEquals(0, values.length());
        assertEquals(initialLength, length);
        assertEquals(initialLength, indices.length());

        //when
        for (int i = 0; i < n; i++) {
            array.set(i, i);
        }
        length = array.forEach(valueCaptor);

        //then
        assertEquals(n, values.length());
        assertEquals(n, values.cardinality());
        assertEquals(1 << bits(n), length);

        //when
        values.clear();
        length = array.forEach(indexCaptor);

        //then
        assertEquals(n, values.length());
        assertEquals(n, values.cardinality());
        assertEquals(1 << bits(n), length);
        assertEquals(1 << bits(n), indices.length());
        assertEquals(1 << bits(n), indices.cardinality());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void forEachWhile(final int initialLength) {
        //given
        final int n = 10000;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);
        final BitSet values = new BitSet(n);
        final BitSet indices = new BitSet(n);
        final IntObjPredicate<Object> predicate = (index, obj) -> {
            indices.set(index);
            if (obj == null) {
                return false;
            }
            values.set(index);
            return true;
        };
        int length;

        //when
        length = array.forEachWhile(predicate);

        //then
        assertEquals(0, values.length());
        assertEquals(1, indices.length());
        assertEquals(0, length);

        //when
        indices.clear();
        array.set(0, 0);
        length = array.forEachWhile(predicate);

        //then
        assertEquals(1, values.length());
        assertEquals(Math.min(2, initialLength), indices.length());
        assertEquals(1, length);

        //when
        values.clear();
        indices.clear();
        for (int i = 0; i < n; i++) {
            array.set(i, i);
        }
        length = array.forEachWhile(predicate);

        //then
        assertEquals(n, length);
        assertEquals(n, values.length());
        assertEquals(n, values.cardinality());
        assertEquals(n + 1, indices.length());
        assertEquals(n + 1, indices.cardinality());
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void indexOf(final int initialLength) {
        //given
        final int n = 10000;

        //when
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //then
        assertEquals(-1, array.indexOf(0));
        assertEquals(-1, array.indexOf(1));
        assertEquals(-1, array.indexOf((index, value) -> value != null));
        assertEquals(0, array.indexOf((index, value) -> value == null));

        //when
        array.set(0, 0);

        //then
        assertEquals(0, array.indexOf(0));
        assertEquals(-1, array.indexOf(1));
        assertEquals(0, array.indexOf((index, value) -> value != null));
        assertEquals(1, array.indexOf((index, value) -> value == null));

        //when
        for (int i = 0; i < n; i++) {
            array.set(i, i);
        }

        //then
        for (int i = 0; i < n; i += (i < 100 || i >= n-100 ? 1 : 10)) {
            final int val = i;
            assertEquals(i, array.indexOf(val));
            assertEquals(i, array.indexOf((index, value) -> value != null && value == val));
        }
        assertEquals(-1, array.indexOf(-1));
        assertEquals(-1, array.indexOf(n));
        assertEquals(0, array.indexOf((index, value) -> value != null));
        assertEquals(n, array.indexOf((index, value) -> value == null));
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void lastIndexOf(final int initialLength) {
        //given
        final int n = 10000;

        //when
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //then
        assertEquals(-1, array.lastIndexOf(0));
        assertEquals(-1, array.lastIndexOf(1));
        assertEquals(-1, array.lastIndexOf((index, value) -> value != null));
        assertEquals(initialLength - 1, array.lastIndexOf((index, value) -> value == null));

        //when
        array.set(0, 0);

        //then
        assertEquals(0, array.lastIndexOf(0));
        assertEquals(-1, array.lastIndexOf(1));
        assertEquals(0, array.lastIndexOf((index, value) -> value != null));
        assertEquals(initialLength - 1 != 0 ? initialLength - 1 : -1, array.lastIndexOf((index, value) -> value == null));

        //when
        for (int i = 0; i < n; i++) {
            array.set(i, i);
        }

        //then
        for (int i = 0; i < n; i += (i < 100 || i >= n-100 ? 1 : 10)) {
            final int val = i;
            assertEquals(i, array.lastIndexOf(val));
            assertEquals(i, array.lastIndexOf((index, value) -> value != null && value == val));
        }
        assertEquals(-1, array.lastIndexOf(-1));
        assertEquals(-1, array.lastIndexOf(n));
        assertEquals(n - 1, array.lastIndexOf((index, value) -> value != null));
        final int nextPow2 = BitUtil.isPowerOfTwo(n) ? n : (Integer.highestOneBit(n) << 1);
        assertEquals(nextPow2 - 1, array.lastIndexOf((index, value) -> value == null));
    }

    @Test
    void negativeIndex() {
        //given
        final AtomicArray<Object> array = new AtomicArray<>();

        //when + then
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(-2, null));
        assertThrows(IndexOutOfBoundsException.class, () -> array.getAndSet(-3, null));
        assertThrows(IndexOutOfBoundsException.class, () -> array.compareAndSet(-3, null, 1));
    }

    @Test
    void illegalInitialLength() {
        //when + then
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(-1));
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(0));
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(3));
        assertThrows(IllegalArgumentException.class, () -> new AtomicArray<>(4095));
    }

    @ParameterizedTest(name = "{0} threads")
    @ValueSource(ints = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512})
    void concurrentTest(final int nThreads) throws InterruptedException {
        //given
        final long maxRuntimeMillis = 10_000;
        final int n = 1_000_000;
        final int r = 1_000_000 / (int)Math.pow(10, Math.floor(Math.log10(nThreads)));
        final AtomicArray<Integer> array = new AtomicArray<>();

        //when
        final AtomicInteger casCount = new AtomicInteger();
        final Thread[] threads = IntStream.range(0, nThreads).mapToObj(index -> {
            final Thread thread = new Thread(null, () -> {
                final Random random = new Random(System.currentTimeMillis() + index);
                for (int i = 0; i < r; i++) {
                    final Integer value = random.nextInt(n);
                    if (array.compareAndSet(value, null, value)) {
                        casCount.incrementAndGet();
                    }
                    assertFalse(array.compareAndSet(value, null, value));
                    assertEquals(value, array.computeIfAbsent(value, x -> x + 3));
                    array.set(value, value);
                    assertFalse(array.compareAndSet(value, value + 1, value + 2));
                }
            }, "thread-" + index);
            thread.start();
            return thread;
        }).toArray(Thread[]::new);
        for (final Thread thread : threads) {
            thread.join(maxRuntimeMillis);
        }

        //then
        int count = 0;
        final int[] bounds = {-1, -1};
        final int[] nulls = {-1, -1};
        for (int i = 0; i < n; i++) {
            final Integer value = array.get(i);
            if (value == null) {
                if (nulls[0] == -1) {
                    nulls[0] = i;
                }
                nulls[1] = i;
            } else {
                assertEquals(i, value);
                if (bounds[0] == -1) {
                    bounds[0] = value;
                }
                bounds[1] = value;
                count++;
            }
        }
        final Random random = new Random();
        for (int i = 0; i <= 10; i++) {
            final int index = random.nextInt(n);
            final Integer value = array.get(index);
            final int first = value == null ? nulls[0] : index;
            final int last = value == null ? array.length() - 1 : index;
            assertEquals(first, array.indexOf(value));
            assertEquals(first, array.indexOf((ind, val) -> Objects.equals(val, value)));
            assertEquals(last, array.lastIndexOf(value));
            assertEquals(last, array.lastIndexOf((ind, val) -> Objects.equals(val, value)));
        }
        assertNotEquals(0, count);
        assertTrue(count <= n);
        assertEquals(casCount.get(), count);
        assertEquals(bounds[0], array.indexOf((index, value) -> value != null), "min");
        assertEquals(bounds[0], array.indexOf(bounds[0]), "min");
        assertEquals(bounds[1], array.lastIndexOf((index, value) -> value != null), "max");
        assertEquals(bounds[1], array.lastIndexOf(bounds[1]), "max");
        assertEquals(nulls[0], array.indexOf((index, value) -> index < n && value == null), "first null");
        assertEquals(nulls[1], array.lastIndexOf((index, value) -> index < n && value == null), "last null");
        System.out.printf("Found %s values in [%s, %s] from %s threads setting %s values each, " +
                        "which is %s%% of the %s possible values in the array of length %s\n",
                count, bounds[0], bounds[1], nThreads, r, (100f*count)/n, n, array.length());
    }

    private static int bits(final int length) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(length - 1);
    }
    private static int bits(final int length, final int initialLength) {
        return bits(Math.max(length, initialLength));
    }
}