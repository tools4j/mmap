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
import org.junit.jupiter.params.provider.ValueSource;
import org.tools4j.mmap.core.buffer.MemoryAccessMode;
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBinding;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

/**
 * Unit test for {@link MemoryBufferImpl}, created through {@link MemoryBuffers}, over every {@link TestMemory}.
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
    @EnumSource(TestMemory.class)
    void plainAccessRoundTripsAtEveryIndex(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void multiByteValuesAreLittleEndianUnlessAByteOrderIsGiven(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void accessesOutsideOfTheBufferThrowWithoutSideEffects(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
        if (!memory.expandable) {
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
    @EnumSource(TestMemory.class)
    void orderedAccessWorksAtAlignedIndices(final TestMemory memory) {
        if (!memory.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void orderedAccessWithBigEndianByteOrderReversesTheBytes(final TestMemory memory) {
        if (!memory.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void orderedAccessToMultiByteValuesOfByteArrayMemoryIsUnsupported(final TestMemory memory) {
        if (memory.orderedAccess) {
            return;
        }
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
        final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
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
    void propertiesOfTheTestMemorys() {
        final MutableMemoryBuffer heap = MemoryBuffers.allocate(CAPACITY);
        assertFalse(heap.isDirect());
        assertFalse(heap.isReadOnly());
        assertEquals(MemoryBinding.FIXED, heap.binding());
        assertEquals(Long.BYTES, heap.alignment());
        final MutableMemoryBuffer direct = MemoryBuffers.allocateDirect(CAPACITY);
        assertTrue(direct.isDirect());
        assertEquals(MemoryBinding.FIXED, direct.binding());
        assertThat(direct.alignment()).isGreaterThanOrEqualTo(64);
        assertEquals(MemoryBinding.EXPANDABLE, MemoryBuffers.expandable(CAPACITY).binding());
        assertFalse(MemoryBuffers.expandable(CAPACITY).isDirect());
        assertEquals(MemoryBinding.EXPANDABLE, MemoryBuffers.expandableDirect(CAPACITY).binding());
        assertTrue(MemoryBuffers.expandableDirect(CAPACITY).isDirect());
        assertEquals(17 * Long.BYTES, MemoryBuffers.wrap(new long[17]).capacity());
        assertEquals(MemoryBinding.FIXED, MemoryBuffers.wrap(new long[17]).binding());
        assertEquals(0, MemoryBuffers.flyweight().capacity());
        assertEquals(MemoryBinding.WRAPPABLE, MemoryBuffers.flyweight().binding());
        assertEquals(MemoryBinding.FIXED, MemoryBuffers.EMPTY.binding());
        assertEquals(MemoryAccessMode.valueOf(System.getProperty("mmap.core.memoryAccess", "UNSAFE")),
                MemoryAccessMode.inUse());
        assertThrows(IllegalArgumentException.class, () -> MemoryBuffers.allocate(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> MemoryBuffers.wrap(new long[2], 1, 2));
    }

    @Test
    void unwrappedFlyweightsWrapNoMemoryAsNewOnes() {
        final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
        assertWrapsNoMemory(buffer);
        buffer.wrap(ByteBuffer.allocateDirect(16), 8, 8);
        buffer.unwrap();
        assertWrapsNoMemory(buffer);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.putByte(0, (byte) 1));
        buffer.wrap(ByteBuffer.allocate(16).asReadOnlyBuffer(), 8, 8);
        buffer.unwrap();
        assertWrapsNoMemory(buffer);
    }

    private static void assertWrapsNoMemory(final MemoryBufferFlyweight buffer) {
        assertEquals(0, buffer.capacity());
        assertFalse(buffer.isReadOnly());
        assertEquals(0, buffer.wrapOffset());
        assertEquals(MemoryBuffers.NULL_ADDRESS, buffer.address());
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.getByte(0));
    }

    @Test
    void wrapOffsetAndAddressOfFlyweights() {
        final MemoryBufferFlyweight buffer = MemoryBuffers.flyweight();
        buffer.wrap(new byte[10], 3, 5);
        assertEquals(3, buffer.wrapOffset());
        assertFalse(buffer.isDirect());
        assertEquals(MemoryBuffers.NULL_ADDRESS, buffer.address());
        assertThrows(IllegalArgumentException.class, () -> buffer.wrap(buffer.address(), 5));
        assertEquals(3, buffer.wrapOffset(), "unchanged");
        buffer.wrap(MemoryBuffers.NULL_ADDRESS, 0);
        assertWrapsNoMemory(buffer);

        buffer.wrap(ByteBuffer.allocate(10), 4, 5);
        assertEquals(4, buffer.wrapOffset());
        assertEquals(MemoryBuffers.NULL_ADDRESS, buffer.address());

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

        final MemoryBufferFlyweight bytes = MemoryBuffers.flyweight();
        bytes.wrap(new byte[20], 2, 10);
        buffer.wrap(bytes, 3, 4);
        assertEquals(5, buffer.wrapOffset());
        Reference.reachabilityFence(direct);
    }

    @Test
    void flyweightsShareTheMemoryTheyWrap() {
        final byte[] array = new byte[16];
        final MemoryBufferFlyweight writer = MemoryBuffers.flyweight();
        writer.wrap(array, 4, 8);
        final MemoryBufferFlyweight reader = MemoryBuffers.flyweight();
        reader.wrap(writer, 2, 4);
        writer.putInt(2, 0x01020304);
        assertEquals(0x01020304, reader.getInt(0));
        assertEquals(0x04, array[6]);
        assertThrows(IndexOutOfBoundsException.class, () -> reader.getByte(4));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.wrap(writer, 6, 4));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(booleans = {false, true})
    void readOnlyByteBuffersAreWrappedReadOnly(final boolean direct) {
        final ByteBuffer writable = (direct ? ByteBuffer.allocateDirect(24) : ByteBuffer.allocate(24))
                .order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer readOnly = writable.asReadOnlyBuffer();
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        flyweight.wrap(readOnly, 8, 16);
        assertTrue(flyweight.isReadOnly());
        assertEquals(direct, flyweight.isDirect());
        assertEquals(8, flyweight.wrapOffset());
        writable.putLong(8, LONG);
        assertEquals(LONG, flyweight.getLong(0));
        assertThrows(ReadOnlyBufferException.class, () -> flyweight.putLong(0, 1));
        assertThrows(ReadOnlyBufferException.class, () -> flyweight.putBytes(0, new byte[8]));
        assertThrows(IndexOutOfBoundsException.class, () -> flyweight.putLong(16, 1));
        assertEquals(LONG, writable.getLong(8));
        final MemoryBufferFlyweight range = MemoryBuffers.flyweight();
        range.wrap(flyweight, 4, 8);
        assertTrue(range.isReadOnly());
        assertEquals(LONG >>> 32, range.getInt(0) & 0xFFFFFFFFL);
        assertThrows(ReadOnlyBufferException.class, () -> range.putInt(0, 1));
        flyweight.wrap(readOnly);
        assertEquals(LONG, flyweight.getLong(8), "same byte buffer again");
        assertTrue(flyweight.isReadOnly());

        flyweight.wrap(writable, 8, 16);
        assertFalse(flyweight.isReadOnly());
        flyweight.putLong(0, 1);
        assertEquals(1, writable.getLong(8));
        flyweight.wrap(readOnly);
        assertThrows(ReadOnlyBufferException.class, () -> flyweight.putByte(0, (byte) 1));
        flyweight.wrap(new byte[8]);
        assertFalse(flyweight.isReadOnly());
        flyweight.putLong(0, 1);
    }

    //--- read-only memory ---

    private static final int READ_ONLY_CAPACITY = 32;

    /** Every kind of write, each to bytes 8 to 15 or fewer, with the method name. */
    private static Stream<Arguments> writes() {
        final byte[] source = new byte[8];
        return Stream.of(
                write("putByte", buffer -> buffer.putByte(8, (byte) 1)),
                write("putShort", buffer -> buffer.putShort(8, (short) 1)),
                write("putChar", buffer -> buffer.putChar(8, 'a')),
                write("putInt", buffer -> buffer.putInt(8, 1)),
                write("putLong", buffer -> buffer.putLong(8, 1)),
                write("putFloat", buffer -> buffer.putFloat(8, 1)),
                write("putDouble", buffer -> buffer.putDouble(8, 1)),
                write("putLong big endian", buffer -> buffer.putLong(8, 1, BIG_ENDIAN)),
                write("putByteVolatile", buffer -> buffer.putByteVolatile(8, (byte) 1)),
                write("putShortVolatile", buffer -> buffer.putShortVolatile(8, (short) 1)),
                write("putCharVolatile", buffer -> buffer.putCharVolatile(8, 'a')),
                write("putIntOpaque", buffer -> buffer.putIntOpaque(8, 1)),
                write("putIntRelease", buffer -> buffer.putIntRelease(8, 1)),
                write("putIntVolatile", buffer -> buffer.putIntVolatile(8, 1)),
                write("compareAndSetInt", buffer -> buffer.compareAndSetInt(8, 0, 1)),
                write("compareAndExchangeInt", buffer -> buffer.compareAndExchangeInt(8, 0, 1)),
                write("getAndAddInt", buffer -> buffer.getAndAddInt(8, 1)),
                write("getAndSetInt", buffer -> buffer.getAndSetInt(8, 1)),
                write("putLongOpaque", buffer -> buffer.putLongOpaque(8, 1)),
                write("putLongRelease", buffer -> buffer.putLongRelease(8, 1)),
                write("putLongVolatile", buffer -> buffer.putLongVolatile(8, 1)),
                write("compareAndSetLong", buffer -> buffer.compareAndSetLong(8, 0, 1)),
                write("compareAndExchangeLong", buffer -> buffer.compareAndExchangeLong(8, 0, 1)),
                write("getAndAddLong", buffer -> buffer.getAndAddLong(8, 1)),
                write("getAndSetLong", buffer -> buffer.getAndSetLong(8, 1)),
                write("putBytes byte[]", buffer -> buffer.putBytes(8, source)),
                write("putBytes byte[] range", buffer -> buffer.putBytes(8, source, 0, 8)),
                write("putBytes buffer", buffer -> buffer.putBytes(8, MemoryBuffers.allocate(8), 0, 8)),
                write("putBytes ByteBuffer range", buffer -> buffer.putBytes(8, ByteBuffer.allocate(8), 0, 8)),
                write("putBytes ByteBuffer", buffer -> buffer.putBytes(8, ByteBuffer.allocate(8), 8)),
                write("setMemory", buffer -> buffer.setMemory(8, 8, (byte) 1)),
                write("putTextAscii", buffer -> buffer.putTextAscii(8, "abc")),
                write("putTextAscii range", buffer -> buffer.putTextAscii(8, "abc", 1, 2)),
                write("putTextUtf8", buffer -> buffer.putTextUtf8(8, "äbc")),
                write("putTextUtf8 range", buffer -> buffer.putTextUtf8(8, "äbc", 1, 2))
        );
    }

    private static Arguments write(final String name, final Consumer<MutableMemoryBuffer> write) {
        return Arguments.of(name, write);
    }

    /** Direct memory filled with a pattern, and a flyweight wrapping it, made read-only. */
    private record ReadOnlyMemory(MutableMemoryBuffer memory, MemoryBufferFlyweight readOnly) {}

    private static ReadOnlyMemory readOnlyDirectMemory() {
        final MutableMemoryBuffer memory = MemoryBuffers.allocateDirect(READ_ONLY_CAPACITY);
        fill(memory, 7);
        final MemoryBufferFlyweight readOnly = MemoryBuffers.flyweight();
        readOnly.wrap(memory.address(), READ_ONLY_CAPACITY);
        assertFalse(readOnly.isReadOnly());
        readOnly.makeReadOnly();
        assertTrue(readOnly.isReadOnly());
        return new ReadOnlyMemory(memory, readOnly);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writes")
    void writesToReadOnlyMemoryThrowWithoutWriting(final String name, final Consumer<MutableMemoryBuffer> write) {
        final ReadOnlyMemory memoryAndReadOnly = readOnlyDirectMemory();
        final MutableMemoryBuffer memory = memoryAndReadOnly.memory();
        final MutableMemoryBuffer readOnly = memoryAndReadOnly.readOnly();
        final byte[] before = bytes(memory);
        assertThrows(ReadOnlyBufferException.class, () -> write.accept(readOnly), name);
        assertArrayEquals(before, bytes(memory), name);
        assertArrayEquals(before, bytes(readOnly), "reads " + name);
    }

    @Test
    void writesBeyondTheCapacityOfReadOnlyMemoryThrowIndexOutOfBounds() {
        final MutableMemoryBuffer readOnly = readOnlyDirectMemory().readOnly();
        final IndexOutOfBoundsException beyond = assertThrows(IndexOutOfBoundsException.class,
                () -> readOnly.putLong(READ_ONLY_CAPACITY - 4, 1));
        assertEquals(assertThrows(IndexOutOfBoundsException.class,
                () -> readOnly.getLong(READ_ONLY_CAPACITY - 4)).getMessage(), beyond.getMessage(),
                "same message as a read");
        assertThrows(IndexOutOfBoundsException.class, () -> readOnly.putLong(-1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> readOnly.putBytes(READ_ONLY_CAPACITY - 4, new byte[8]));
        assertThrows(IndexOutOfBoundsException.class, () -> readOnly.setMemory(0, -1, (byte) 1));
    }

    @Test
    void rangesOfReadOnlyMemoryStayReadOnly() {
        final ReadOnlyMemory memoryAndReadOnly = readOnlyDirectMemory();
        final MemoryBufferFlyweight range = MemoryBuffers.flyweight();
        range.wrap(memoryAndReadOnly.readOnly(), 8, 16);
        assertTrue(range.isReadOnly());
        assertEquals(memoryAndReadOnly.memory().getLong(8), range.getLong(0));
        assertThrows(ReadOnlyBufferException.class, () -> range.putLong(0, 1));
        assertThrows(ReadOnlyBufferException.class, () -> range.putBytes(0, new byte[16]));
    }

    @Test
    void wrappingWritableMemoryAgainMakesAFlyweightWritable() {
        final ReadOnlyMemory memoryAndReadOnly = readOnlyDirectMemory();
        final MemoryBufferFlyweight flyweight = memoryAndReadOnly.readOnly();
        flyweight.wrap(flyweight.address(), READ_ONLY_CAPACITY);
        assertFalse(flyweight.isReadOnly());
        flyweight.putLong(8, LONG);
        assertEquals(LONG, memoryAndReadOnly.memory().getLong(8));
        flyweight.makeReadOnly();
        flyweight.unwrap();
        assertWrapsNoMemory(flyweight);
    }

    @Test
    void rangesOfWritableMemoryTypedAsReadableBuffersAreWritable() {
        final MemoryBuffer memory = MemoryBuffers.allocate(16);
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        flyweight.wrap(memory, 8, 8);
        assertFalse(flyweight.isReadOnly());
        flyweight.putLong(0, LONG);
        assertEquals(LONG, memory.getLong(8));
    }

    //--- buffers of other implementations, such as test doubles ---

    /** A test double of another class than the buffers, delegating to the given buffer. */
    private static MemoryBuffer otherImplementation(final MemoryBuffer buffer) {
        final MemoryBuffer other = mock(MemoryBuffer.class, delegatesTo(buffer));
        assertFalse(other instanceof MemoryBufferImpl);
        return other;
    }

    @ParameterizedTest(name = "direct: {0}")
    @ValueSource(booleans = {false, true})
    void flyweightsCannotWrapBuffersOfOtherImplementations(final boolean direct) {
        final MutableMemoryBuffer memory = direct ? MemoryBuffers.allocateDirect(16) : MemoryBuffers.allocate(16);
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        flyweight.wrap(new byte[8]);
        assertThrows(IllegalArgumentException.class, () -> flyweight.wrap(otherImplementation(memory)));
        assertThrows(IllegalArgumentException.class, () -> flyweight.wrap(otherImplementation(memory), 0, 8));
        assertEquals(8, flyweight.capacity(), "unchanged");
    }

    @ParameterizedTest(name = "direct: {0}")
    @ValueSource(booleans = {false, true})
    void bytesAreCopiedFromBuffersOfOtherImplementations(final boolean direct) {
        final MutableMemoryBuffer source = direct ? MemoryBuffers.allocateDirect(16) : MemoryBuffers.allocate(16);
        fill(source, 5);
        final MutableMemoryBuffer target = MemoryBuffers.allocate(32);
        target.putBytes(8, otherImplementation(source), 4, 8);
        final byte[] copied = new byte[8];
        target.getBytes(8, copied);
        final byte[] expected = new byte[8];
        source.getBytes(4, expected);
        assertArrayEquals(expected, copied);
        assertEquals(0, target.getLong(0));
        assertEquals(0, target.getLong(16));
        assertThrows(IndexOutOfBoundsException.class, () -> target.putBytes(0, otherImplementation(source), 12, 8));
    }

    //--- wrapping only flyweights ---

    private static Stream<Arguments> buffersThatAreNoFlyweights() {
        return Stream.of(
                Arguments.of("allocate", MemoryBuffers.allocate(16)),
                Arguments.of("allocateDirect", MemoryBuffers.allocateDirect(16)),
                Arguments.of("wrap long[]", MemoryBuffers.wrap(new long[2])),
                Arguments.of("expandable", MemoryBuffers.expandable(16)),
                Arguments.of("expandableDirect", MemoryBuffers.expandableDirect(16)),
                Arguments.of("EMPTY", MemoryBuffers.EMPTY)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("buffersThatAreNoFlyweights")
    void buffersThatAreNoFlyweightsCannotBeWrappedEvenWhenCast(final String name, final MutableMemoryBuffer buffer) {
        final int capacity = buffer.capacity();
        //the cast is the point of this test: rewrapping must fail even through the flyweight type
        final MemoryBufferFlyweight flyweight = (MemoryBufferFlyweight) buffer;
        final MutableMemoryBuffer other = MemoryBuffers.allocateDirect(32);
        final long address = other.address();
        final Runnable[] wraps = {
                () -> flyweight.wrap(address, 8),
                () -> flyweight.wrap(new byte[8]),
                () -> flyweight.wrap(new byte[8], 0, 8),
                () -> flyweight.wrap(ByteBuffer.allocate(8)),
                () -> flyweight.wrap(ByteBuffer.allocate(8), 0, 8),
                () -> flyweight.wrap(other),
                () -> flyweight.wrap(other, 0, 8),
                flyweight::makeReadOnly,
                flyweight::unwrap
        };
        for (final Runnable wrap : wraps) {
            assertThrows(UnsupportedOperationException.class, wrap::run, name);
            assertEquals(capacity, buffer.capacity(), name);
            assertFalse(buffer.isReadOnly(), name);
        }
        Reference.reachabilityFence(other);
    }

    @Test
    void emptyBufferHasNoCapacity() {
        final MutableMemoryBuffer empty = MemoryBuffers.EMPTY;
        assertEquals(0, empty.capacity());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.getByte(0));
        assertThrows(IndexOutOfBoundsException.class, () -> empty.putByte(0, (byte) 1));
        assertThrows(IndexOutOfBoundsException.class, () -> empty.putLongRelease(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> empty.putBytes(0, new byte[1]));
    }

    //--- expandable buffers ---

    @ParameterizedTest
    @EnumSource(value = TestMemory.class, names = {"EXPANDABLE", "EXPANDABLE_DIRECT"})
    void expandableBuffersGrowInAdvanceOnEnsuringCapacity(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(16);
        fill(buffer, 3);
        final byte[] content = bytes(buffer);
        buffer.ensureCapacity(16);
        assertEquals(16, buffer.capacity(), "sufficient");
        buffer.ensureCapacity(100);
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(100);
        buffer.ensureCapacity(200, 8);
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(208);
        final byte[] grown = new byte[16];
        buffer.getBytes(0, grown);
        assertArrayEquals(content, grown);
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCapacity(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.ensureCapacity(-1, 8));
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCapacity(0, -1));
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCapacity(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> buffer.ensureCapacity(Integer.MAX_VALUE, 8));
    }

    @Test
    void ensuringCapacityOfBuffersThatAreNotExpandableThrowsOnlyIfTheyAreTooSmall() {
        final MemoryBufferFlyweight readOnly = MemoryBuffers.flyweight();
        readOnly.wrap(ByteBuffer.allocate(16).asReadOnlyBuffer());
        final MutableMemoryBuffer[] buffers = {
                MemoryBuffers.allocate(16), MemoryBuffers.allocateDirect(16), TestMemory.BYTE_ARRAY_RANGE.create(16),
                readOnly
        };
        for (final MutableMemoryBuffer buffer : buffers) {
            buffer.ensureCapacity(16);
            buffer.ensureCapacity(8, 8);
            assertThrows(UnsupportedOperationException.class, () -> buffer.ensureCapacity(17), buffer.toString());
            assertThrows(UnsupportedOperationException.class, () -> buffer.ensureCapacity(8, 9), buffer.toString());
            assertEquals(16, buffer.capacity(), buffer.toString());
        }
        MemoryBuffers.EMPTY.ensureCapacity(0);
        assertThrows(UnsupportedOperationException.class, () -> MemoryBuffers.EMPTY.ensureCapacity(1));
    }

    @Test
    void expandableBuffersGrowIntoMemoryLikeTheirOwnEvenFromCapacity0() {
        final MutableMemoryBuffer heap = MemoryBuffers.expandable(0);
        final MutableMemoryBuffer direct = MemoryBuffers.expandableDirect(0);
        assertFalse(heap.isReadOnly());
        assertFalse(direct.isReadOnly());
        heap.putLong(100, LONG);
        direct.putLong(100, LONG);
        assertFalse(heap.isDirect());
        assertTrue(direct.isDirect());
        assertEquals(LONG, heap.getLong(100));
        assertEquals(LONG, direct.getLong(100));
    }

    @ParameterizedTest
    @EnumSource(value = TestMemory.class, names = {"EXPANDABLE", "EXPANDABLE_DIRECT"})
    void expandableBuffersGrowOnWritesBeyondTheirCapacity(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(16);
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
        //the non-native order adds with compare-and-set, after growing as well
        final ByteOrder nonNative = ByteOrder.nativeOrder() == LITTLE_ENDIAN ? BIG_ENDIAN : LITTLE_ENDIAN;
        assertEquals(0, buffer.getAndAddLong(3000, 5, nonNative));
        assertEquals(5, buffer.getLong(3000, nonNative));
        buffer.putTextAscii(4000, "abc");
        assertEquals("abc", buffer.getTextAscii(4000, 3));
        assertEquals(99, buffer.getByte(0));
        assertThat(buffer.alignment()).isGreaterThanOrEqualTo(Long.BYTES);
    }

    //--- bulk copies ---

    static Stream<Arguments> memoryPairs() {
        return Arrays.stream(TestMemory.values())
                .flatMap(source -> Arrays.stream(TestMemory.values()).map(target -> Arguments.of(source, target)));
    }

    @ParameterizedTest
    @MethodSource("memoryPairs")
    void copiesBetweenAllKindsOfMemory(final TestMemory sourceMemory, final TestMemory targetMemory) {
        final MutableMemoryBuffer source = sourceMemory.create(CAPACITY);
        final MutableMemoryBuffer target = targetMemory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void copiesToAndFromArraysAndByteBuffers(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
    @EnumSource(TestMemory.class)
    void copiesWithinTheSameBufferAndSetsMemory(final TestMemory memory) {
        final MutableMemoryBuffer buffer = memory.create(CAPACITY);
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
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        //re-wrapping the same direct byte buffer, as wrapping another one creates a segment with FFM
        final MemoryBufferFlyweight byteBufferFlyweight = MemoryBuffers.flyweight();
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

    @Test
    void wrappingAndCopyingDifferentByteBuffersDoesNotAllocateUnlessFfmCannotAccessTheirFields() {
        assumeTrue(MemoryAccessMode.inUse() != MemoryAccessMode.FFM ||
                ByteBuffer.class.getModule().isOpen("java.nio", MemoryBufferImplTest.class.getModule()),
                "FFM allocates a segment per byte buffer unless java.nio is open");
        final com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final ByteBuffer[] byteBuffers = {
                ByteBuffer.allocateDirect(CAPACITY), ByteBuffer.allocateDirect(CAPACITY),
                ByteBuffer.allocate(CAPACITY).asReadOnlyBuffer(), ByteBuffer.allocateDirect(CAPACITY).asReadOnlyBuffer()
        };
        final MemoryBufferFlyweight flyweight = MemoryBuffers.flyweight();
        final MutableMemoryBuffer heap = MemoryBuffers.allocate(CAPACITY);
        final long[] sum = {0};
        final Consumer<Integer> rounds = n -> {
            for (int i = 0; i < n; i++) {
                final ByteBuffer byteBuffer = byteBuffers[i & 3];
                flyweight.wrap(byteBuffer, 8, 16);
                sum[0] += flyweight.getLong(0);
                heap.getBytes(0, byteBuffers[i & 1], 8, 8);
                heap.putBytes(8, byteBuffer, 0, 8);
            }
        };
        rounds.accept(100_000);
        final long before = threads.getCurrentThreadAllocatedBytes();
        rounds.accept(10_000);
        final long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        //NOTE: tolerate sporadic bytes as above, while a segment per round would amount to at least 720 KB
        assertThat(allocated).isLessThan(10_000);
        assertEquals(0, sum[0]);
    }
}
