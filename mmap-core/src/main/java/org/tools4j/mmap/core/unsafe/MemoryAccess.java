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
 * Direct memory is addressed by its address, heap memory by a base and an offset as returned by
 * {@link #heapBase(byte[])} and {@link #heapOffset(byte[], int)}, or their {@code long[]} and {@code ByteBuffer}
 * counterparts. Each access has an overload per kind of memory: one taking the address, one taking a {@code byte[]}
 * base, for the accesses supporting it, and one taking any other heap base. So each compiles to a pure off-heap or
 * on-heap access of a known type without type check, small enough for the JIT compiler to inline it also at a branch
 * of the caller that is rarely taken: a call left in a loop keeps the loop from being unrolled and vectorised.
 * Copying and setting memory take a null base for direct memory instead.
 * <p>
 * Values are in native byte order; plain accesses work at any address, all others need naturally aligned addresses,
 * and heap memory backed by a {@code long[]}, except for single bytes.
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
     * Returns the heap base of a heap byte buffer, writable or read-only, i.e. also without accessible array.
     *
     * @param buffer the heap byte buffer
     * @return the heap base, to be used with {@link #heapOffset(ByteBuffer, int)}
     * @throws IllegalArgumentException if the byte buffer's memory cannot be accessed, such as that of a
     *                                  {@code MemorySegment} of an array other than a {@code byte[]}
     */
    Object heapBase(ByteBuffer buffer);

    /**
     * Returns the offset of a byte of a heap byte buffer, to be used with its
     * {@linkplain #heapBase(ByteBuffer) heap base}.
     *
     * @param buffer the heap byte buffer
     * @param byteOffset the absolute offset of the byte in the byte buffer
     * @return the offset
     */
    long heapOffset(ByteBuffer buffer, int byteOffset);

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
     * @param address the address
     * @return the value
     */
    byte getByte(long address);

    /**
     * Reads a byte in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    byte getByte(byte[] base, long offset);

    /**
     * Reads a byte in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    byte getByte(Object base, long offset);

    /**
     * Writes a byte in native byte order with plain access, at any address.
     *
     * @param address the address
     * @param value   the value
     */
    void putByte(long address, byte value);

    /**
     * Writes a byte in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putByte(byte[] base, long offset, byte value);

    /**
     * Writes a byte in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putByte(Object base, long offset, byte value);

    /**
     * Reads a short in native byte order with plain access, at any address.
     *
     * @param address the address
     * @return the value
     */
    short getShort(long address);

    /**
     * Reads a short in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    short getShort(byte[] base, long offset);

    /**
     * Reads a short in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    short getShort(Object base, long offset);

    /**
     * Writes a short in native byte order with plain access, at any address.
     *
     * @param address the address
     * @param value   the value
     */
    void putShort(long address, short value);

    /**
     * Writes a short in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putShort(byte[] base, long offset, short value);

    /**
     * Writes a short in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putShort(Object base, long offset, short value);

    /**
     * Reads a char in native byte order with plain access, at any address.
     *
     * @param address the address
     * @return the value
     */
    char getChar(long address);

    /**
     * Reads a char in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    char getChar(byte[] base, long offset);

    /**
     * Reads a char in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    char getChar(Object base, long offset);

    /**
     * Writes a char in native byte order with plain access, at any address.
     *
     * @param address the address
     * @param value   the value
     */
    void putChar(long address, char value);

    /**
     * Writes a char in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putChar(byte[] base, long offset, char value);

    /**
     * Writes a char in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putChar(Object base, long offset, char value);

    /**
     * Reads a int in native byte order with plain access, at any address.
     *
     * @param address the address
     * @return the value
     */
    int getInt(long address);

    /**
     * Reads a int in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    int getInt(byte[] base, long offset);

    /**
     * Reads a int in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    int getInt(Object base, long offset);

    /**
     * Writes a int in native byte order with plain access, at any address.
     *
     * @param address the address
     * @param value   the value
     */
    void putInt(long address, int value);

    /**
     * Writes a int in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putInt(byte[] base, long offset, int value);

    /**
     * Writes a int in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putInt(Object base, long offset, int value);

    /**
     * Reads a long in native byte order with plain access, at any address.
     *
     * @param address the address
     * @return the value
     */
    long getLong(long address);

    /**
     * Reads a long in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    long getLong(byte[] base, long offset);

    /**
     * Reads a long in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    long getLong(Object base, long offset);

    /**
     * Writes a long in native byte order with plain access, at any address.
     *
     * @param address the address
     * @param value   the value
     */
    void putLong(long address, long value);

    /**
     * Writes a long in native byte order with plain access, at any address.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putLong(byte[] base, long offset, long value);

    /**
     * Writes a long in native byte order with plain access, at any address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putLong(Object base, long offset, long value);

    /**
     * Reads a byte in native byte order with volatile access.
     *
     * @param address the address
     * @return the value
     */
    byte getByteVolatile(long address);

    /**
     * Reads a byte in native byte order with volatile access.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    byte getByteVolatile(byte[] base, long offset);

    /**
     * Reads a byte in native byte order with volatile access.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    byte getByteVolatile(Object base, long offset);

    /**
     * Writes a byte in native byte order with volatile access.
     *
     * @param address the address
     * @param value   the value
     */
    void putByteVolatile(long address, byte value);

    /**
     * Writes a byte in native byte order with volatile access.
     *
     * @param base   the byte array, as returned by {@link #heapBase(byte[])}, or by {@link #heapBase(ByteBuffer)} if
     *               a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(byte[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putByteVolatile(byte[] base, long offset, byte value);

    /**
     * Writes a byte in native byte order with volatile access.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putByteVolatile(Object base, long offset, byte value);

    /**
     * Reads a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    short getShortVolatile(long address);

    /**
     * Reads a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    short getShortVolatile(Object base, long offset);

    /**
     * Writes a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putShortVolatile(long address, short value);

    /**
     * Writes a short in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putShortVolatile(Object base, long offset, short value);

    /**
     * Reads a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    char getCharVolatile(long address);

    /**
     * Reads a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    char getCharVolatile(Object base, long offset);

    /**
     * Writes a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putCharVolatile(long address, char value);

    /**
     * Writes a char in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putCharVolatile(Object base, long offset, char value);

    /**
     * Reads an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    int getIntOpaque(long address);

    /**
     * Reads an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    int getIntOpaque(Object base, long offset);

    /**
     * Reads an int in native byte order with acquire access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    int getIntAcquire(long address);

    /**
     * Reads an int in native byte order with acquire access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    int getIntAcquire(Object base, long offset);

    /**
     * Reads an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    int getIntVolatile(long address);

    /**
     * Reads an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    int getIntVolatile(Object base, long offset);

    /**
     * Writes an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putIntOpaque(long address, int value);

    /**
     * Writes an int in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putIntOpaque(Object base, long offset, int value);

    /**
     * Writes an int in native byte order with release access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putIntRelease(long address, int value);

    /**
     * Writes an int in native byte order with release access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putIntRelease(Object base, long offset, int value);

    /**
     * Writes an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putIntVolatile(long address, int value);

    /**
     * Writes an int in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putIntVolatile(Object base, long offset, int value);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param address  the address
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetInt(long address, int expected, int update);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param base     the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *                 not a {@code byte[]}
     * @param offset   the offset as returned by {@link #heapOffset(long[], int)} or
     *                 {@link #heapOffset(ByteBuffer, int)}
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetInt(Object base, long offset, int expected, int update);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param address  the address
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    int compareAndExchangeInt(long address, int expected, int update);

    /**
     * Atomically sets an int in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param base     the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *                 not a {@code byte[]}
     * @param offset   the offset as returned by {@link #heapOffset(long[], int)} or
     *                 {@link #heapOffset(ByteBuffer, int)}
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    int compareAndExchangeInt(Object base, long offset, int expected, int update);

    /**
     * Atomically adds to an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param delta   the value to add
     * @return the value before adding
     */
    int getAndAddInt(long address, int delta);

    /**
     * Atomically adds to an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param delta  the value to add
     * @return the value before adding
     */
    int getAndAddInt(Object base, long offset, int delta);

    /**
     * Atomically writes an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     * @return the value before writing
     */
    int getAndSetInt(long address, int value);

    /**
     * Atomically writes an int in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     * @return the value before writing
     */
    int getAndSetInt(Object base, long offset, int value);

    /**
     * Reads a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    long getLongOpaque(long address);

    /**
     * Reads a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    long getLongOpaque(Object base, long offset);

    /**
     * Reads a long in native byte order with acquire access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    long getLongAcquire(long address);

    /**
     * Reads a long in native byte order with acquire access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    long getLongAcquire(Object base, long offset);

    /**
     * Reads a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @return the value
     */
    long getLongVolatile(long address);

    /**
     * Reads a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @return the value
     */
    long getLongVolatile(Object base, long offset);

    /**
     * Writes a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putLongOpaque(long address, long value);

    /**
     * Writes a long in native byte order with opaque access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putLongOpaque(Object base, long offset, long value);

    /**
     * Writes a long in native byte order with release access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putLongRelease(long address, long value);

    /**
     * Writes a long in native byte order with release access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putLongRelease(Object base, long offset, long value);

    /**
     * Writes a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     */
    void putLongVolatile(long address, long value);

    /**
     * Writes a long in native byte order with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param value  the value
     */
    void putLongVolatile(Object base, long offset, long value);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param address  the address
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetLong(long address, long expected, long update);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address.
     *
     * @param base     the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *                 not a {@code byte[]}
     * @param offset   the offset as returned by {@link #heapOffset(long[], int)} or
     *                 {@link #heapOffset(ByteBuffer, int)}
     * @param expected the expected value
     * @param update   the new value
     * @return true if the value was updated
     */
    boolean compareAndSetLong(Object base, long offset, long expected, long update);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param address  the address
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    long compareAndExchangeLong(long address, long expected, long update);

    /**
     * Atomically sets a long in native byte order if it is the expected value, with volatile access, at a naturally
     * aligned address, returning the value found.
     *
     * @param base     the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *                 not a {@code byte[]}
     * @param offset   the offset as returned by {@link #heapOffset(long[], int)} or
     *                 {@link #heapOffset(ByteBuffer, int)}
     * @param expected the expected value
     * @param update   the new value
     * @return the value found
     */
    long compareAndExchangeLong(Object base, long offset, long expected, long update);

    /**
     * Atomically adds to a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param delta   the value to add
     * @return the value before adding
     */
    long getAndAddLong(long address, long delta);

    /**
     * Atomically adds to a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
     * @param delta  the value to add
     * @return the value before adding
     */
    long getAndAddLong(Object base, long offset, long delta);

    /**
     * Atomically writes a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param address the address
     * @param value   the value
     * @return the value before writing
     */
    long getAndSetLong(long address, long value);

    /**
     * Atomically writes a long in native byte order, with volatile access, at a naturally aligned address.
     *
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if
     *               not a {@code byte[]}
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or
     *               {@link #heapOffset(ByteBuffer, int)}
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
     * @param base   the heap base as returned by {@link #heapBase(long[])}, or by {@link #heapBase(ByteBuffer)} if not
     *               a {@code byte[]}, or null for direct memory
     * @param offset the offset as returned by {@link #heapOffset(long[], int)} or {@link #heapOffset(ByteBuffer, int)},
     *               or the address for direct memory
     * @param length the number of bytes
     * @param value  the value
     */
    void setMemory(Object base, long offset, long length, byte value);
}
