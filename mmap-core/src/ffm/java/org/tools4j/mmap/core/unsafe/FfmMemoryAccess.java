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

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_CHAR_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * Memory access through the Foreign Function and Memory API, Java 22 or later, compiled only when building with Java
 * 22 or later and loaded by name.
 * <p>
 * Direct memory is accessed through one segment spanning all addresses, so that wrapping an address creates no
 * segment; creating that segment is a restricted operation, warning unless {@code --enable-native-access} allows it.
 * A {@code byte[]} is its own heap base, accessed through byte array views, which only support plain access. A
 * {@code long[]} is represented by its heap segment, created once per array, which also supports ordered and atomic
 * access.
 */
@Unsafe
public enum FfmMemoryAccess implements MemoryAccess {
    /** The only instance. */
    INSTANCE;

    private static final MemorySegment ALL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
    private static final long BYTE_PATTERN = 0x0101010101010101L;

    private static final VarHandle BYTE = JAVA_BYTE.varHandle();
    private static final VarHandle SHORT = JAVA_SHORT.varHandle();
    private static final VarHandle SHORT_UNALIGNED = JAVA_SHORT_UNALIGNED.varHandle();
    private static final VarHandle CHAR = JAVA_CHAR.varHandle();
    private static final VarHandle CHAR_UNALIGNED = JAVA_CHAR_UNALIGNED.varHandle();
    private static final VarHandle INT = JAVA_INT.varHandle();
    private static final VarHandle INT_UNALIGNED = JAVA_INT_UNALIGNED.varHandle();
    private static final VarHandle LONG = JAVA_LONG.varHandle();
    private static final VarHandle LONG_UNALIGNED = JAVA_LONG_UNALIGNED.varHandle();

    private static final VarHandle BYTES = MethodHandles.arrayElementVarHandle(byte[].class);
    private static final VarHandle BYTES_AS_SHORT = MethodHandles.byteArrayViewVarHandle(short[].class,
            ByteOrder.nativeOrder());
    private static final VarHandle BYTES_AS_CHAR = MethodHandles.byteArrayViewVarHandle(char[].class,
            ByteOrder.nativeOrder());
    private static final VarHandle BYTES_AS_INT = MethodHandles.byteArrayViewVarHandle(int[].class,
            ByteOrder.nativeOrder());
    private static final VarHandle BYTES_AS_LONG = MethodHandles.byteArrayViewVarHandle(long[].class,
            ByteOrder.nativeOrder());

    /** Segment of direct or long[] memory: byte[] memory never reaches the methods using it. */
    private static MemorySegment segment(final Object base) {
        return base == null ? ALL : (MemorySegment) base;
    }

    @Override
    public Object heapBase(final byte[] array) {
        return array;
    }

    @Override
    public Object heapBase(final long[] array) {
        return MemorySegment.ofArray(array);
    }

    @Override
    public long heapOffset(final byte[] array, final int byteOffset) {
        return byteOffset;
    }

    @Override
    public long heapOffset(final long[] array, final int byteOffset) {
        return byteOffset;
    }

    @Override
    public long address(final ByteBuffer buffer) {
        //the segment of a buffer starts at its position
        return MemorySegment.ofBuffer(buffer).address() - buffer.position();
    }

