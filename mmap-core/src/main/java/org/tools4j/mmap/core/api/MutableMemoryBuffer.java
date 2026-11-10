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
package org.tools4j.mmap.core.api;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Read and write access to a range of bytes in direct or heap memory, with the byte order, alignment and bounds rules
 * described in {@link MemoryBuffer}; ordered writes and atomic operations follow the rules of ordered access.
 * <p>
 * Expandable buffers ({@link MemoryBuffers#expandable(int)}, {@link MemoryBuffers#expandableDirect(int)}) grow on
 * writes beyond their capacity instead of throwing; reads beyond the capacity throw as for any buffer.
 */
public interface MutableMemoryBuffer extends MemoryBuffer {

    /**
     * Writes a byte at the given index.
     *
     * @param index the index of the byte
     * @param value the byte to write
     */
    void putByte(int index, byte value);

    /**
     * Writes a short at the given index.
     *
     * @param index the index of the first byte
     * @param value the short to write
     */
    void putShort(int index, short value);

    /**
     * Writes a short at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the short to write
     * @param byteOrder the byte order of the short in memory
     */
    void putShort(int index, short value, ByteOrder byteOrder);

    /**
     * Writes a char at the given index.
     *
     * @param index the index of the first byte
     * @param value the char to write
     */
    void putChar(int index, char value);

    /**
     * Writes a char at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the char to write
     * @param byteOrder the byte order of the char in memory
     */
    void putChar(int index, char value, ByteOrder byteOrder);

    /**
     * Writes an int at the given index.
     *
     * @param index the index of the first byte
     * @param value the int to write
     */
    void putInt(int index, int value);

    /**
     * Writes an int at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the int to write
     * @param byteOrder the byte order of the int in memory
     */
    void putInt(int index, int value, ByteOrder byteOrder);

    /**
     * Writes a long at the given index.
     *
     * @param index the index of the first byte
     * @param value the long to write
     */
    void putLong(int index, long value);

    /**
     * Writes a long at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the long to write
     * @param byteOrder the byte order of the long in memory
     */
    void putLong(int index, long value, ByteOrder byteOrder);

    /**
     * Writes a float at the given index.
     *
     * @param index the index of the first byte
     * @param value the float to write
     */
    void putFloat(int index, float value);

    /**
     * Writes a float at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the float to write
     * @param byteOrder the byte order of the float in memory
     */
    void putFloat(int index, float value, ByteOrder byteOrder);

    /**
     * Writes a double at the given index.
     *
     * @param index the index of the first byte
     * @param value the double to write
     */
    void putDouble(int index, double value);

    /**
     * Writes a double at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param value the double to write
     * @param byteOrder the byte order of the double in memory
     */
    void putDouble(int index, double value, ByteOrder byteOrder);

    /**
     * Writes an int at the given index with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntOpaque(int index, int value);

    /**
     * Writes an int at the given index in the given byte order, with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @param byteOrder the byte order of the int in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntOpaque(int index, int value, ByteOrder byteOrder);

    /**
     * Writes an int at the given index with release access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntRelease(int index, int value);

    /**
     * Writes an int at the given index in the given byte order, with release access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @param byteOrder the byte order of the int in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntRelease(int index, int value, ByteOrder byteOrder);

    /**
     * Writes an int at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntVolatile(int index, int value);

    /**
     * Writes an int at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @param byteOrder the byte order of the int in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    void putIntVolatile(int index, int value, ByteOrder byteOrder);

    /**
     * Atomically sets the int at the given index to the update value if it equals the expected value, with volatile
     * access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @return true if the value was updated, false if another value was found
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    boolean compareAndSetInt(int index, int expectedValue, int updateValue);

    /**
     * Atomically sets the int at the given index in the given byte order to the update value if it equals the
     * expected value, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @param byteOrder the byte order of the int in memory
     * @return true if the value was updated, false if another value was found
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    boolean compareAndSetInt(int index, int expectedValue, int updateValue, ByteOrder byteOrder);

    /**
     * Atomically sets the int at the given index to the update value if it equals the expected value, with volatile
     * access, and returns the value found.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @return the value found, equal to the expected value if the value was updated
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int compareAndExchangeInt(int index, int expectedValue, int updateValue);

    /**
     * Atomically sets the int at the given index in the given byte order to the update value if it equals the
     * expected value, with volatile access, and returns the value found.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @param byteOrder the byte order of the int in memory
     * @return the value found, equal to the expected value if the value was updated
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int compareAndExchangeInt(int index, int expectedValue, int updateValue, ByteOrder byteOrder);

    /**
     * Atomically adds a delta to the int at the given index, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param delta the value to add
     * @return the value before adding
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getAndAddInt(int index, int delta);

    /**
     * Atomically adds a delta to the int at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param delta the value to add
     * @param byteOrder the byte order of the int in memory
     * @return the value before adding
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getAndAddInt(int index, int delta, ByteOrder byteOrder);

    /**
     * Atomically writes an int at the given index, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @return the value before writing
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getAndSetInt(int index, int value);

    /**
     * Atomically writes an int at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the int to write
     * @param byteOrder the byte order of the int in memory
     * @return the value before writing
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getAndSetInt(int index, int value, ByteOrder byteOrder);

    /**
     * Writes a long at the given index with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongOpaque(int index, long value);

    /**
     * Writes a long at the given index in the given byte order, with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @param byteOrder the byte order of the long in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongOpaque(int index, long value, ByteOrder byteOrder);

    /**
     * Writes a long at the given index with release access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongRelease(int index, long value);

    /**
     * Writes a long at the given index in the given byte order, with release access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @param byteOrder the byte order of the long in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongRelease(int index, long value, ByteOrder byteOrder);

    /**
     * Writes a long at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongVolatile(int index, long value);

    /**
     * Writes a long at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @param byteOrder the byte order of the long in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    void putLongVolatile(int index, long value, ByteOrder byteOrder);

    /**
     * Atomically sets the long at the given index to the update value if it equals the expected value, with volatile
     * access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @return true if the value was updated, false if another value was found
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    boolean compareAndSetLong(int index, long expectedValue, long updateValue);

    /**
     * Atomically sets the long at the given index in the given byte order to the update value if it equals the
     * expected value, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @param byteOrder the byte order of the long in memory
     * @return true if the value was updated, false if another value was found
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    boolean compareAndSetLong(int index, long expectedValue, long updateValue, ByteOrder byteOrder);

    /**
     * Atomically sets the long at the given index to the update value if it equals the expected value, with volatile
     * access, and returns the value found.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @return the value found, equal to the expected value if the value was updated
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long compareAndExchangeLong(int index, long expectedValue, long updateValue);

    /**
     * Atomically sets the long at the given index in the given byte order to the update value if it equals the
     * expected value, with volatile access, and returns the value found.
     *
     * @param index the index of the first byte, naturally aligned
     * @param expectedValue the value expected at the index
     * @param updateValue the value to write if the expected value was found
     * @param byteOrder the byte order of the long in memory
     * @return the value found, equal to the expected value if the value was updated
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long compareAndExchangeLong(int index, long expectedValue, long updateValue, ByteOrder byteOrder);

    /**
     * Atomically adds a delta to the long at the given index, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param delta the value to add
     * @return the value before adding
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getAndAddLong(int index, long delta);

    /**
     * Atomically adds a delta to the long at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param delta the value to add
     * @param byteOrder the byte order of the long in memory
     * @return the value before adding
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getAndAddLong(int index, long delta, ByteOrder byteOrder);

    /**
     * Atomically writes a long at the given index, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @return the value before writing
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getAndSetLong(int index, long value);

    /**
     * Atomically writes a long at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the long to write
     * @param byteOrder the byte order of the long in memory
     * @return the value before writing
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getAndSetLong(int index, long value, ByteOrder byteOrder);

    /**
     * Writes a byte at the given index with volatile access.
     *
     * @param index the index of the byte
     * @param value the byte to write
     */
    void putByteVolatile(int index, byte value);

    /**
     * Writes a short at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the short to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the short is not naturally aligned
     */
    void putShortVolatile(int index, short value);

    /**
     * Writes a short at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the short to write
     * @param byteOrder the byte order of the short in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the short is not naturally aligned
     */
    void putShortVolatile(int index, short value, ByteOrder byteOrder);

    /**
     * Writes a char at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the char to write
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the char is not naturally aligned
     */
    void putCharVolatile(int index, char value);

    /**
     * Writes a char at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param value the char to write
     * @param byteOrder the byte order of the char in memory
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the char is not naturally aligned
     */
    void putCharVolatile(int index, char value, ByteOrder byteOrder);

    /**
     * Copies a whole array to this buffer.
     *
     * @param index the index of the first byte to write in this buffer
     * @param source the array to copy
     */
    void putBytes(int index, byte[] source);

    /**
     * Copies bytes from an array to this buffer.
     *
     * @param index the index of the first byte to write in this buffer
     * @param source the array to copy from
     * @param sourceOffset the offset of the first byte in the array
     * @param length the number of bytes to copy
     */
    void putBytes(int index, byte[] source, int sourceOffset, int length);

    /**
     * Copies bytes from another buffer, or from another range of this buffer, to this buffer.
     *
     * @param index the index of the first byte to write in this buffer
     * @param source the buffer to copy from
     * @param sourceIndex the index of the first byte in the source buffer
     * @param length the number of bytes to copy
     */
    void putBytes(int index, MemoryBuffer source, int sourceIndex, int length);

    /**
     * Copies bytes from a {@code ByteBuffer} at an absolute offset to this buffer, leaving its position unchanged.
     *
     * @param index the index of the first byte to write in this buffer
     * @param source the byte buffer to copy from, direct or heap, possibly read-only
     * @param sourceOffset the absolute offset of the first byte in the byte buffer
     * @param length the number of bytes to copy
     */
    void putBytes(int index, ByteBuffer source, int sourceOffset, int length);

    /**
     * Copies bytes from a {@code ByteBuffer} at its position to this buffer, and advances its position by the number
     * of bytes copied.
     *
     * @param index the index of the first byte to write in this buffer
     * @param source the byte buffer to copy from, direct or heap, possibly read-only
     * @param length the number of bytes to copy, at most the byte buffer's remaining bytes
     */
    void putBytes(int index, ByteBuffer source, int length);

    /**
     * Sets a range of bytes to a value.
     *
     * @param index the index of the first byte to set
     * @param length the number of bytes to set
     * @param value the value to set the bytes to
     */
    void setMemory(int index, int length, byte value);

    /**
     * Writes characters as ASCII to this buffer, one byte per character, without a length prefix; characters
     * outside of ASCII are written as {@code '?'}.
     *
     * @param index the index of the first byte to write
     * @param value the characters to write
     * @return the number of bytes written, the length of the characters
     */
    int putAscii(int index, CharSequence value);

    /**
     * Writes a range of characters as ASCII to this buffer, one byte per character, without a length prefix;
     * characters outside of ASCII are written as {@code '?'}.
     *
     * @param index the index of the first byte to write
     * @param value the characters to write from
     * @param valueOffset the index of the first character to write
     * @param length the number of characters to write
     * @return the number of bytes written, the length
     */
    int putAscii(int index, CharSequence value, int valueOffset, int length);

    /**
     * Encodes characters as UTF-8 to this buffer, without a length prefix; unpaired surrogates are encoded as
     * {@code '?'}, as the JDK does. {@link Utf8#length(CharSequence)} tells the number of bytes beforehand.
     *
     * @param index the index of the first byte to write
     * @param value the characters to encode
     * @return the number of bytes written
     */
    int putUtf8(int index, CharSequence value);

    /**
     * Encodes a range of characters as UTF-8 to this buffer, without a length prefix; unpaired surrogates are
     * encoded as {@code '?'}, as the JDK does. {@link Utf8#length(CharSequence, int, int)} tells the number of bytes
     * beforehand.
     *
     * @param index the index of the first byte to write
     * @param value the characters to encode from
     * @param valueOffset the index of the first character to encode
     * @param length the number of characters to encode
     * @return the number of bytes written
     */
    int putUtf8(int index, CharSequence value, int valueOffset, int length);
}
