/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.collections.IntObjConsumer;
import org.agrona.collections.IntObjPredicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.BitSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @ParameterizedTest
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

    @ParameterizedTest
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

    @ParameterizedTest
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

    @ParameterizedTest
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

    @ParameterizedTest
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

    private static int bits(final int length) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(length - 1);
    }
    private static int bits(final int length, final int initialLength) {
        return bits(Math.max(length, initialLength));
    }
}