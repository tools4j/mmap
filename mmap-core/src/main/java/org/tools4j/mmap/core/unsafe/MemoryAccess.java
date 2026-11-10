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

import java.nio.ByteBuffer;

/**
 * Raw memory access to direct and heap memory behind the memory buffers, one implementation per access mechanism,
 * chosen once per JVM.
 * <p>
 * Memory is addressed by a base and an offset: for direct memory, a null base and the address as offset; for heap
 * memory, the base and offset as returned by {@link #heapBase(byte[])} and {@link #heapOffset(byte[], int)}, or their
 * {@code long[]} counterparts. Callers pass a literal null for direct memory, so that each access compiles to a pure
 * off-heap or on-heap access. Values are in native byte order; plain accesses work at any address, all others need
 * naturally aligned addresses, and heap memory backed by a {@code long[]}, except for single bytes.
 * <p>
 * Nothing is checked: the callers check bounds and alignment, and misuse can crash the JVM.
 */
@Unsafe
public interface MemoryAccess {

    /**
     * Returns the heap base representing a byte array.
     *
     * @param array the array
     * @return the heap base
     */
    Object heapBase(byte[] array);

    /**
     * Returns the heap base representing a long array.
     *
     * @param array the array
     * @return the heap base
     */
    Object heapBase(long[] array);

    /**
     * Returns the offset of a byte of a byte array, to be used with its {@linkplain #heapBase(byte[]) heap base}.
     *
     * @param array      the array
     * @param byteOffset the offset of the byte from the start of the array
     * @return the offset
     */
    long heapOffset(byte[] array, int byteOffset);

    /**
     * Returns the offset of a byte of a long array, to be used with its {@linkplain #heapBase(long[]) heap base}.
     *
     * @param array      the array
     * @param byteOffset the offset of the byte in bytes from the start of the array
     * @return the offset
     */
    long heapOffset(long[] array, int byteOffset);

    /**
     * Returns the address of the first byte of a direct byte buffer.
     *
     * @param buffer the direct byte buffer
     * @return the address
     */
    long address(ByteBuffer buffer);

    /**
     * Reads a byte in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    byte getByte(Object base, long offset);

    /**
     * Writes a byte in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putByte(Object base, long offset, byte value);

    /**
     * Reads a short in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    short getShort(Object base, long offset);

    /**
     * Writes a short in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putShort(Object base, long offset, short value);

    /**
     * Reads a char in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    char getChar(Object base, long offset);

    /**
     * Writes a char in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putChar(Object base, long offset, char value);

    /**
     * Reads a int in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    int getInt(Object base, long offset);

    /**
     * Writes a int in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putInt(Object base, long offset, int value);

    /**
     * Reads a long in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    long getLong(Object base, long offset);

    /**
     * Writes a long in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putLong(Object base, long offset, long value);

    /**
     * Reads a byte in native byte order with volatile access.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    byte getByteVolatile(Object base, long offset);

    /**
     * Writes a byte in native byte order with volatile access.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putByteVolatile(Object base, long offset, byte value);

    /**
     * Reads a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    short getShortVolatile(Object base, long offset);

    /**
     * Writes a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putShortVolatile(Object base, long offset, short value);

    /**
     * Reads a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    char getCharVolatile(Object base, long offset);

    /**
     * Writes a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putCharVolatile(Object base, long offset, char value);

    /**
     * Reads an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    int getIntOpaque(Object base, long offset);

    /**
     * Reads an int in native byte order with acquire access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    int getIntAcquire(Object base, long offset);

    /**
     * Reads an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    int getIntVolatile(Object base, long offset);

    /**
     * Writes an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putIntOpaque(Object base, long offset, int value);

    /**
     * Writes an int in native byte order with release access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putIntRelease(Object base, long offset, int value);

    /**
     * Writes an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putIntVolatile(Object base, long offset, int value);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetInt(Object base, long offset, int expected, int update);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    int compareAndExchangeInt(Object base, long offset, int expected, int update);

    /**
     * Atomically adds to an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param delta  the value to add
     * @return the value before adding
     */
    int getAndAddInt(Object base, long offset, int delta);

    /**
     * Atomically writes an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     * @return the value before writing
     */
    int getAndSetInt(Object base, long offset, int value);

    /**
     * Reads a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    long getLongOpaque(Object base, long offset);

    /**
     * Reads a long in native byte order with acquire access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    long getLongAcquire(Object base, long offset);

    /**
     * Reads a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @return the value
     */
    long getLongVolatile(Object base, long offset);

    /**
     * Writes a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putLongOpaque(Object base, long offset, long value);

    /**
     * Writes a long in native byte order with release access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putLongRelease(Object base, long offset, long value);

    /**
     * Writes a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     */
    void putLongVolatile(Object base, long offset, long value);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetLong(Object base, long offset, long expected, long update);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    long compareAndExchangeLong(Object base, long offset, long expected, long update);

    /**
     * Atomically adds to a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param delta  the value to add
     * @return the value before adding
     */
    long getAndAddLong(Object base, long offset, long delta);

    /**
     * Atomically writes a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param value  the value
     * @return the value before writing
     */
    long getAndSetLong(Object base, long offset, long value);

    /**
     * Copies bytes, also between direct and heap memory, and also between overlapping ranges.
     *
     * @param sourceBase        the heap base of the source, or null for direct memory
     * @param sourceOffset      the offset of the source, or its address for direct memory
     * @param destinationBase   the heap base of the destination, or null for direct memory
     * @param destinationOffset the offset of the destination, or its address for direct memory
     * @param length            the number of bytes
     */
    void copyMemory(Object sourceBase, long sourceOffset, Object destinationBase, long destinationOffset, long length);

    /**
     * Sets bytes to a value.
     *
     * @param base   the heap base as returned by {@link #heapBase(byte[])} or {@link #heapBase(long[])}, or null for
     *               direct memory
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or {@link #heapOffset(long[], int)},
     *               or the address for direct memory
     * @param length the number of bytes
     * @param value  the value
     */
    void setMemory(Object base, long offset, long length, byte value);
}
