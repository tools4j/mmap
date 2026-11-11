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
package org.tools4j.mmap.core.buffer.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.buffer.MutableMemoryBufferFlyweight;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link MemoryBufferImpl}, created through {@link MemoryBuffers}, over every {@link MemoryKind}.
 */
class MemoryBufferImplTest {

    private static final int CAPACITY = 64;
    private static final long LONG = 0x0102030405060708L;

    private static byte[] bytes(final MemoryBuffer buffer) {
        final byte[] bytes = new byte[buffer.capacity()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    private static void fill(final MutableMemoryBuffer buffer, final int seed) {
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.putByte(i, (byte) (seed + i));
        }
    }

    //--- plain access ---

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void plainAccessRoundTripsAtEveryIndex(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        assertEquals(CAPACITY, buffer.capacity());
        for (int i = 0; i + Long.BYTES <= CAPACITY; i++) {
            buffer.putByte(i, (byte) i);
            assertEquals((byte) i, buffer.getByte(i));
            buffer.putShort(i, (short) (i * 1001));
            assertEquals((short) (i * 1001), buffer.getShort(i));
            buffer.putChar(i, (char) (i * 1003));
            assertEquals((char) (i * 1003), buffer.getChar(i));
            buffer.putInt(i, i * 100_003);
            assertEquals(i * 100_003, buffer.getInt(i));
            buffer.putLong(i, LONG * i);
            assertEquals(LONG * i, buffer.getLong(i));
            buffer.putFloat(i, i / 3f);
            assertEquals(i / 3f, buffer.getFloat(i));
            buffer.putDouble(i, i / 7d);
            assertEquals(i / 7d, buffer.getDouble(i));
        }
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void multiByteValuesAreLittleEndianUnlessAByteOrderIsGiven(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        buffer.putLong(3, LONG);
        for (int i = 0; i < Long.BYTES; i++) {
            assertEquals((byte) (8 - i), buffer.getByte(3 + i));
        }
        assertEquals(Long.reverseBytes(LONG), buffer.getLong(3, BIG_ENDIAN));
        assertEquals(LONG, buffer.getLong(3, LITTLE_ENDIAN));
        buffer.putLong(3, LONG, BIG_ENDIAN);
        assertEquals(Long.reverseBytes(LONG), buffer.getLong(3));
        buffer.putInt(1, 0x01020304, BIG_ENDIAN);
        assertEquals(0x04030201, buffer.getInt(1));
        assertEquals(0x01020304, buffer.getInt(1, BIG_ENDIAN));
        buffer.putShort(1, (short) 0x0102, BIG_ENDIAN);
        assertEquals((short) 0x0201, buffer.getShort(1));
        assertEquals((short) 0x0102, buffer.getShort(1, BIG_ENDIAN));
        buffer.putChar(1, (char) 0x0102, BIG_ENDIAN);
        assertEquals((char) 0x0201, buffer.getChar(1));
        assertEquals((char) 0x0102, buffer.getChar(1, BIG_ENDIAN));
        buffer.putFloat(5, 1.5f, BIG_ENDIAN);
        assertEquals(1.5f, buffer.getFloat(5, BIG_ENDIAN));
        assertEquals(Integer.reverseBytes(Float.floatToRawIntBits(1.5f)), buffer.getInt(5));
        buffer.putDouble(5, 2.5, BIG_ENDIAN);
        assertEquals(2.5, buffer.getDouble(5, BIG_ENDIAN));
        assertEquals(Long.reverseBytes(Double.doubleToRawLongBits(2.5)), buffer.getLong(5));
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void accessesOutsideOfTheBufferThrowWithoutSideEffects(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        fill(buffer, 7);
        final byte[] content = bytes(buffer);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getByte(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getByte(CAPACITY));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getLong(CAPACITY - 7));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getInt(CAPACITY - 3));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getShort(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.putLong(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(CAPACITY - 2, new byte[3]));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.putBytes(-1, new byte[3]));
        if (!kind.expandable) {
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putLong(CAPACITY - 7, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putByte(CAPACITY, (byte) 1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.putBytes(CAPACITY - 2, new byte[3]));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.setMemory(CAPACITY - 2, 3, (byte) 1));
            assertEquals(CAPACITY, buffer.capacity());
        }
        assertArrayEquals(content, Arrays.copyOf(bytes(buffer), CAPACITY));
    }

    //--- ordered access ---

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void orderedAccessWorksAtAlignedIndices(final MemoryKind kind) {
        if (!kind.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        assertThat(buffer.alignment()).isGreaterThanOrEqualTo(Long.BYTES);
        buffer.putLongVolatile(8, LONG);
        assertEquals(LONG, buffer.getLong(8));
        assertEquals(LONG, buffer.getLongVolatile(8));
        assertEquals(LONG, buffer.getLongAcquire(8));
        assertEquals(LONG, buffer.getLongOpaque(8));
        buffer.putLongRelease(16, 5);
        assertEquals(5, buffer.getLongAcquire(16));
        buffer.putLongOpaque(16, 6);
        assertEquals(6, buffer.getLongOpaque(16));
        assertTrue(buffer.compareAndSetLong(16, 6, 7));
        assertFalse(buffer.compareAndSetLong(16, 6, 8));
        assertEquals(7, buffer.compareAndExchangeLong(16, 7, 9));
        assertEquals(9, buffer.compareAndExchangeLong(16, 7, 10));
        assertEquals(9, buffer.getAndAddLong(16, 3));
        assertEquals(12, buffer.getAndSetLong(16, 20));
        assertEquals(20, buffer.getLong(16));

        buffer.putIntVolatile(4, 0x01020304);
        assertEquals(0x01020304, buffer.getInt(4));
        assertEquals(0x01020304, buffer.getIntVolatile(4));
        assertEquals(0x01020304, buffer.getIntAcquire(4));
        assertEquals(0x01020304, buffer.getIntOpaque(4));
        buffer.putIntRelease(4, 5);
        buffer.putIntOpaque(4, 6);
        assertTrue(buffer.compareAndSetInt(4, 6, 7));
        assertFalse(buffer.compareAndSetInt(4, 6, 8));
        assertEquals(7, buffer.compareAndExchangeInt(4, 7, 9));
        assertEquals(9, buffer.getAndAddInt(4, 3));
        assertEquals(12, buffer.getAndSetInt(4, 20));
        assertEquals(20, buffer.getInt(4));

        buffer.putShortVolatile(2, (short) 0x0102);
        assertEquals((short) 0x0102, buffer.getShortVolatile(2));
        buffer.putCharVolatile(2, (char) 0x0304);
        assertEquals((char) 0x0304, buffer.getCharVolatile(2));
        buffer.putByteVolatile(3, (byte) 5);
        assertEquals((byte) 5, buffer.getByteVolatile(3));
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void orderedAccessWithBigEndianByteOrderReversesTheBytes(final MemoryKind kind) {
        if (!kind.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        buffer.putLongVolatile(8, LONG, BIG_ENDIAN);
        assertEquals(Long.reverseBytes(LONG), buffer.getLong(8));
        assertEquals(LONG, buffer.getLongVolatile(8, BIG_ENDIAN));
        assertEquals(LONG, buffer.getLongAcquire(8, BIG_ENDIAN));
        assertEquals(LONG, buffer.getLongOpaque(8, BIG_ENDIAN));
        buffer.putLongRelease(8, 1, BIG_ENDIAN);
        buffer.putLongOpaque(8, 2, BIG_ENDIAN);
        assertTrue(buffer.compareAndSetLong(8, 2, 3, BIG_ENDIAN));
        assertEquals(3, buffer.compareAndExchangeLong(8, 3, 4, BIG_ENDIAN));
        assertEquals(4, buffer.getAndAddLong(8, 255, BIG_ENDIAN));
        assertEquals(259, buffer.getAndSetLong(8, 1, BIG_ENDIAN));
        assertEquals(Long.reverseBytes(1), buffer.getLong(8));
        assertEquals(Long.reverseBytes(1), buffer.getAndAddLong(8, 1, LITTLE_ENDIAN));

        buffer.putIntVolatile(4, 0x01020304, BIG_ENDIAN);
        assertEquals(0x04030201, buffer.getInt(4));
        assertEquals(0x01020304, buffer.getIntVolatile(4, BIG_ENDIAN));
        assertEquals(0x01020304, buffer.getIntAcquire(4, BIG_ENDIAN));
        assertEquals(0x01020304, buffer.getIntOpaque(4, BIG_ENDIAN));
        buffer.putIntRelease(4, 1, BIG_ENDIAN);
        buffer.putIntOpaque(4, 2, BIG_ENDIAN);
        assertTrue(buffer.compareAndSetInt(4, 2, 3, BIG_ENDIAN));
        assertEquals(3, buffer.compareAndExchangeInt(4, 3, 4, BIG_ENDIAN));
        assertEquals(4, buffer.getAndAddInt(4, 255, BIG_ENDIAN));
        assertEquals(259, buffer.getAndSetInt(4, 1, BIG_ENDIAN));
        assertEquals(Integer.reverseBytes(1), buffer.getInt(4));

        buffer.putShortVolatile(2, (short) 0x0102, BIG_ENDIAN);
        assertEquals((short) 0x0201, buffer.getShort(2));
        assertEquals((short) 0x0102, buffer.getShortVolatile(2, BIG_ENDIAN));
        buffer.putCharVolatile(2, (char) 0x0102, BIG_ENDIAN);
        assertEquals((char) 0x0201, buffer.getChar(2));
        assertEquals((char) 0x0102, buffer.getCharVolatile(2, BIG_ENDIAN));
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void orderedAccessToMultiByteValuesOfByteArrayMemoryIsUnsupported(final MemoryKind kind) {
        if (kind.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        assertEquals(1, buffer.alignment());
        assertThrows(UnsupportedOperationException.class, () -> buffer.getLongVolatile(8));
        assertThrows(UnsupportedOperationException.class, () -> buffer.putLongRelease(8, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.compareAndSetLong(8, 0, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.getAndAddInt(8, 1));
        assertThrows(UnsupportedOperationException.class, () -> buffer.getIntAcquire(8));
        assertThrows(UnsupportedOperationException.class, () -> buffer.getShortVolatile(8));
        assertThrows(UnsupportedOperationException.class, () -> buffer.putCharVolatile(8, 'x'));
        buffer.putByteVolatile(3, (byte) 5);
        assertEquals((byte) 5, buffer.getByteVolatile(3));
    }

    @Test
    void misalignedOrderedAccessThrows() {
        final MutableMemoryBufferFlyweight buffer = MemoryBuffers.mutableFlyweight();
        buffer.wrap(MemoryBuffers.allocateDirect(CAPACITY), 1, CAPACITY - 8);
        assertEquals(1, buffer.alignment());
        assertThrows(IllegalStateException.class, () -> buffer.getLongVolatile(0));
        assertThrows(IllegalStateException.class, () -> buffer.compareAndSetInt(0, 0, 1));
        assertThrows(IllegalStateException.class, () -> buffer.putShortVolatile(0, (short) 1));
        buffer.putLongVolatile(7, LONG);
        assertEquals(LONG, buffer.getLong(7));
        buffer.putByteVolatile(0, (byte) 1);

        buffer.wrap(MemoryBuffers.allocate(CAPACITY), 4, CAPACITY - 8);
        assertEquals(4, buffer.alignment());
        assertThrows(IllegalStateException.class, () -> buffer.getLongAcquire(0));
        assertTrue(buffer.compareAndSetInt(0, 0, 1));
        buffer.putLongRelease(4, LONG);
        assertEquals(LONG, buffer.getLongAcquire(4));
    }

    //--- properties and wrapping ---

    @Test
    void propertiesOfTheMemoryKinds() {
        final MutableMemoryBuffer heap = MemoryBuffers.allocate(CAPACITY);
        assertFalse(heap.isDirect());
        assertFalse(heap.isExpandable());
        assertEquals(Long.BYTES, heap.alignment());
        final MutableMemoryBuffer direct = MemoryBuffers.allocateDirect(CAPACITY);
        assertTrue(direct.isDirect());
        assertThat(direct.alignment()).isGreaterThanOrEqualTo(64);
        assertTrue(MemoryBuffers.expandable(CAPACITY).isExpandable());
        assertTrue(MemoryBuffers.expandableDirect(CAPACITY).isDirect());
        assertEquals(17 * Long.BYTES, MemoryBuffers.wrap(new long[17]).capacity());
        assertEquals(0, MemoryBuffers.mutableFlyweight().capacity());
        assertEquals(0, MemoryBuffers.flyweight().capacity());
        assertThrows(IllegalArgumentException.class, () -> MemoryBuffers.allocate(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> MemoryBuffers.wrap(new long[2], 1, 2));
    }

    @Test
    void wrapOffsetAndAddressOfFlyweights() {
        final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
        buffer.wrap(new byte[10], 3, 5);
        assertEquals(3, buffer.wrapOffset());
        assertFalse(buffer.isDirect());
        assertThrows(IllegalStateException.class, buffer::address);

        buffer.wrap(ByteBuffer.allocate(10), 4, 5);
        assertEquals(4, buffer.wrapOffset());

        final MutableMemoryBuffer longs = MemoryBuffers.wrap(new long[4], 1, 2);
        buffer.wrap(longs, 4, 8);
        assertEquals(12, buffer.wrapOffset());
        assertEquals(4, buffer.alignment());

        final ByteBuffer direct = ByteBuffer.allocateDirect(32);
        buffer.wrap(direct, 8, 16);
        assertEquals(8, buffer.wrapOffset());
        assertTrue(buffer.isDirect());
        final long address = buffer.address();
        buffer.wrap(address, 4);
        assertEquals(0, buffer.wrapOffset());
        assertEquals(address, buffer.address());
        assertEquals(4, buffer.capacity());

        final MutableMemoryBuffer bytes = MemoryBuffers.mutableFlyweight();
        ((MutableMemoryBufferFlyweight) bytes).wrap(new byte[20], 2, 10);
        buffer.wrap(bytes, 3, 4);
        assertEquals(5, buffer.wrapOffset());
        Reference.reachabilityFence(direct);
    }

    @Test
    void flyweightsShareTheMemoryTheyWrap() {
        final byte[] array = new byte[16];
        final MutableMemoryBufferFlyweight writer = MemoryBuffers.mutableFlyweight();
        writer.wrap(array, 4, 8);
        final MemoryBufferFlyweight reader = MemoryBuffers.flyweight();
        reader.wrap(writer, 2, 4);
        writer.putInt(2, 0x01020304);
        assertEquals(0x01020304, reader.getInt(0));
        assertEquals(0x04, array[6]);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.getByte(4));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.wrap(writer, 6, 4));
    }

    @Test
    void readOnlyByteBuffers() {
        final ByteBuffer heap = ByteBuffer.allocate(16).put(0, (byte) 3).asReadOnlyBuffer();
        final ByteBuffer direct = ByteBuffer.allocateDirect(16).put(0, (byte) 4).asReadOnlyBuffer();
        final MemoryBufferFlyweight reader = MemoryBuffers.flyweight();
        assertThrows(IllegalArgumentException.class, () -> reader.wrap(heap));
        reader.wrap(direct);
        assertEquals(4, reader.getByte(0));
        final MutableMemoryBufferFlyweight writer = MemoryBuffers.mutableFlyweight();
        assertThrows(ReadOnlyBufferException.class, () -> writer.wrap(heap));
        assertThrows(ReadOnlyBufferException.class, () -> writer.wrap(direct, 0, 8));
        assertEquals(0, writer.capacity());
    }

    //--- expandable buffers ---

    @ParameterizedTest
    @EnumSource(value = MemoryKind.class, names = {"EXPANDABLE", "EXPANDABLE_DIRECT"})
    void expandableBuffersGrowOnWritesBeyondTheirCapacity(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(16);
        fill(buffer, 1);
        final byte[] content = bytes(buffer);
        final MemoryBufferFlyweight oldMemory = MemoryBuffers.flyweight();
        oldMemory.wrap(buffer);

        buffer.putLong(20, LONG);
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(28);
        assertEquals(LONG, buffer.getLong(20));
        assertArrayEquals(content, Arrays.copyOf(bytes(buffer), 16));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getLong(buffer.capacity() - 7));

        buffer.putByte(0, (byte) 99);
        assertEquals(1, oldMemory.getByte(0));
        assertEquals(16, oldMemory.capacity());

        buffer.putBytes(1000, new byte[24]);
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(1024);
        assertTrue(buffer.compareAndSetLong(2048, 0, 1));
        buffer.putTextAscii(4000, "abc");
        assertEquals("abc", buffer.getTextAscii(4000, 3));
        assertEquals(99, buffer.getByte(0));
        assertThat(buffer.alignment()).isGreaterThanOrEqualTo(Long.BYTES);
    }

    //--- bulk copies ---

    static Stream<Arguments> kindPairs() {
        return Arrays.stream(MemoryKind.values())
                .flatMap(source -> Arrays.stream(MemoryKind.values()).map(target -> Arguments.of(source, target)));
    }

    @ParameterizedTest
    @MethodSource("kindPairs")
    void copiesBetweenAllKindsOfMemory(final MemoryKind sourceKind, final MemoryKind targetKind) {
        final MutableMemoryBuffer source = sourceKind.create(CAPACITY);
        final MutableMemoryBuffer target = targetKind.create(CAPACITY);
        fill(source, 11);
        target.putBytes(5, source, 3, 40);
        for (int i = 0; i < 40; i++) {
            assertEquals((byte) (11 + 3 + i), target.getByte(5 + i));
        }
        assertEquals(0, target.getByte(4));
        assertEquals(0, target.getByte(45));
        source.getBytes(0, target, 0, CAPACITY);
        assertArrayEquals(bytes(source), Arrays.copyOf(bytes(target), CAPACITY));
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void copiesToAndFromArraysAndByteBuffers(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        final byte[] array = new byte[CAPACITY];
        for (int i = 0; i < CAPACITY; i++) {
            array[i] = (byte) (i * 3);
        }
        buffer.putBytes(0, array);
        assertArrayEquals(array, bytes(buffer));
        final byte[] range = new byte[10];
        buffer.getBytes(4, range, 2, 6);
        assertArrayEquals(new byte[]{0, 0, 12, 15, 18, 21, 24, 27, 0, 0}, range);

        for (final ByteBuffer byteBuffer : new ByteBuffer[]{ByteBuffer.allocate(20), ByteBuffer.allocateDirect(20)}) {
            buffer.getBytes(2, byteBuffer, 5, 4);
            assertEquals(0, byteBuffer.position());
            assertEquals(6, byteBuffer.get(5));
            assertEquals(15, byteBuffer.get(8));
            byteBuffer.position(10);
            buffer.getBytes(1, byteBuffer, 3);
            assertEquals(13, byteBuffer.position());
            assertEquals(3, byteBuffer.get(10));

            byteBuffer.put(0, (byte) -1).put(1, (byte) -2);
            buffer.putBytes(30, byteBuffer, 0, 2);
            assertEquals(-1, buffer.getByte(30));
            assertEquals(-2, buffer.getByte(31));
            byteBuffer.position(0);
            buffer.putBytes(40, byteBuffer.asReadOnlyBuffer(), 2);
            assertEquals(-2, buffer.getByte(41));
            byteBuffer.position(1);
            final ByteBuffer readOnly = byteBuffer.asReadOnlyBuffer();
            buffer.putBytes(50, readOnly, 1);
            assertEquals(2, readOnly.position());
            assertEquals(-2, buffer.getByte(50));
            assertThrows(ReadOnlyBufferException.class, () -> buffer.getBytes(0, readOnly, 0, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getBytes(0, byteBuffer, 18, 3));
        }
    }

    @ParameterizedTest
    @EnumSource(MemoryKind.class)
    void copiesWithinTheSameBufferAndSetsMemory(final MemoryKind kind) {
        final MutableMemoryBuffer buffer = kind.create(CAPACITY);
        fill(buffer, 0);
        buffer.putBytes(2, buffer, 0, 10);
        for (int i = 0; i < 10; i++) {
            assertEquals((byte) i, buffer.getByte(2 + i));
        }
        buffer.putBytes(0, buffer, 4, 10);
        final byte[] copied = new byte[10];
        buffer.getBytes(0, copied);
        assertArrayEquals(new byte[]{2, 3, 4, 5, 6, 7, 8, 9, 12, 13}, copied);
        buffer.setMemory(10, 20, (byte) 7);
        for (int i = 10; i < 30; i++) {
            assertEquals(7, buffer.getByte(i));
        }
        assertEquals(30, buffer.getByte(30));
    }

    //--- allocation ---

    @Test
    void wrappingAndAccessingDoNotAllocate() {
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final MutableMemoryBuffer heap = MemoryBuffers.allocate(CAPACITY);
        final MutableMemoryBuffer direct = MemoryBuffers.allocateDirect(CAPACITY);
        final byte[] array = new byte[CAPACITY];
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(CAPACITY);
        final MutableMemoryBufferFlyweight flyweight = MemoryBuffers.mutableFlyweight();
        //re-wrapping the same direct byte buffer, as wrapping another one creates a segment with FFM
        final MutableMemoryBufferFlyweight byteBufferFlyweight = MemoryBuffers.mutableFlyweight();
        final StringBuilder text = new StringBuilder(64);
        final Consumer<Integer> rounds = n -> {
            for (int i = 0; i < n; i++) {
                flyweight.wrap(i % 2 == 0 ? heap : direct);
                flyweight.putLong(8, i);
                flyweight.compareAndSetLong(8, i, i + 1);
                flyweight.getAndAddInt(4, 1);
                flyweight.wrap(array, 8, 16);
                flyweight.putInt(4, flyweight.getInt(0) + 1);
                byteBufferFlyweight.wrap(byteBuffer, i % 2 * 8, 32);
                byteBufferFlyweight.putLongRelease(16, byteBufferFlyweight.getLongAcquire(16) + 1);
                byteBufferFlyweight.putTextUtf8(24, "abc");
                text.setLength(0);
                byteBufferFlyweight.getTextUtf8(24, 3, text);
                heap.putBytes(32, direct, 0, 16);
            }
        };
        rounds.accept(100_000);
        final long before = threads.getCurrentThreadAllocatedBytes();
        rounds.accept(10_000);
        final long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        //NOTE: tolerate sporadic bytes, e.g. objects rematerialized when the JIT deoptimizes, while a single
        //      allocation per round would amount to at least 160 KB
        assertThat(allocated).isLessThan(10_000);
    }
}