    @Override
    public byte getByte(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return array[(int) offset];
        }
        return (byte) BYTE.get(segment(base), offset);
    }

    @Override
    public void putByte(final Object base, final long offset, final byte value) {
        if (base instanceof byte[] array) {
            array[(int) offset] = value;
        } else {
            BYTE.set(segment(base), offset, value);
        }
    }

    @Override
    public short getShort(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return (short) BYTES_AS_SHORT.get(array, (int) offset);
        }
        return (short) SHORT_UNALIGNED.get(segment(base), offset);
    }

    @Override
    public void putShort(final Object base, final long offset, final short value) {
        if (base instanceof byte[] array) {
            BYTES_AS_SHORT.set(array, (int) offset, value);
        } else {
            SHORT_UNALIGNED.set(segment(base), offset, value);
        }
    }

    @Override
    public char getChar(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return (char) BYTES_AS_CHAR.get(array, (int) offset);
        }
        return (char) CHAR_UNALIGNED.get(segment(base), offset);
    }

    @Override
    public void putChar(final Object base, final long offset, final char value) {
        if (base instanceof byte[] array) {
            BYTES_AS_CHAR.set(array, (int) offset, value);
        } else {
            CHAR_UNALIGNED.set(segment(base), offset, value);
        }
    }

    @Override
    public int getInt(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return (int) BYTES_AS_INT.get(array, (int) offset);
        }
        return (int) INT_UNALIGNED.get(segment(base), offset);
    }

    @Override
    public void putInt(final Object base, final long offset, final int value) {
        if (base instanceof byte[] array) {
            BYTES_AS_INT.set(array, (int) offset, value);
        } else {
            INT_UNALIGNED.set(segment(base), offset, value);
        }
    }

    @Override
    public long getLong(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return (long) BYTES_AS_LONG.get(array, (int) offset);
        }
        return (long) LONG_UNALIGNED.get(segment(base), offset);
    }

    @Override
    public void putLong(final Object base, final long offset, final long value) {
        if (base instanceof byte[] array) {
            BYTES_AS_LONG.set(array, (int) offset, value);
        } else {
            LONG_UNALIGNED.set(segment(base), offset, value);
        }
    }

    @Override
    public byte getByteVolatile(final Object base, final long offset) {
        if (base instanceof byte[] array) {
            return (byte) BYTES.getVolatile(array, (int) offset);
        }
        return (byte) BYTE.getVolatile(segment(base), offset);
    }

    @Override
    public void putByteVolatile(final Object base, final long offset, final byte value) {
        if (base instanceof byte[] array) {
            BYTES.setVolatile(array, (int) offset, value);
        } else {
            BYTE.setVolatile(segment(base), offset, value);
        }
    }

    @Override
    public short getShortVolatile(final Object base, final long offset) {
        return (short) SHORT.getVolatile(segment(base), offset);
    }

    @Override
    public void putShortVolatile(final Object base, final long offset, final short value) {
        SHORT.setVolatile(segment(base), offset, value);
    }

    @Override
    public char getCharVolatile(final Object base, final long offset) {
        return (char) CHAR.getVolatile(segment(base), offset);
    }

    @Override
    public void putCharVolatile(final Object base, final long offset, final char value) {
        CHAR.setVolatile(segment(base), offset, value);
    }

    @Override
    public int getIntOpaque(final Object base, final long offset) {
        return (int) INT.getOpaque(segment(base), offset);
    }

    @Override
    public int getIntAcquire(final Object base, final long offset) {
        return (int) INT.getAcquire(segment(base), offset);
    }

    @Override
    public int getIntVolatile(final Object base, final long offset) {
        return (int) INT.getVolatile(segment(base), offset);
    }

    @Override
    public void putIntOpaque(final Object base, final long offset, final int value) {
        INT.setOpaque(segment(base), offset, value);
    }

    @Override
    public void putIntRelease(final Object base, final long offset, final int value) {
        INT.setRelease(segment(base), offset, value);
    }

    @Override
    public void putIntVolatile(final Object base, final long offset, final int value) {
        INT.setVolatile(segment(base), offset, value);
    }

    @Override
    public boolean compareAndSetInt(final Object base, final long offset, final int expected, final int update) {
        return INT.compareAndSet(segment(base), offset, expected, update);
    }

    @Override
    public int compareAndExchangeInt(final Object base, final long offset, final int expected, final int update) {
        return (int) INT.compareAndExchange(segment(base), offset, expected, update);
    }

    @Override
    public int getAndAddInt(final Object base, final long offset, final int delta) {
        return (int) INT.getAndAdd(segment(base), offset, delta);
    }

    @Override
    public int getAndSetInt(final Object base, final long offset, final int value) {
        return (int) INT.getAndSet(segment(base), offset, value);
    }

    @Override
    public long getLongOpaque(final Object base, final long offset) {
        return (long) LONG.getOpaque(segment(base), offset);
    }

    @Override
    public long getLongAcquire(final Object base, final long offset) {
        return (long) LONG.getAcquire(segment(base), offset);
    }

    @Override
    public long getLongVolatile(final Object base, final long offset) {
        return (long) LONG.getVolatile(segment(base), offset);
    }

    @Override
    public void putLongOpaque(final Object base, final long offset, final long value) {
        LONG.setOpaque(segment(base), offset, value);
    }

    @Override
    public void putLongRelease(final Object base, final long offset, final long value) {
        LONG.setRelease(segment(base), offset, value);
    }

    @Override
    public void putLongVolatile(final Object base, final long offset, final long value) {
        LONG.setVolatile(segment(base), offset, value);
    }

    @Override
    public boolean compareAndSetLong(final Object base, final long offset, final long expected, final long update) {
        return LONG.compareAndSet(segment(base), offset, expected, update);
    }

    @Override
    public long compareAndExchangeLong(final Object base, final long offset, final long expected, final long update) {
        return (long) LONG.compareAndExchange(segment(base), offset, expected, update);
    }

    @Override
    public long getAndAddLong(final Object base, final long offset, final long delta) {
        return (long) LONG.getAndAdd(segment(base), offset, delta);
    }

    @Override
    public long getAndSetLong(final Object base, final long offset, final long value) {
        return (long) LONG.getAndSet(segment(base), offset, value);
    }

    @Override
    public void copyMemory(final Object sourceBase, final long sourceOffset, final Object destinationBase,
                           final long destinationOffset, final long length) {
        if (sourceBase instanceof byte[] source) {
            if (destinationBase instanceof byte[] destination) {
                System.arraycopy(source, (int) sourceOffset, destination, (int) destinationOffset, (int) length);
            } else {
                MemorySegment.copy(source, (int) sourceOffset, segment(destinationBase), JAVA_BYTE,
                        destinationOffset, (int) length);
            }
        } else if (destinationBase instanceof byte[] destination) {
            MemorySegment.copy(segment(sourceBase), JAVA_BYTE, sourceOffset, destination, (int) destinationOffset,
                    (int) length);
        } else {
            MemorySegment.copy(segment(sourceBase), sourceOffset, segment(destinationBase), destinationOffset,
                    length);
        }
    }

    @Override
    public void setMemory(final Object base, final long offset, final long length, final byte value) {
        if (base instanceof byte[] array) {
            Arrays.fill(array, (int) offset, (int) (offset + length), value);
            return;
        }
        //no slice and fill, as a slice is a new segment
        final MemorySegment segment = segment(base);
        final long pattern = (value & 0xffL) * BYTE_PATTERN;
        long index = 0;
        for (; index <= length - Long.BYTES; index += Long.BYTES) {
            LONG_UNALIGNED.set(segment, offset + index, pattern);
        }
        for (; index < length; index++) {
            BYTE.set(segment, offset + index, value);
        }
    }
}
