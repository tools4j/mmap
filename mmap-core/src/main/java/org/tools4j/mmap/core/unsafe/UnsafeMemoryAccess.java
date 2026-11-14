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
package org.tools4j.mmap.core.unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static org.tools4j.mmap.core.unsafe.UnsafeAccess.UNSAFE;

/**
 * Memory access through {@code jdk.internal.misc.Unsafe}, the default.
 * <p>
 * Direct memory is accessed with a null base. A heap base is a {@code byte[]} or a {@code long[]}, and each plain
 * access passes it to Unsafe with its array type: a {@code byte[]} by the {@code byte[]} overloads, a {@code long[]}
 * cast by the others, where only it arrives. The JIT compiler treats a plain access through a base of unknown type as
 * one that may touch any field of any object, so in a loop it reloads the buffer's own fields on every access and
 * doesn't unroll or vectorise it, which made loops over heap memory, and over direct memory at call sites that also
 * see heap memory, three to six times slower; the type check of the cast is moved out of the loop. Ordered and atomic
 * accesses pass the base as it is: their barriers make each access load the buffer's fields again anyway, and keep
 * the type check in the loop, a load of the array's class per access, which made loops of them over heap memory up
 * to a fifth slower.
 */
@SuppressWarnings("RedundantCast")
@Unsafe
public enum UnsafeMemoryAccess implements MemoryAccess {
    /** The only instance. */
    INSTANCE;

    private static final long BYTE_ARRAY_BASE_OFFSET = arrayBaseOffset(byte[].class);
    private static final long LONG_ARRAY_BASE_OFFSET = arrayBaseOffset(long[].class);
    private static final long BUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(Buffer.class, "address");
    private static final long BYTE_BUFFER_ARRAY_OFFSET = UNSAFE.objectFieldOffset(ByteBuffer.class, "hb");
    private static final long BYTE_BUFFER_ARRAY_OFFSET_OFFSET = UNSAFE.objectFieldOffset(ByteBuffer.class, "offset");

    /**
     * Calls {@code Unsafe.arrayBaseOffset} reflectively: it returns an int up to Java 22 and a long since Java 23, and
     * a direct call compiles to the descriptor of the JDK compiling it, failing on the other versions.
     */
    private static long arrayBaseOffset(final Class<?> arrayClass) {
        try {
            return ((Number) jdk.internal.misc.Unsafe.class.getMethod("arrayBaseOffset", Class.class)
                    .invoke(UNSAFE, arrayClass)).longValue();
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot get the base offset of " + arrayClass.getSimpleName(), e);
        }
    }

    @Override
    public Object heapBase(final byte[] array) {
        return array;
    }

    @Override
    public Object heapBase(final long[] array) {
        return array;
    }

    @Override
    public long heapOffset(final byte[] array, final int byteOffset) {
        return BYTE_ARRAY_BASE_OFFSET + byteOffset;
    }

    @Override
    public long heapOffset(final long[] array, final int byteOffset) {
        return LONG_ARRAY_BASE_OFFSET + byteOffset;
    }

    /** Reads the array of a heap byte buffer from its field, since read-only ones hide it. */
    @Override
    public Object heapBase(final ByteBuffer buffer) {
        final Object array = UNSAFE.getReference(buffer, BYTE_BUFFER_ARRAY_OFFSET);
        if (array == null) {
            throw new IllegalArgumentException("Cannot wrap a heap byte buffer not backed by a byte[]: " + buffer);
        }
        return array;
    }

    @Override
    public long heapOffset(final ByteBuffer buffer, final int byteOffset) {
        return BYTE_ARRAY_BASE_OFFSET + UNSAFE.getInt(buffer, BYTE_BUFFER_ARRAY_OFFSET_OFFSET) + byteOffset;
    }

