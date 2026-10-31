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

import org.agrona.BitUtil;
import org.agrona.collections.IntObjConsumer;
import org.agrona.collections.IntObjPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    void getAndSet(final int initialLength) {
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

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64, 256, 4096})
    void indexOfMinAndMax(final int initialLength) {
        //given
        final int n = 1000;
        final Comparator<Integer> nullsLast = Comparator.nullsLast(Comparator.naturalOrder());
        final Comparator<Integer> nullsFirst = Comparator.nullsFirst(Comparator.naturalOrder());
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then: only nulls
        assertIndexOfMinAndMax(array, nullsLast);
        assertIndexOfMinAndMax(array, nullsFirst);

        //when: duplicates in all blocks, unique min and max in later blocks
        for (int i = 0; i < n; i++) {
            array.set(i, i % 7);
        }
        array.set(n - 3, -1);
        array.set(n / 2 + 1, 100);

        //then
        assertIndexOfMinAndMax(array, nullsLast);
        assertIndexOfMinAndMax(array, nullsFirst);
    }

    /** Asserts min/max index methods against a brute force scan over all elements, nulls included */
    private static void assertIndexOfMinAndMax(final AtomicArray<Integer> array, final Comparator<Integer> comparator) {
        int firstMin = -1, lastMin = -1, firstMax = -1, lastMax = -1;
        for (int i = 0; i < array.length(); i++) {
            final Integer value = array.get(i);
            if (firstMin < 0 || comparator.compare(value, array.get(firstMin)) < 0) firstMin = i;
            if (lastMin < 0 || comparator.compare(value, array.get(lastMin)) <= 0) lastMin = i;
            if (firstMax < 0 || comparator.compare(value, array.get(firstMax)) > 0) firstMax = i;
            if (lastMax < 0 || comparator.compare(value, array.get(lastMax)) >= 0) lastMax = i;
        }
        assertEquals(firstMin, array.indexOfMin(comparator), "indexOfMin");
        assertEquals(lastMin, array.lastIndexOfMin(comparator), "lastIndexOfMin");
        assertEquals(firstMax, array.indexOfMax(comparator), "indexOfMax");
        assertEquals(lastMax, array.lastIndexOfMax(comparator), "lastIndexOfMax");
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64})
    void allocationAndAcquireReads(final int initialLength) {
        //given
        final int far = 4 * initialLength + 1;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then
        assertTrue(array.isAllocated(0));
        assertFalse(array.isAllocated(far));
        assertNull(array.getAcquire(0));
        assertNull(array.getAcquire(far));

        //when
        array.set(far, 7);

        //then
        assertTrue(array.isAllocated(far));
        assertEquals(7, array.getAcquire(far));
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64})
    void setIfAbsentAndSetIfPresent(final int initialLength) {
        //given
        final int far = 4 * initialLength + 1;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then: unallocated block
        assertFalse(array.setIfPresent(far, 1));
        assertFalse(array.isAllocated(far));

        //when + then: absent
        assertNull(array.setIfAbsent(far, null));
        assertTrue(array.isAllocated(far));
        assertFalse(array.setIfPresent(far, 1));
        assertNull(array.get(far));
        assertEquals(5, array.setIfAbsent(far, 5));

        //when + then: present
        assertEquals(5, array.setIfAbsent(far, 6));
        assertTrue(array.setIfPresent(far, 8));
        assertEquals(8, array.get(far));
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64})
    void computeIfPresentPassesArrayIndex(final int initialLength) {
        //given
        final int far = 4 * initialLength + 1;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);
        final List<Integer> indices = new ArrayList<>();

        //when + then: unallocated block and absent value
        assertNull(array.computeIfPresent(far, (index, value) -> value + 1));
        array.set(far + 1, 0);
        assertNull(array.computeIfPresent(far, (index, value) -> value + 1));
        assertNull(array.get(far));

        //when
        array.set(far, 10);
        final Integer result = array.computeIfPresent(far, (index, value) -> {
            indices.add(index);
            return value + 1;
        });

        //then
        assertEquals(11, result);
        assertEquals(11, array.get(far));
        assertEquals(List.of(far), indices);
    }

    @ParameterizedTest(name = "initial length: {0}")
    @ValueSource(ints = {1, 2, 4, 8, 64})
    void compareAndExchange(final int initialLength) {
        //given
        final int far = 4 * initialLength + 1;
        final AtomicArray<Integer> array = new AtomicArray<>(initialLength);

        //when + then: non-null expected value does not allocate a block
        assertNull(array.compareAndExchange(far, 1, 2));
        assertFalse(array.isAllocated(far));

        //when + then: success returns expected value as witness
        assertNull(array.compareAndExchange(far, null, 3));
        assertEquals(3, array.get(far));
        assertEquals(3, array.compareAndExchange(far, 3, 5));
        assertEquals(5, array.get(far));

        //when + then: failure returns current value as witness
        assertEquals(5, array.compareAndExchange(far, 4, 6));
        assertEquals(5, array.get(far));
    }

    @Test
    void concurrentComputeIfPresentAndBlockAllocation() throws InterruptedException {
        //given
        final int nThreads = 8;
        final int increments = 10_000;
        final int far = 1 << 16;
        final AtomicArray<Integer> array = new AtomicArray<>(1);
        array.set(0, 0);
        final CountDownLatch start = new CountDownLatch(1);
        final Thread[] threads = new Thread[nThreads];
        for (int t = 0; t < nThreads; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(() -> {
                await(start);
                //all threads allocate the same far blocks concurrently
                array.set(far + threadIndex, threadIndex);
                for (int i = 0; i < increments; i++) {
                    array.computeIfPresent(0, (index, value) -> value + 1);
                }
            }, "computing-" + t);
            threads[t].start();
        }

        //when
        start.countDown();
        for (final Thread thread : threads) {
            thread.join(10_000);
        }

        //then
        assertEquals(nThreads * increments, array.get(0));
        for (int t = 0; t < nThreads; t++) {
            assertEquals(t, array.get(far + t));
        }
    }

    private static void await(final CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (final InterruptedException e) {
            throw new IllegalStateException(e);
        }
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
        final int r = 100_000 / (int)Math.pow(10, Math.floor(Math.log10(nThreads)));
        final long seed = System.currentTimeMillis();
        final AtomicArray<Integer> array = new AtomicArray<>();

        //when
        final AtomicInteger casCount = new AtomicInteger();
        final CountDownLatch start = new CountDownLatch(1);
        final Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        final Thread[] threads = IntStream.range(0, nThreads).mapToObj(index -> {
            final Thread thread = new Thread(null, () -> {
                try {
                    final Random random = new Random(seed + index);
                    start.await();
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
                } catch (final Throwable t) {
                    failures.add(t);
                }
            }, "thread-" + index);
            thread.start();
            return thread;
        }).toArray(Thread[]::new);
        start.countDown();
        for (final Thread thread : threads) {
            thread.join(maxRuntimeMillis);
            assertFalse(thread.isAlive(), thread.getName() + " still running after " + maxRuntimeMillis + "ms");
        }
        if (!failures.isEmpty()) {
            final AssertionError error = new AssertionError(failures.size() + " of " + nThreads +
                    " threads failed, seed=" + seed, failures.peek());
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
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