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
package org.tools4j.mmap.core.buffer;

import org.tools4j.mmap.core.unsafe.Unsafe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Read access to a range of bytes in direct (off-heap) or heap memory, from index 0 to {@link #capacity()} exclusive.
 * <p>
 * <b>Byte order:</b> multi-byte values are little endian, independent of the platform and of the
 * {@link ByteBuffer#order() order} of a wrapped {@code ByteBuffer}, unless a {@link ByteOrder} is passed to the
 * accessor.
 * <p>
 * <b>Plain access</b> ({@code getLong(..)} etc.) works at any index, aligned or not.
 * <p>
 * <b>Ordered access</b> (opaque, acquire, volatile, and the atomic operations of {@link MutableMemoryBuffer}) needs
 * memory whose alignment is known: direct memory, or heap memory allocated by {@link MemoryBuffers}. The address of
 * the value must be naturally aligned, that is, a multiple of the value's size; {@link #alignment()} tells the
 * alignment of index 0. Ordered access to multi-byte values in memory backed by a {@code byte[]} throws
 * {@link UnsupportedOperationException}, as the JDK's byte array view var handles do, since the elements of a
 * {@code byte[]} have no guaranteed alignment; single bytes have no alignment and support ordered access everywhere.
 * <p>
 * All accesses check their bounds and throw {@link IndexOutOfBoundsException} for an access outside of the buffer.
 * <p>
 * Buffers are not thread-safe, except for the ordered accesses to the same memory through different buffers.
 */
public interface MemoryBuffer {

    /**
     * Returns the number of bytes in this buffer.
     *
     * @return the capacity in bytes
     */
    int capacity();

    /**
     * Returns whether this buffer's memory is direct (off-heap) memory, rather than heap memory.
     *
     * @return true for direct memory, false for heap memory
     */
    boolean isDirect();

    /**
     * Returns whether this buffer's memory is read-only: writes to it throw {@link java.nio.ReadOnlyBufferException},
     * see {@link MutableMemoryBuffer}. A buffer with capacity 0 is not read-only, since a write to it is out of bounds
     * like a write beyond the capacity of any buffer, or grows an expandable one.
     *
     * @return true for read-only memory, false for writable memory
     */
    boolean isReadOnly();

    /**
     * Returns how this buffer is bound to its memory, fixed when it was created.
     *
     * @return the binding
     */
    MemoryBinding binding();

    /**
     * Returns the guaranteed alignment of the address of index 0, the largest power of two dividing it: for direct
     * memory that of the address; for heap memory allocated by {@link MemoryBuffers} 8, or less for a flyweight
     * wrapping it at an index that is not a multiple of 8; for a wrapped {@code byte[]} or heap {@code ByteBuffer} 1,
     * since the elements of a {@code byte[]} have no guaranteed alignment.
     * <p>
     * Ordered access to a value of {@code n} bytes at {@code index} is supported if {@code n <= alignment()} and
     * {@code index % n == 0}, or if {@code n == 1}.
     *
     * @return the alignment in bytes, a power of two
     */
    long alignment();

    /**
     * Returns the address of the first byte of this buffer's direct memory, or {@link MemoryBuffers#NULL_ADDRESS} for
     * heap memory, see {@link #isDirect()}.
     *
     * @return the address, or {@link MemoryBuffers#NULL_ADDRESS} for heap memory
     */
    @Unsafe
    long address();

    /**
     * Returns the offset of index 0 within the underlying array or {@code ByteBuffer}: index {@code i} of this buffer
     * is element {@code wrapOffset() + i} of the wrapped byte array or byte buffer. A flyweight wrapping a range of
     * another buffer has the other buffer's offset plus the index of the range, so it stays relative to the underlying
     * array or byte buffer; for a buffer created with {@link MemoryBuffers#wrap(long[], int, int)}, it is the byte
     * offset from the start of its {@code long[]}, 8 bytes per element. An allocated buffer, and a flyweight wrapping a
     * raw address, have offset 0.
     *
     * @return the offset in bytes
     */
    int wrapOffset();

    /**
     * Returns the byte at the given index.
     *
     * @param index the index of the byte
     * @return the byte
     */
    byte getByte(int index);

    /**
     * Returns the short at the given index.
     *
     * @param index the index of the first byte
     * @return the short
     */
    short getShort(int index);

    /**
     * Returns the short at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the short in memory
     * @return the short
     */
    short getShort(int index, ByteOrder byteOrder);

    /**
     * Returns the char at the given index.
     *
     * @param index the index of the first byte
     * @return the char
     */
    char getChar(int index);

    /**
     * Returns the char at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the char in memory
     * @return the char
     */
    char getChar(int index, ByteOrder byteOrder);

    /**
     * Returns the int at the given index.
     *
     * @param index the index of the first byte
     * @return the int
     */
    int getInt(int index);

    /**
     * Returns the int at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the int in memory
     * @return the int
     */
    int getInt(int index, ByteOrder byteOrder);

    /**
     * Returns the long at the given index.
     *
     * @param index the index of the first byte
     * @return the long
     */
    long getLong(int index);

    /**
     * Returns the long at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the long in memory
     * @return the long
     */
    long getLong(int index, ByteOrder byteOrder);

    /**
     * Returns the float at the given index.
     *
     * @param index the index of the first byte
     * @return the float
     */
    float getFloat(int index);

    /**
     * Returns the float at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the float in memory
     * @return the float
     */
    float getFloat(int index, ByteOrder byteOrder);

    /**
     * Returns the double at the given index.
     *
     * @param index the index of the first byte
     * @return the double
     */
    double getDouble(int index);

    /**
     * Returns the double at the given index in the given byte order.
     *
     * @param index the index of the first byte
     * @param byteOrder the byte order of the double in memory
     * @return the double
     */
    double getDouble(int index, ByteOrder byteOrder);

    /**
     * Returns the int at the given index with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntOpaque(int index);

    /**
     * Returns the int at the given index in the given byte order, with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the int in memory
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntOpaque(int index, ByteOrder byteOrder);

    /**
     * Returns the int at the given index with acquire access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntAcquire(int index);

    /**
     * Returns the int at the given index in the given byte order, with acquire access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the int in memory
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntAcquire(int index, ByteOrder byteOrder);

    /**
     * Returns the int at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntVolatile(int index);

    /**
     * Returns the int at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the int in memory
     * @return the int
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the int is not naturally aligned
     */
    int getIntVolatile(int index, ByteOrder byteOrder);

    /**
     * Returns the long at the given index with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongOpaque(int index);

    /**
     * Returns the long at the given index in the given byte order, with opaque access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the long in memory
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongOpaque(int index, ByteOrder byteOrder);

    /**
     * Returns the long at the given index with acquire access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongAcquire(int index);

    /**
     * Returns the long at the given index in the given byte order, with acquire access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the long in memory
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongAcquire(int index, ByteOrder byteOrder);

    /**
     * Returns the long at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongVolatile(int index);

    /**
     * Returns the long at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the long in memory
     * @return the long
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the long is not naturally aligned
     */
    long getLongVolatile(int index, ByteOrder byteOrder);

    /**
     * Returns the byte at the given index with volatile access.
     *
     * @param index the index of the byte
     * @return the byte
     */
    byte getByteVolatile(int index);

    /**
     * Returns the short at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the short
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the short is not naturally aligned
     */
    short getShortVolatile(int index);

    /**
     * Returns the short at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the short in memory
     * @return the short
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the short is not naturally aligned
     */
    short getShortVolatile(int index, ByteOrder byteOrder);

    /**
     * Returns the char at the given index with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @return the char
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the char is not naturally aligned
     */
    char getCharVolatile(int index);

    /**
     * Returns the char at the given index in the given byte order, with volatile access.
     *
     * @param index the index of the first byte, naturally aligned
     * @param byteOrder the byte order of the char in memory
     * @return the char
     * @throws UnsupportedOperationException if this buffer's memory is backed by a {@code byte[]}
     * @throws IllegalStateException if the address of the char is not naturally aligned
     */
    char getCharVolatile(int index, ByteOrder byteOrder);

    /**
     * Copies bytes from this buffer to a whole array, as many as the array is long.
     *
     * @param index the index of the first byte to copy in this buffer
     * @param destination the array to copy to
     */
    void getBytes(int index, byte[] destination);

    /**
     * Copies bytes from this buffer to an array.
     *
     * @param index the index of the first byte to copy in this buffer
     * @param destination the array to copy to
     * @param destinationOffset the offset of the first byte in the array
     * @param length the number of bytes to copy
     */
    void getBytes(int index, byte[] destination, int destinationOffset, int length);

    /**
     * Copies bytes from this buffer to another buffer.
     *
     * @param index the index of the first byte to copy in this buffer
     * @param destination the buffer to copy to
     * @param destinationIndex the index of the first byte in the destination buffer
     * @param length the number of bytes to copy
     */
    void getBytes(int index, MutableMemoryBuffer destination, int destinationIndex, int length);

    /**
     * Copies bytes from this buffer to a {@code ByteBuffer} at an absolute offset, leaving its position unchanged.
     *
     * @param index the index of the first byte to copy in this buffer
     * @param destination the byte buffer to copy to, direct or heap
     * @param destinationOffset the absolute offset of the first byte in the byte buffer
     * @param length the number of bytes to copy
     * @throws java.nio.ReadOnlyBufferException if the byte buffer is read-only
     */
    void getBytes(int index, ByteBuffer destination, int destinationOffset, int length);

    /**
     * Copies bytes from this buffer to a {@code ByteBuffer} at its position, and advances its position by the number
     * of bytes copied.
     *
     * @param index the index of the first byte to copy in this buffer
     * @param destination the byte buffer to copy to, direct or heap
     * @param length the number of bytes to copy, at most the byte buffer's remaining bytes
     * @throws java.nio.ReadOnlyBufferException if the byte buffer is read-only
     */
    void getBytes(int index, ByteBuffer destination, int length);

    /**
     * Appends ASCII text from this buffer to an appendable, one character per byte, without allocating unless the
     * appendable does; there is no length prefix, the caller knows the length.
     *
     * @param index the index of the first byte
     * @param length the number of bytes, one per character
     * @param destination the appendable to append to
     * @return the number of bytes read, the length
     * @throws java.io.UncheckedIOException if the appendable throws an {@link java.io.IOException}
     */
    int getTextAscii(int index, int length, Appendable destination);

    /**
     * Appends ASCII text from this buffer to a string builder, one character per byte, without allocating unless the
     * string builder grows; there is no length prefix, the caller knows the length.
     *
     * @param index the index of the first byte
     * @param length the number of bytes, one per character
     * @param destination the string builder to append to
     * @return the number of bytes read, the length
     */
    int getTextAscii(int index, int length, StringBuilder destination);

    /**
     * Returns ASCII text from this buffer as a new string, one character per byte; allocates the string.
     *
     * @param index the index of the first byte
     * @param length the number of bytes, one per character
     * @return the string
     */
    String getTextAscii(int index, int length);

    /**
     * Decodes UTF-8 text from this buffer and appends it to an appendable, without allocating unless the appendable
     * does; there is no length prefix, the caller knows the length in bytes.
     * <p>
     * Decoding can be resumed: if the range ends within the encoding of a character, those up to 3 bytes are not
     * read, and the result is less than the length, so that text split into chunks can be decoded chunk by chunk,
     * continuing at {@code index} plus the result. Other malformed input is decoded as the replacement character
     * {@code U+FFFD}, as the JDK does.
     *
     * @param index the index of the first byte
     * @param length the number of bytes to decode at most
     * @param destination the appendable to append to
     * @return the number of bytes read, the length or up to 3 less
     * @throws java.io.UncheckedIOException if the appendable throws an {@link java.io.IOException}
     */
    int getTextUtf8(int index, int length, Appendable destination);

    /**
     * Decodes UTF-8 text from this buffer and appends it to a string builder, without allocating unless the string
     * builder grows; there is no length prefix, the caller knows the length in bytes.
     * <p>
     * Decoding can be resumed: if the range ends within the encoding of a character, those up to 3 bytes are not
     * read, and the result is less than the length, so that text split into chunks can be decoded chunk by chunk,
     * continuing at {@code index} plus the result. Other malformed input is decoded as the replacement character
     * {@code U+FFFD}, as the JDK does.
     *
     * @param index the index of the first byte
     * @param length the number of bytes to decode at most
     * @param destination the string builder to append to
     * @return the number of bytes read, the length or up to 3 less
     */
    int getTextUtf8(int index, int length, StringBuilder destination);

    /**
     * Decodes UTF-8 text from this buffer into a new string; allocates the string. All bytes are decoded, malformed
     * input, including a character cut off by the end of the range, as the replacement character {@code U+FFFD}, as
     * the JDK does.
     *
     * @param index the index of the first byte
     * @param length the number of bytes to decode
     * @return the string
     */
    String getTextUtf8(int index, int length);
}
