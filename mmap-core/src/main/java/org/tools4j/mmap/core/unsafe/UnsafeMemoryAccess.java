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
 */
@Unsafe
public enum UnsafeMemoryAccess implements MemoryAccess {
    /** The only instance. */
    INSTANCE;

    private static final long BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final long LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);
    private static final long BUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset(Buffer.class, "address");

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

    @Override
    public long address(final ByteBuffer buffer) {
        return UNSAFE.getLong(buffer, BUFFER_ADDRESS_OFFSET);
    }

    @Override
    public byte getByte(final Object base, final long offset) {
        return UNSAFE.getByte(base, offset);
    }

    @Override
    public void putByte(final Object base, final long offset, final byte value) {
        UNSAFE.putByte(base, offset, value);
    }

    @Override
    public short getShort(final Object base, final long offset) {
        return UNSAFE.getShortUnaligned(base, offset);
    }

    @Override
    public void putShort(final Object base, final long offset, final short value) {
        UNSAFE.putShortUnaligned(base, offset, value);
    }

    @Override
    public char getChar(final Object base, final long offset) {
        return UNSAFE.getCharUnaligned(base, offset);
    }

    @Override
    public void putChar(final Object base, final long offset, final char value) {
        UNSAFE.putCharUnaligned(base, offset, value);
    }

    @Override
    public int getInt(final Object base, final long offset) {
        return UNSAFE.getIntUnaligned(base, offset);
    }

    @Override
    public void putInt(final Object base, final long offset, final int value) {
        UNSAFE.putIntUnaligned(base, offset, value);
    }

    @Override
    public long getLong(final Object base, final long offset) {
        return UNSAFE.getLongUnaligned(base, offset);
    }

    @Override
    public void putLong(final Object base, final long offset, final long value) {
        UNSAFE.putLongUnaligned(base, offset, value);
    }

    @Override
    public byte getByteVolatile(final Object base, final long offset) {
        return UNSAFE.getByteVolatile(base, offset);
    }

    @Override
    public void putByteVolatile(final Object base, final long offset, final byte value) {
        UNSAFE.putByteVolatile(base, offset, value);
    }

    @Override
    public short getShortVolatile(final Object base, final long offset) {
        return UNSAFE.getShortVolatile(base, offset);
    }

    @Override
    public void putShortVolatile(final Object base, final long offset, final short value) {
        UNSAFE.putShortVolatile(base, offset, value);
    }

    @Override
    public char getCharVolatile(final Object base, final long offset) {
        return UNSAFE.getCharVolatile(base, offset);
    }

    @Override
    public void putCharVolatile(final Object base, final long offset, final char value) {
        UNSAFE.putCharVolatile(base, offset, value);
    }

    @Override
    public int getIntOpaque(final Object base, final long offset) {
        return UNSAFE.getIntOpaque(base, offset);
    }

    @Override
    public int getIntAcquire(final Object base, final long offset) {
        return UNSAFE.getIntAcquire(base, offset);
    }

    @Override
    public int getIntVolatile(final Object base, final long offset) {
        return UNSAFE.getIntVolatile(base, offset);
    }

    @Override
    public void putIntOpaque(final Object base, final long offset, final int value) {
        UNSAFE.putIntOpaque(base, offset, value);
    }

    @Override
    public void putIntRelease(final Object base, final long offset, final int value) {
        UNSAFE.putIntRelease(base, offset, value);
    }

    @Override
    public void putIntVolatile(final Object base, final long offset, final int value) {
        UNSAFE.putIntVolatile(base, offset, value);
    }

    @Override
    public boolean compareAndSetInt(final Object base, final long offset, final int expected, final int update) {
        return UNSAFE.compareAndSetInt(base, offset, expected, update);
    }

    @Override
    public int compareAndExchangeInt(final Object base, final long offset, final int expected, final int update) {
        return UNSAFE.compareAndExchangeInt(base, offset, expected, update);
    }

    @Override
    public int getAndAddInt(final Object base, final long offset, final int delta) {
        return UNSAFE.getAndAddInt(base, offset, delta);
    }

    @Override
    public int getAndSetInt(final Object base, final long offset, final int value) {
        return UNSAFE.getAndSetInt(base, offset, value);
    }

    @Override
    public long getLongOpaque(final Object base, final long offset) {
        return UNSAFE.getLongOpaque(base, offset);
    }

    @Override
    public long getLongAcquire(final Object base, final long offset) {
        return UNSAFE.getLongAcquire(base, offset);
    }

    @Override
    public long getLongVolatile(final Object base, final long offset) {
        return UNSAFE.getLongVolatile(base, offset);
    }

    @Override
    public void putLongOpaque(final Object base, final long offset, final long value) {
        UNSAFE.putLongOpaque(base, offset, value);
    }

    @Override
    public void putLongRelease(final Object base, final long offset, final long value) {
        UNSAFE.putLongRelease(base, offset, value);
    }

    @Override
    public void putLongVolatile(final Object base, final long offset, final long value) {
        UNSAFE.putLongVolatile(base, offset, value);
    }

    @Override
    public boolean compareAndSetLong(final Object base, final long offset, final long expected, final long update) {
        return UNSAFE.compareAndSetLong(base, offset, expected, update);
    }

    @Override
    public long compareAndExchangeLong(final Object base, final long offset, final long expected, final long update) {
        return UNSAFE.compareAndExchangeLong(base, offset, expected, update);
    }

    @Override
    public long getAndAddLong(final Object base, final long offset, final long delta) {
        return UNSAFE.getAndAddLong(base, offset, delta);
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