    @Override
    public long address(final ByteBuffer buffer) {
        return UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET);
    }

    @Override
    public byte getByte(final long address) {
        return UNSAFE.getByte(null, address);
    }

    @Override
    public byte getByte(final Object base, final long offset) {
        return UNSAFE.getByte((long[]) base, offset);
    }

    @Override
    public byte getByte(final byte[] base, final long offset) {
        return UNSAFE.getByte(base, offset);
    }

    @Override
    public void putByte(final long address, final byte value) {
        UNSAFE.putByte(null, address, value);
    }

    @Override
    public void putByte(final Object base, final long offset, final byte value) {
        UNSAFE.putByte((long[]) base, offset, value);
    }

    @Override
    public void putByte(final byte[] base, final long offset, final byte value) {
        UNSAFE.putByte(base, offset, value);
    }

    @Override
    public short getShort(final long address) {
        return UNSAFE.getShortUnaligned(null, address);
    }

    @Override
    public short getShort(final Object base, final long offset) {
        return UNSAFE.getShortUnaligned((long[]) base, offset);
    }

    @Override
    public short getShort(final byte[] base, final long offset) {
        return UNSAFE.getShortUnaligned(base, offset);
    }

    @Override
    public void putShort(final long address, final short value) {
        UNSAFE.putShortUnaligned(null, address, value);
    }

    @Override
    public void putShort(final Object base, final long offset, final short value) {
        UNSAFE.putShortUnaligned((long[]) base, offset, value);
    }

    @Override
    public void putShort(final byte[] base, final long offset, final short value) {
        UNSAFE.putShortUnaligned(base, offset, value);
    }

    @Override
    public char getChar(final long address) {
        return UNSAFE.getCharUnaligned(null, address);
    }

    @Override
    public char getChar(final Object base, final long offset) {
        return UNSAFE.getCharUnaligned((long[]) base, offset);
    }

    @Override
    public char getChar(final byte[] base, final long offset) {
        return UNSAFE.getCharUnaligned(base, offset);
    }

    @Override
    public void putChar(final long address, final char value) {
        UNSAFE.putCharUnaligned(null, address, value);
    }

    @Override
    public void putChar(final Object base, final long offset, final char value) {
        UNSAFE.putCharUnaligned((long[]) base, offset, value);
    }

    @Override
    public void putChar(final byte[] base, final long offset, final char value) {
        UNSAFE.putCharUnaligned(base, offset, value);
    }

    @Override
    public int getInt(final long address) {
        return UNSAFE.getIntUnaligned(null, address);
    }

    @Override
    public int getInt(final Object base, final long offset) {
        return UNSAFE.getIntUnaligned((long[]) base, offset);
    }

    @Override
    public int getInt(final byte[] base, final long offset) {
        return UNSAFE.getIntUnaligned(base, offset);
    }

    @Override
    public void putInt(final long address, final int value) {
        UNSAFE.putIntUnaligned(null, address, value);
    }

    @Override
    public void putInt(final Object base, final long offset, final int value) {
        UNSAFE.putIntUnaligned((long[]) base, offset, value);
    }

    @Override
    public void putInt(final byte[] base, final long offset, final int value) {
        UNSAFE.putIntUnaligned(base, offset, value);
    }

    @Override
    public long getLong(final long address) {
        return UNSAFE.getLongUnaligned(null, address);
    }

    @Override
    public long getLong(final Object base, final long offset) {
        return UNSAFE.getLongUnaligned((long[]) base, offset);
    }

    @Override
    public long getLong(final byte[] base, final long offset) {
        return UNSAFE.getLongUnaligned(base, offset);
    }

    @Override
    public void putLong(final long address, final long value) {
        UNSAFE.putLongUnaligned(null, address, value);
    }

    @Override
    public void putLong(final Object base, final long offset, final long value) {
        UNSAFE.putLongUnaligned((long[]) base, offset, value);
    }

    @Override
    public void putLong(final byte[] base, final long offset, final long value) {
        UNSAFE.putLongUnaligned(base, offset, value);
    }

    @Override
    public byte getByteVolatile(final long address) {
        return UNSAFE.getByteVolatile(null, address);
    }

    @Override
    public byte getByteVolatile(final Object base, final long offset) {
        return UNSAFE.getByteVolatile(base, offset);
    }

    @Override
    public byte getByteVolatile(final byte[] base, final long offset) {
        return UNSAFE.getByteVolatile(base, offset);
    }

    @Override
    public void putByteVolatile(final long address, final byte value) {
        UNSAFE.putByteVolatile(null, address, value);
    }

    @Override
    public void putByteVolatile(final Object base, final long offset, final byte value) {
        UNSAFE.putByteVolatile(base, offset, value);
    }

    @Override
    public void putByteVolatile(final byte[] base, final long offset, final byte value) {
        UNSAFE.putByteVolatile(base, offset, value);
    }

    @Override
    public short getShortVolatile(final long address) {
        return UNSAFE.getShortVolatile(null, address);
    }

    @Override
    public short getShortVolatile(final Object base, final long offset) {
        return UNSAFE.getShortVolatile(base, offset);
    }

    @Override
    public void putShortVolatile(final long address, final short value) {
        UNSAFE.putShortVolatile(null, address, value);
    }

    @Override
    public void putShortVolatile(final Object base, final long offset, final short value) {
        UNSAFE.putShortVolatile(base, offset, value);
    }

    @Override
    public char getCharVolatile(final long address) {
        return UNSAFE.getCharVolatile(null, address);
    }

    @Override
    public char getCharVolatile(final Object base, final long offset) {
        return UNSAFE.getCharVolatile(base, offset);
    }

    @Override
    public void putCharVolatile(final long address, final char value) {
        UNSAFE.putCharVolatile(null, address, value);
    }

    @Override
    public void putCharVolatile(final Object base, final long offset, final char value) {
        UNSAFE.putCharVolatile(base, offset, value);
    }

    @Override
    public int getIntOpaque(final long address) {
        return UNSAFE.getIntOpaque(null, address);
    }

    @Override
    public int getIntOpaque(final Object base, final long offset) {
        return UNSAFE.getIntOpaque(base, offset);
    }

    @Override
    public int getIntAcquire(final long address) {
        return UNSAFE.getIntAcquire(null, address);
    }

    @Override
    public int getIntAcquire(final Object base, final long offset) {
        return UNSAFE.getIntAcquire(base, offset);
    }

    @Override
    public int getIntVolatile(final long address) {
        return UNSAFE.getIntVolatile(null, address);
    }

    @Override
    public int getIntVolatile(final Object base, final long offset) {
        return UNSAFE.getIntVolatile(base, offset);
    }

    @Override
    public void putIntOpaque(final long address, final int value) {
        UNSAFE.putIntOpaque(null, address, value);
    }

    @Override
    public void putIntOpaque(final Object base, final long offset, final int value) {
        UNSAFE.putIntOpaque(base, offset, value);
    }

    @Override
    public void putIntRelease(final long address, final int value) {
        UNSAFE.putIntRelease(null, address, value);
    }

    @Override
    public void putIntRelease(final Object base, final long offset, final int value) {
        UNSAFE.putIntRelease(base, offset, value);
    }

    @Override
    public void putIntVolatile(final long address, final int value) {
        UNSAFE.putIntVolatile(null, address, value);
    }

    @Override
    public void putIntVolatile(final Object base, final long offset, final int value) {
        UNSAFE.putIntVolatile(base, offset, value);
    }

    @Override
    public boolean compareAndSetInt(final long address, final int expected, final int update) {
        return UNSAFE.compareAndSetInt(null, address, expected, update);
    }

    @Override
    public boolean compareAndSetInt(final Object base, final long offset, final int expected, final int update) {
        return UNSAFE.compareAndSetInt(base, offset, expected, update);
    }

    @Override
    public int compareAndExchangeInt(final long address, final int expected, final int update) {
        return UNSAFE.compareAndExchangeInt(null, address, expected, update);
    }

    @Override
    public int compareAndExchangeInt(final Object base, final long offset, final int expected, final int update) {
        return UNSAFE.compareAndExchangeInt(base, offset, expected, update);
    }

    @Override
    public int getAndAddInt(final long address, final int delta) {
        return UNSAFE.getAndAddInt(null, address, delta);
    }

    @Override
    public int getAndAddInt(final Object base, final long offset, final int delta) {
        return UNSAFE.getAndAddInt(base, offset, delta);
    }

    @Override
    public int getAndSetInt(final long address, final int value) {
        return UNSAFE.getAndSetInt(null, address, value);
    }

    @Override
    public int getAndSetInt(final Object base, final long offset, final int value) {
        return UNSAFE.getAndSetInt(base, offset, value);
    }

    @Override
    public long getLongOpaque(final long address) {
        return UNSAFE.getLongOpaque(null, address);
    }

    @Override
    public long getLongOpaque(final Object base, final long offset) {
        return UNSAFE.getLongOpaque(base, offset);
    }

    @Override
    public long getLongAcquire(final long address) {
        return UNSAFE.getLongAcquire(null, address);
    }

    @Override
    public long getLongAcquire(final Object base, final long offset) {
        return UNSAFE.getLongAcquire(base, offset);
    }

    @Override
    public long getLongVolatile(final long address) {
        return UNSAFE.getLongVolatile(null, address);
    }

    @Override
    public long getLongVolatile(final Object base, final long offset) {
        return UNSAFE.getLongVolatile(base, offset);
    }

    @Override
    public void putLongOpaque(final long address, final long value) {
        UNSAFE.putLongOpaque(null, address, value);
    }

    @Override
    public void putLongOpaque(final Object base, final long offset, final long value) {
        UNSAFE.putLongOpaque(base, offset, value);
    }

    @Override
    public void putLongRelease(final long address, final long value) {
        UNSAFE.putLongRelease(null, address, value);
    }

    @Override
    public void putLongRelease(final Object base, final long offset, final long value) {
        UNSAFE.putLongRelease(base, offset, value);
    }

    @Override
    public void putLongVolatile(final long address, final long value) {
        UNSAFE.putLongVolatile(null, address, value);
    }

    @Override
    public void putLongVolatile(final Object base, final long offset, final long value) {
        UNSAFE.putLongVolatile(base, offset, value);
    }

    @Override
    public boolean compareAndSetLong(final long address, final long expected, final long update) {
        return UNSAFE.compareAndSetLong(null, address, expected, update);
    }

    @Override
    public boolean compareAndSetLong(final Object base, final long offset, final long expected, final long update) {
        return UNSAFE.compareAndSetLong(base, offset, expected, update);
    }

    @Override
    public long compareAndExchangeLong(final long address, final long expected, final long update) {
        return UNSAFE.compareAndExchangeLong(null, address, expected, update);
    }

    @Override
    public long compareAndExchangeLong(final Object base, final long offset, final long expected, final long update) {
        return UNSAFE.compareAndExchangeLong(base, offset, expected, update);
    }

    @Override
    public long getAndAddLong(final long address, final long delta) {
        return UNSAFE.getAndAddLong(null, address, delta);
    }

    @Override
    public long getAndAddLong(final Object base, final long offset, final long delta) {
        return UNSAFE.getAndAddLong(base, offset, delta);
    }

    @Override
    public long getAndSetLong(final long address, final long value) {
        return UNSAFE.getAndSetLong(null, address, value);
    }

    @Override
    public long getAndSetLong(final Object base, final long offset, final long value) {
        return UNSAFE.getAndSetLong(base, offset, value);
    }

    @Override
    public void copyMemory(final Object sourceBase, final long sourceOffset, final Object destinationBase,
                           final long destinationOffset, final long length) {
        UNSAFE.copyMemory(sourceBase, sourceOffset, destinationBase, destinationOffset, length);
    }

    @Override
    public void setMemory(final Object base, final long offset, final long length, final byte value) {
        UNSAFE.setMemory(base, offset, length, value);
    }
}
