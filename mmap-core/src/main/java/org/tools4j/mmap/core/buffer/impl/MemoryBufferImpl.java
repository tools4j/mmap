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

import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.buffer.MutableMemoryBufferFlyweight;
import org.tools4j.mmap.core.text.Utf8;
import org.tools4j.mmap.core.unsafe.MemoryAccess;
import org.tools4j.mmap.core.unsafe.UnsafeMemoryAccess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * The one implementation of all memory buffer interfaces, so that call sites stay monomorphic whatever memory they
 * access.
 * <p>
 * Memory is a heap base and an offset for the {@link MemoryAccess} chosen once per JVM: a null base and the address
 * for direct memory, else the base and offset of a {@code byte[]} or {@code long[]}. Every access branches on the
 * base, so that each branch compiles to a pure off-heap or on-heap access; the branch is moved out of loops.
 * Multi-byte values are little endian: on big-endian platforms they are reversed, which constant-folds away on
 * little-endian ones.
 * <p>
 * Bounds are checked with the intrinsic {@link Objects#checkIndex(int, int)}, which C2 eliminates in loops.
 * Expandable buffers grow before the bounds check of a write, so that the check stays the same for all buffers.
 */
public final class MemoryBufferImpl implements MemoryBufferFlyweight, MutableMemoryBufferFlyweight {

    private static final MemoryAccess ACCESS = memoryAccess();
    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final byte[] NO_BYTES = {};
    private static final int DIRECT_ALIGNMENT = 64;
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - DIRECT_ALIGNMENT;
    private static final int MIN_EXPANDED_CAPACITY = 64;
    private static final char REPLACEMENT_CHARACTER = '�';

    private final boolean mutableFlyweight;
    private final boolean expandable;
    private final boolean expandableDirect;

    private Object heapBase;
    private long offset;
    private int capacity;
    private int wrapOffset;
    private Object owner;
    private boolean orderedAccess;
    private long alignmentBase;

    private MemoryBufferImpl(final boolean mutableFlyweight, final boolean expandable, final boolean expandableDirect) {
        this.mutableFlyweight = mutableFlyweight;
        this.expandable = expandable;
        this.expandableDirect = expandableDirect;
        wrapHeap(NO_BYTES, 0, 0, 0, NO_BYTES);
    }

    private static MemoryAccess memoryAccess() {
        final String name = System.getProperty(MemoryBuffers.MEMORY_ACCESS_PROPERTY, "UNSAFE");
        switch (name) {
            case "UNSAFE":
                return UnsafeMemoryAccess.INSTANCE;
            case "FFM":
                throw new IllegalStateException("Memory access through FFM is not available yet");
            default:
                throw new IllegalArgumentException("Invalid value for system property " +
                        MemoryBuffers.MEMORY_ACCESS_PROPERTY + ": " + name + ", must be UNSAFE or FFM");
        }
    }

    //--- factories, used by MemoryBuffers ---

    /**
     * Returns a new read-only flyweight wrapping no memory.
     *
     * @return the flyweight
     */
    public static MemoryBufferFlyweight flyweight() {
        return new MemoryBufferImpl(false, false, false);
    }

    /**
     * Returns a new mutable flyweight wrapping no memory.
     *
     * @return the flyweight
     */
    public static MutableMemoryBufferFlyweight mutableFlyweight() {
        return new MemoryBufferImpl(true, false, false);
    }

    /**
     * Allocates a heap buffer backed by a {@code long[]}.
     *
     * @param capacity the capacity in bytes
     * @return the buffer
     */
    public static MutableMemoryBuffer allocate(final int capacity) {
        validateCapacity(capacity);
        final MemoryBufferImpl buffer = new MemoryBufferImpl(true, false, false);
        buffer.wrapHeap(new long[longsFor(capacity)], 0, capacity);
        return buffer;
    }

    /**
     * Returns a heap buffer over a range of elements of a {@code long[]}.
     *
     * @param array  the array
     * @param offset the index of the first element
     * @param length the number of elements
     * @return the buffer
     */
    public static MutableMemoryBuffer wrap(final long[] array, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, array.length);
        if (length > Integer.MAX_VALUE / Long.BYTES) {
            throw new IllegalArgumentException("Too many elements for a buffer: " + length);
        }
        final MemoryBufferImpl buffer = new MemoryBufferImpl(true, false, false);
        buffer.wrapHeap(array, offset * Long.BYTES, length * Long.BYTES);
        return buffer;
    }

    /**
     * Allocates a direct buffer aligned to a cache line.
     *
     * @param capacity the capacity in bytes
     * @return the buffer
     */
    public static MutableMemoryBuffer allocateDirect(final int capacity) {
        validateCapacity(capacity);
        final MemoryBufferImpl buffer = new MemoryBufferImpl(true, false, false);
        buffer.wrapAllocatedDirect(capacity);
        return buffer;
    }

    /**
     * Allocates an expandable heap buffer backed by a {@code long[]}.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer
     */
    public static MutableMemoryBuffer expandable(final int initialCapacity) {
        validateCapacity(initialCapacity);
        final MemoryBufferImpl buffer = new MemoryBufferImpl(true, true, false);
        buffer.wrapHeap(new long[longsFor(initialCapacity)], 0, initialCapacity);
        return buffer;
    }

    /**
     * Allocates an expandable direct buffer.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer
     */
    public static MutableMemoryBuffer expandableDirect(final int initialCapacity) {
        validateCapacity(initialCapacity);
        final MemoryBufferImpl buffer = new MemoryBufferImpl(true, true, true);
        buffer.wrapAllocatedDirect(initialCapacity);
        return buffer;
    }

    private static void validateCapacity(final int capacity) {
        if (capacity < 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("Invalid capacity " + capacity + ", must be between 0 and " +
                    MAX_CAPACITY);
        }
    }

    private static int longsFor(final int bytes) {
        return (int) (((long) bytes + Long.BYTES - 1) / Long.BYTES);
    }

    //--- wrapping ---

    private void wrapHeap(final byte[] array, final int byteOffset, final int length, final int wrapOffset,
                          final Object owner) {
        this.heapBase = ACCESS.heapBase(array);
        this.offset = ACCESS.heapOffset(array, byteOffset);
        this.capacity = length;
        this.wrapOffset = wrapOffset;
        this.owner = owner;
        this.orderedAccess = false;
        this.alignmentBase = 0;
    }

    private void wrapHeap(final long[] array, final int byteOffset, final int length) {
        this.heapBase = array == owner ? heapBase : ACCESS.heapBase(array);
        this.offset = ACCESS.heapOffset(array, byteOffset);
        this.capacity = length;
        this.wrapOffset = byteOffset;
        this.owner = array;
        this.orderedAccess = true;
        this.alignmentBase = byteOffset;
    }

    private void wrapDirect(final long address, final int capacity, final int wrapOffset, final Object owner) {
        this.heapBase = null;
        this.offset = address;
        this.capacity = capacity;
        this.wrapOffset = wrapOffset;
        this.owner = owner;
        this.orderedAccess = true;
        this.alignmentBase = address;
    }

    private void wrapAllocatedDirect(final int capacity) {
        final ByteBuffer memory = ByteBuffer.allocateDirect(capacity + DIRECT_ALIGNMENT - 1);
        final long address = ACCESS.address(memory);
        final long aligned = (address + DIRECT_ALIGNMENT - 1) & -DIRECT_ALIGNMENT;
        wrapDirect(aligned, capacity, 0, memory);
    }

    @Override
    public void wrap(final long address, final int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        wrapDirect(address, capacity, 0, null);
    }

    @Override
    public void wrap(final byte[] array) {
        wrapHeap(array, 0, array.length, 0, array);
    }

    @Override
    public void wrap(final byte[] array, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, array.length);
        wrapHeap(array, offset, length, offset, array);
    }

    @Override
    public void wrap(final ByteBuffer buffer) {
        wrap(buffer, 0, buffer.capacity());
    }

    @Override
    public void wrap(final ByteBuffer buffer, final int offset, final int length) {
        Objects.checkFromIndexSize(offset, length, buffer.capacity());
        if (mutableFlyweight && buffer.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        if (buffer.isDirect()) {
            wrapDirect(ACCESS.address(buffer) + offset, length, offset, buffer);
        } else if (buffer.hasArray()) {
            wrapHeap(buffer.array(), buffer.arrayOffset() + offset, length, offset, buffer);
        } else {
            throw new IllegalArgumentException("Cannot wrap a heap byte buffer without accessible array: " + buffer);
        }
    }

    @Override
    public void wrap(final MemoryBuffer buffer) {
        wrapRange(buffer, 0, buffer.capacity());
    }

    @Override
    public void wrap(final MemoryBuffer buffer, final int index, final int length) {
        wrapRange(buffer, index, length);
    }

    @Override
    public void wrap(final MutableMemoryBuffer buffer) {
        wrapRange(buffer, 0, buffer.capacity());
    }

    @Override
    public void wrap(final MutableMemoryBuffer buffer, final int index, final int length) {
        wrapRange(buffer, index, length);
    }

    private void wrapRange(final MemoryBuffer buffer, final int index, final int length) {
        final MemoryBufferImpl source = (MemoryBufferImpl) buffer;
        Objects.checkFromIndexSize(index, length, source.capacity);
        this.heapBase = source.heapBase;
        this.offset = source.offset + index;
        this.capacity = length;
        this.wrapOffset = source.wrapOffset + index;
        this.owner = source.owner;
        this.orderedAccess = source.orderedAccess;
        this.alignmentBase = source.alignmentBase + index;
    }

    //--- properties ---

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean isDirect() {
        return heapBase == null;
    }

    @Override
    public boolean isExpandable() {
        return expandable;
    }

    @Override
    public long alignment() {
        if (heapBase == null) {
            return Long.lowestOneBit(alignmentBase);
        }
        if (!orderedAccess) {
            return 1;
        }
        return alignmentBase == 0 ? Long.BYTES : Math.min(Long.BYTES, Long.lowestOneBit(alignmentBase));
    }

    @Override
    public int wrapOffset() {
        return wrapOffset;
    }

    @Override
    public long address() {
        if (heapBase != null) {
            throw new IllegalStateException("Heap memory has no address");
        }
        return offset;
    }

    //--- checks ---

    private void checkBounds(final int index, final int size) {
        Objects.checkIndex(index, capacity - size + 1);
    }

    private void checkOrdered(final int index, final int size) {
        if (!orderedAccess) {
            throw unsupportedOrderedAccess();
        }
        if (((alignmentBase + index) & (size - 1)) != 0) {
            throw misalignedAccess(index, size);
        }
    }

    private static UnsupportedOperationException unsupportedOrderedAccess() {
        return new UnsupportedOperationException("Ordered access to multi-byte values is not supported for memory " +
                "backed by a byte[], whose elements have no guaranteed alignment");
    }

    private IllegalStateException misalignedAccess(final int index, final int size) {
        return new IllegalStateException("Misaligned ordered access of " + size + " bytes at index " + index +
                " with alignment " + alignment());
    }

    private void ensureCapacity(final int index, final int size) {
        if (index >= 0 && size >= 0 && index > capacity - size) {
            grow((long) index + size);
        }
    }

    private void grow(final long required) {
        if (required > MAX_CAPACITY) {
            return;//bounds check throws
        }
        final int newCapacity = (int) Math.min(MAX_CAPACITY,
                Math.max(required, Math.max(MIN_EXPANDED_CAPACITY, 2L * capacity)));
        if (expandableDirect) {
            final long oldAddress = offset;
            final int oldCapacity = capacity;
            final Object oldOwner = owner;
            wrapAllocatedDirect(newCapacity);
            ACCESS.copyMemory(null, oldAddress, null, offset, oldCapacity);
            Reference.reachabilityFence(oldOwner);
        } else {
            wrapHeap(Arrays.copyOf((long[]) owner, longsFor(newCapacity)), 0, newCapacity);
        }
    }

    //--- byte order ---

    private static short littleEndian(final short value) {
        return NATIVE_LITTLE_ENDIAN ? value : Short.reverseBytes(value);
    }

    private static char littleEndian(final char value) {
        return NATIVE_LITTLE_ENDIAN ? value : Character.reverseBytes(value);
    }

    private static int littleEndian(final int value) {
        return NATIVE_LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
    }

    private static long littleEndian(final long value) {
        return NATIVE_LITTLE_ENDIAN ? value : Long.reverseBytes(value);
    }

    private static short ordered(final short value, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? value : Short.reverseBytes(value);
    }

    private static char ordered(final char value, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? value : Character.reverseBytes(value);
    }

    private static int ordered(final int value, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? value : Integer.reverseBytes(value);
    }

    private static long ordered(final long value, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? value : Long.reverseBytes(value);
    }

    //--- plain reads ---

    @Override
    public byte getByte(final int index) {
        checkBounds(index, Byte.BYTES);
        return rawGetByte(index);
    }

    private byte rawGetByte(final int index) {
        final Object base = heapBase;
        return base == null ? ACCESS.getByte(null, offset + index) : ACCESS.getByte(base, offset + index);
    }

    @Override
    public short getShort(final int index) {
        checkBounds(index, Short.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getShort(null, offset + index) :
                ACCESS.getShort(base, offset + index));
    }

    @Override
    public short getShort(final int index, final ByteOrder byteOrder) {
        return ordered(getShort(index), byteOrder);
    }

    @Override
    public char getChar(final int index) {
        checkBounds(index, Character.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getChar(null, offset + index) :
                ACCESS.getChar(base, offset + index));
    }

    @Override
    public char getChar(final int index, final ByteOrder byteOrder) {
        return ordered(getChar(index), byteOrder);
    }

    @Override
    public int getInt(final int index) {
        checkBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getInt(null, offset + index) :
                ACCESS.getInt(base, offset + index));
    }

    @Override
    public int getInt(final int index, final ByteOrder byteOrder) {
        return ordered(getInt(index), byteOrder);
    }

    @Override
    public long getLong(final int index) {
        checkBounds(index, Long.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getLong(null, offset + index) :
                ACCESS.getLong(base, offset + index));
    }

    @Override
    public long getLong(final int index, final ByteOrder byteOrder) {
        return ordered(getLong(index), byteOrder);
    }

    @Override
    public float getFloat(final int index) {
        return Float.intBitsToFloat(getInt(index));
    }

    @Override
    public float getFloat(final int index, final ByteOrder byteOrder) {
        return Float.intBitsToFloat(getInt(index, byteOrder));
    }

    @Override
    public double getDouble(final int index) {
        return Double.longBitsToDouble(getLong(index));
    }

    @Override
    public double getDouble(final int index, final ByteOrder byteOrder) {
        return Double.longBitsToDouble(getLong(index, byteOrder));
    }

    //--- plain writes ---

    @Override
    public void putByte(final int index, final byte value) {
        if (expandable) {
            ensureCapacity(index, Byte.BYTES);
        }
        checkBounds(index, Byte.BYTES);
        rawPutByte(index, value);
    }

    private void rawPutByte(final int index, final byte value) {
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putByte(null, offset + index, value);
        } else {
            ACCESS.putByte(base, offset + index, value);
        }
    }

    @Override
    public void putShort(final int index, final short value) {
        if (expandable) {
            ensureCapacity(index, Short.BYTES);
        }
        checkBounds(index, Short.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putShort(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putShort(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putShort(final int index, final short value, final ByteOrder byteOrder) {
        putShort(index, ordered(value, byteOrder));
    }

    @Override
    public void putChar(final int index, final char value) {
        if (expandable) {
            ensureCapacity(index, Character.BYTES);
        }
        checkBounds(index, Character.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putChar(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putChar(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putChar(final int index, final char value, final ByteOrder byteOrder) {
        putChar(index, ordered(value, byteOrder));
    }

    @Override
    public void putInt(final int index, final int value) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putInt(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putInt(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putInt(final int index, final int value, final ByteOrder byteOrder) {
        putInt(index, ordered(value, byteOrder));
    }

    @Override
    public void putLong(final int index, final long value) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putLong(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putLong(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putLong(final int index, final long value, final ByteOrder byteOrder) {
        putLong(index, ordered(value, byteOrder));
    }

    @Override
    public void putFloat(final int index, final float value) {
        putInt(index, Float.floatToRawIntBits(value));
    }

    @Override
    public void putFloat(final int index, final float value, final ByteOrder byteOrder) {
        putInt(index, Float.floatToRawIntBits(value), byteOrder);
    }

    @Override
    public void putDouble(final int index, final double value) {
        putLong(index, Double.doubleToRawLongBits(value));
    }

    @Override
    public void putDouble(final int index, final double value, final ByteOrder byteOrder) {
        putLong(index, Double.doubleToRawLongBits(value), byteOrder);
    }

    //--- volatile access to small types ---

    @Override
    public byte getByteVolatile(final int index) {
        checkBounds(index, Byte.BYTES);
        final Object base = heapBase;
        return base == null ? ACCESS.getByteVolatile(null, offset + index) :
                ACCESS.getByteVolatile(base, offset + index);
    }

    @Override
    public void putByteVolatile(final int index, final byte value) {
        if (expandable) {
            ensureCapacity(index, Byte.BYTES);
        }
        checkBounds(index, Byte.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putByteVolatile(null, offset + index, value);
        } else {
            ACCESS.putByteVolatile(base, offset + index, value);
        }
    }

    @Override
    public short getShortVolatile(final int index) {
        checkBounds(index, Short.BYTES);
        checkOrdered(index, Short.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getShortVolatile(null, offset + index) :
                ACCESS.getShortVolatile(base, offset + index));
    }

    @Override
    public short getShortVolatile(final int index, final ByteOrder byteOrder) {
        return ordered(getShortVolatile(index), byteOrder);
    }

    @Override
    public void putShortVolatile(final int index, final short value) {
        if (expandable) {
            ensureCapacity(index, Short.BYTES);
        }
        checkBounds(index, Short.BYTES);
        checkOrdered(index, Short.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putShortVolatile(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putShortVolatile(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putShortVolatile(final int index, final short value, final ByteOrder byteOrder) {
        putShortVolatile(index, ordered(value, byteOrder));
    }

    @Override
    public char getCharVolatile(final int index) {
        checkBounds(index, Character.BYTES);
        checkOrdered(index, Character.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getCharVolatile(null, offset + index) :
                ACCESS.getCharVolatile(base, offset + index));
    }

    @Override
    public char getCharVolatile(final int index, final ByteOrder byteOrder) {
        return ordered(getCharVolatile(index), byteOrder);
    }

    @Override
    public void putCharVolatile(final int index, final char value) {
        if (expandable) {
            ensureCapacity(index, Character.BYTES);
        }
        checkBounds(index, Character.BYTES);
        checkOrdered(index, Character.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putCharVolatile(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putCharVolatile(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putCharVolatile(final int index, final char value, final ByteOrder byteOrder) {
        putCharVolatile(index, ordered(value, byteOrder));
    }

    //--- ordered and atomic int access ---

    @Override
    public int getIntOpaque(final int index) {
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getIntOpaque(null, offset + index) :
                ACCESS.getIntOpaque(base, offset + index));
    }

    @Override
    public int getIntOpaque(final int index, final ByteOrder byteOrder) {
        return ordered(getIntOpaque(index), byteOrder);
    }

    @Override
    public int getIntAcquire(final int index) {
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getIntAcquire(null, offset + index) :
                ACCESS.getIntAcquire(base, offset + index));
    }

    @Override
    public int getIntAcquire(final int index, final ByteOrder byteOrder) {
        return ordered(getIntAcquire(index), byteOrder);
    }

    @Override
    public int getIntVolatile(final int index) {
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getIntVolatile(null, offset + index) :
                ACCESS.getIntVolatile(base, offset + index));
    }

    @Override
    public int getIntVolatile(final int index, final ByteOrder byteOrder) {
        return ordered(getIntVolatile(index), byteOrder);
    }

    @Override
    public void putIntOpaque(final int index, final int value) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putIntOpaque(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putIntOpaque(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putIntOpaque(final int index, final int value, final ByteOrder byteOrder) {
        putIntOpaque(index, ordered(value, byteOrder));
    }

    @Override
    public void putIntRelease(final int index, final int value) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putIntRelease(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putIntRelease(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putIntRelease(final int index, final int value, final ByteOrder byteOrder) {
        putIntRelease(index, ordered(value, byteOrder));
    }

    @Override
    public void putIntVolatile(final int index, final int value) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putIntVolatile(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putIntVolatile(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putIntVolatile(final int index, final int value, final ByteOrder byteOrder) {
        putIntVolatile(index, ordered(value, byteOrder));
    }

    @Override
    public boolean compareAndSetInt(final int index, final int expectedValue, final int updateValue) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return base == null ?
                ACCESS.compareAndSetInt(null, offset + index, littleEndian(expectedValue), littleEndian(updateValue)) :
                ACCESS.compareAndSetInt(base, offset + index, littleEndian(expectedValue), littleEndian(updateValue));
    }

    @Override
    public boolean compareAndSetInt(final int index, final int expectedValue, final int updateValue,
                                    final ByteOrder byteOrder) {
        return compareAndSetInt(index, ordered(expectedValue, byteOrder), ordered(updateValue, byteOrder));
    }

    @Override
    public int compareAndExchangeInt(final int index, final int expectedValue, final int updateValue) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        final int expected = littleEndian(expectedValue);
        final int update = littleEndian(updateValue);
        return littleEndian(base == null ? ACCESS.compareAndExchangeInt(null, offset + index, expected, update) :
                ACCESS.compareAndExchangeInt(base, offset + index, expected, update));
    }

    @Override
    public int compareAndExchangeInt(final int index, final int expectedValue, final int updateValue,
                                     final ByteOrder byteOrder) {
        final int found = compareAndExchangeInt(index, ordered(expectedValue, byteOrder),
                ordered(updateValue, byteOrder));
        return ordered(found, byteOrder);
    }

    @Override
    public int getAndAddInt(final int index, final int delta) {
        if (!NATIVE_LITTLE_ENDIAN) {
            return getAndAddIntByCompareAndSet(index, delta, ByteOrder.LITTLE_ENDIAN);
        }
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return base == null ? ACCESS.getAndAddInt(null, offset + index, delta) :
                ACCESS.getAndAddInt(base, offset + index, delta);
    }

    @Override
    public int getAndAddInt(final int index, final int delta, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? getAndAddInt(index, delta) :
                getAndAddIntByCompareAndSet(index, delta, byteOrder);
    }

    private int getAndAddIntByCompareAndSet(final int index, final int delta, final ByteOrder byteOrder) {
        int value;
        do {
            value = getIntVolatile(index, byteOrder);
        } while (!compareAndSetInt(index, value, value + delta, byteOrder));
        return value;
    }

    @Override
    public int getAndSetInt(final int index, final int value) {
        if (expandable) {
            ensureCapacity(index, Integer.BYTES);
        }
        checkBounds(index, Integer.BYTES);
        checkOrdered(index, Integer.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getAndSetInt(null, offset + index, littleEndian(value)) :
                ACCESS.getAndSetInt(base, offset + index, littleEndian(value)));
    }

    @Override
    public int getAndSetInt(final int index, final int value, final ByteOrder byteOrder) {
        return ordered(getAndSetInt(index, ordered(value, byteOrder)), byteOrder);
    }

    //--- ordered and atomic long access ---

    @Override
    public long getLongOpaque(final int index) {
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getLongOpaque(null, offset + index) :
                ACCESS.getLongOpaque(base, offset + index));
    }

    @Override
    public long getLongOpaque(final int index, final ByteOrder byteOrder) {
        return ordered(getLongOpaque(index), byteOrder);
    }

    @Override
    public long getLongAcquire(final int index) {
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getLongAcquire(null, offset + index) :
                ACCESS.getLongAcquire(base, offset + index));
    }

    @Override
    public long getLongAcquire(final int index, final ByteOrder byteOrder) {
        return ordered(getLongAcquire(index), byteOrder);
    }

    @Override
    public long getLongVolatile(final int index) {
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getLongVolatile(null, offset + index) :
                ACCESS.getLongVolatile(base, offset + index));
    }

    @Override
    public long getLongVolatile(final int index, final ByteOrder byteOrder) {
        return ordered(getLongVolatile(index), byteOrder);
    }

    @Override
    public void putLongOpaque(final int index, final long value) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putLongOpaque(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putLongOpaque(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putLongOpaque(final int index, final long value, final ByteOrder byteOrder) {
        putLongOpaque(index, ordered(value, byteOrder));
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putLongRelease(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putLongRelease(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putLongRelease(final int index, final long value, final ByteOrder byteOrder) {
        putLongRelease(index, ordered(value, byteOrder));
    }

    @Override
    public void putLongVolatile(final int index, final long value) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putLongVolatile(null, offset + index, littleEndian(value));
        } else {
            ACCESS.putLongVolatile(base, offset + index, littleEndian(value));
        }
    }

    @Override
    public void putLongVolatile(final int index, final long value, final ByteOrder byteOrder) {
        putLongVolatile(index, ordered(value, byteOrder));
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return base == null ?
                ACCESS.compareAndSetLong(null, offset + index, littleEndian(expectedValue), littleEndian(updateValue)) :
                ACCESS.compareAndSetLong(base, offset + index, littleEndian(expectedValue), littleEndian(updateValue));
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue,
                                     final ByteOrder byteOrder) {
        return compareAndSetLong(index, ordered(expectedValue, byteOrder), ordered(updateValue, byteOrder));
    }

    @Override
    public long compareAndExchangeLong(final int index, final long expectedValue, final long updateValue) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        final long expected = littleEndian(expectedValue);
        final long update = littleEndian(updateValue);
        return littleEndian(base == null ? ACCESS.compareAndExchangeLong(null, offset + index, expected, update) :
                ACCESS.compareAndExchangeLong(base, offset + index, expected, update));
    }

    @Override
    public long compareAndExchangeLong(final int index, final long expectedValue, final long updateValue,
                                       final ByteOrder byteOrder) {
        final long found = compareAndExchangeLong(index, ordered(expectedValue, byteOrder),
                ordered(updateValue, byteOrder));
        return ordered(found, byteOrder);
    }

    @Override
    public long getAndAddLong(final int index, final long delta) {
        if (!NATIVE_LITTLE_ENDIAN) {
            return getAndAddLongByCompareAndSet(index, delta, ByteOrder.LITTLE_ENDIAN);
        }
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return base == null ? ACCESS.getAndAddLong(null, offset + index, delta) :
                ACCESS.getAndAddLong(base, offset + index, delta);
    }

    @Override
    public long getAndAddLong(final int index, final long delta, final ByteOrder byteOrder) {
        return byteOrder == ByteOrder.LITTLE_ENDIAN ? getAndAddLong(index, delta) :
                getAndAddLongByCompareAndSet(index, delta, byteOrder);
    }

    private long getAndAddLongByCompareAndSet(final int index, final long delta, final ByteOrder byteOrder) {
        long value;
        do {
            value = getLongVolatile(index, byteOrder);
        } while (!compareAndSetLong(index, value, value + delta, byteOrder));
        return value;
    }

    @Override
    public long getAndSetLong(final int index, final long value) {
        if (expandable) {
            ensureCapacity(index, Long.BYTES);
        }
        checkBounds(index, Long.BYTES);
        checkOrdered(index, Long.BYTES);
        final Object base = heapBase;
        return littleEndian(base == null ? ACCESS.getAndSetLong(null, offset + index, littleEndian(value)) :
                ACCESS.getAndSetLong(base, offset + index, littleEndian(value)));
    }

    @Override
    public long getAndSetLong(final int index, final long value, final ByteOrder byteOrder) {
        return ordered(getAndSetLong(index, ordered(value, byteOrder)), byteOrder);
    }

    //--- bulk copies ---

    @Override
    public void getBytes(final int index, final byte[] destination) {
        getBytes(index, destination, 0, destination.length);
    }

    @Override
    public void getBytes(final int index, final byte[] destination, final int destinationOffset, final int length) {
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(destinationOffset, length, destination.length);
        ACCESS.copyMemory(heapBase, offset + index, ACCESS.heapBase(destination),
                ACCESS.heapOffset(destination, destinationOffset), length);
    }

    @Override
    public void getBytes(final int index, final MutableMemoryBuffer destination, final int destinationIndex,
                         final int length) {
        destination.putBytes(destinationIndex, this, index, length);
    }

    @Override
    public void getBytes(final int index, final ByteBuffer destination, final int destinationOffset,
                         final int length) {
        Objects.checkFromIndexSize(index, length, capacity);
        Objects.checkFromIndexSize(destinationOffset, length, destination.capacity());
        if (destination.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        if (destination.isDirect()) {
            ACCESS.copyMemory(heapBase, offset + index, null, ACCESS.address(destination) + destinationOffset,
                    length);
            Reference.reachabilityFence(destination);
        } else {
            final byte[] array = destination.array();
            ACCESS.copyMemory(heapBase, offset + index, ACCESS.heapBase(array),
                    ACCESS.heapOffset(array, destination.arrayOffset() + destinationOffset), length);
        }
    }

    @Override
    public void getBytes(final int index, final ByteBuffer destination, final int length) {
        final int position = destination.position();
        Objects.checkFromIndexSize(position, length, destination.limit());
        getBytes(index, destination, position, length);
        destination.position(position + length);
    }

    @Override
    public void putBytes(final int index, final byte[] source) {
        putBytes(index, source, 0, source.length);
    }

    @Override
    public void putBytes(final int index, final byte[] source, final int sourceOffset, final int length) {
        Objects.checkFromIndexSize(sourceOffset, length, source.length);
        if (expandable) {
            ensureCapacity(index, length);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        ACCESS.copyMemory(ACCESS.heapBase(source), ACCESS.heapOffset(source, sourceOffset), heapBase,
                offset + index, length);
    }

    @Override
    public void putBytes(final int index, final MemoryBuffer source, final int sourceIndex, final int length) {
        final MemoryBufferImpl buffer = (MemoryBufferImpl) source;
        Objects.checkFromIndexSize(sourceIndex, length, buffer.capacity);
        if (expandable) {
            ensureCapacity(index, length);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        ACCESS.copyMemory(buffer.heapBase, buffer.offset + sourceIndex, heapBase, offset + index, length);
        Reference.reachabilityFence(buffer.owner);
    }

    @Override
    public void putBytes(final int index, final ByteBuffer source, final int sourceOffset, final int length) {
        Objects.checkFromIndexSize(sourceOffset, length, source.capacity());
        if (expandable) {
            ensureCapacity(index, length);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        if (source.isDirect()) {
            ACCESS.copyMemory(null, ACCESS.address(source) + sourceOffset, heapBase, offset + index, length);
            Reference.reachabilityFence(source);
        } else if (source.hasArray()) {
            final byte[] array = source.array();
            ACCESS.copyMemory(ACCESS.heapBase(array), ACCESS.heapOffset(array, source.arrayOffset() + sourceOffset),
                    heapBase, offset + index, length);
        } else {
            for (int i = 0; i < length; i++) {
                rawPutByte(index + i, source.get(sourceOffset + i));
            }
        }
    }

    @Override
    public void putBytes(final int index, final ByteBuffer source, final int length) {
        final int position = source.position();
        Objects.checkFromIndexSize(position, length, source.limit());
        putBytes(index, source, position, length);
        source.position(position + length);
    }

    @Override
    public void setMemory(final int index, final int length, final byte value) {
        if (expandable) {
            ensureCapacity(index, length);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        ACCESS.setMemory(heapBase, offset + index, length, value);
    }

    //--- ASCII text ---

    @Override
    public int getTextAscii(final int index, final int length, final Appendable destination) {
        if (destination instanceof StringBuilder) {
            return getTextAscii(index, length, (StringBuilder) destination);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        try {
            for (int i = 0; i < length; i++) {
                destination.append((char) (rawGetByte(index + i) & 0xff));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return length;
    }

    @Override
    public int getTextAscii(final int index, final int length, final StringBuilder destination) {
        Objects.checkFromIndexSize(index, length, capacity);
        destination.ensureCapacity(destination.length() + length);
        for (int i = 0; i < length; i++) {
            destination.append((char) (rawGetByte(index + i) & 0xff));
        }
        return length;
    }

    @Override
    public String getTextAscii(final int index, final int length) {
        final byte[] bytes = new byte[length];
        getBytes(index, bytes, 0, length);
        return new String(bytes, ISO_8859_1);
    }

    @Override
    public int putTextAscii(final int index, final CharSequence value) {
        return putTextAscii(index, value, 0, value.length());
    }

    @Override
    public int putTextAscii(final int index, final CharSequence value, final int valueOffset, final int length) {
        Objects.checkFromIndexSize(valueOffset, length, value.length());
        if (expandable) {
            ensureCapacity(index, length);
        }
        Objects.checkFromIndexSize(index, length, capacity);
        for (int i = 0; i < length; i++) {
            final char ch = value.charAt(valueOffset + i);
            rawPutByte(index + i, ch < 0x80 ? (byte) ch : (byte) '?');
        }
        return length;
    }

    //--- UTF-8 text ---

    @Override
    public int getTextUtf8(final int index, final int length, final Appendable destination) {
        try {
            return decodeUtf8(index, length, destination, true);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public int getTextUtf8(final int index, final int length, final StringBuilder destination) {
        try {
            return decodeUtf8(index, length, destination, true);
        } catch (final IOException e) {
            throw new AssertionError("A string builder does not throw", e);
        }
    }

    @Override
    public String getTextUtf8(final int index, final int length) {
        final StringBuilder text = new StringBuilder(length);
        try {
            decodeUtf8(index, length, text, false);
        } catch (final IOException e) {
            throw new AssertionError("A string builder does not throw", e);
        }
        return text.toString();
    }

    /**
     * Decodes UTF-8 exactly as the JDK's decoder does (that of {@code new String(bytes, UTF_8)}, the same in Java 17
     * to 25), replacing malformed input by the replacement character; a truncated sequence at the end becomes one
     * replacement character, or stops resumable decoding before it.
     */
    private int decodeUtf8(final int index, final int length, final Appendable destination, final boolean resumable)
            throws IOException {
        Objects.checkFromIndexSize(index, length, capacity);
        final int end = index + length;
        int i = index;
        while (i < end) {
            final int b1 = rawGetByte(i);
            if (b1 >= 0) {
                destination.append((char) b1);
                i++;
            } else if ((b1 >> 5) == -2 && (b1 & 0x1e) != 0) {
                if (i + 1 < end) {
                    final int b2 = rawGetByte(i + 1);
                    if (isNotContinuation(b2)) {
                        destination.append(REPLACEMENT_CHARACTER);
                        i++;
                    } else {
                        destination.append((char) (((b1 & 0x1f) << 6) | (b2 & 0x3f)));
                        i += 2;
                    }
                    continue;
                }
                return truncatedAtEnd(index, end, i, destination, resumable);
            } else if ((b1 >> 4) == -2) {
                if (i + 2 < end) {
                    final int b2 = rawGetByte(i + 1);
                    final int b3 = rawGetByte(i + 2);
                    if (isMalformed3(b1, b2) || isNotContinuation(b3)) {
                        destination.append(REPLACEMENT_CHARACTER);
                        i += isMalformed3(b1, b2) ? 1 : 2;
                    } else {
                        final char ch = (char) (((b1 & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f));
                        destination.append(Character.isSurrogate(ch) ? REPLACEMENT_CHARACTER : ch);
                        i += 3;
                    }
                    continue;
                }
                if (i + 1 < end && isMalformed3(b1, rawGetByte(i + 1))) {
                    destination.append(REPLACEMENT_CHARACTER);
                    i++;
                    continue;
                }
                return truncatedAtEnd(index, end, i, destination, resumable);
            } else if ((b1 >> 3) == -2) {
                final int lead = b1 & 0xff;
                if (i + 3 < end) {
                    final int b2 = rawGetByte(i + 1);
                    final int b3 = rawGetByte(i + 2);
                    final int b4 = rawGetByte(i + 3);
                    final int codePoint = ((lead & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3f) << 6) |
                            (b4 & 0x3f);
                    if (isNotContinuation(b2) || isNotContinuation(b3) || isNotContinuation(b4) ||
                            !Character.isSupplementaryCodePoint(codePoint)) {
                        destination.append(REPLACEMENT_CHARACTER);
                        i += isMalformed4(lead, b2 & 0xff) ? 1 : isNotContinuation(b3) ? 2 : 3;
                    } else {
                        destination.append(Character.highSurrogate(codePoint))
                                .append(Character.lowSurrogate(codePoint));
                        i += 4;
                    }
                    continue;
                }
                if (lead > 0xf4 || (i + 1 < end && isMalformed4(lead, rawGetByte(i + 1) & 0xff))) {
                    destination.append(REPLACEMENT_CHARACTER);
                    i++;
                    continue;
                }
                if (i + 2 < end && isNotContinuation(rawGetByte(i + 2))) {
                    destination.append(REPLACEMENT_CHARACTER);
                    i += 2;
                    continue;
                }
                return truncatedAtEnd(index, end, i, destination, resumable);
            } else {
                destination.append(REPLACEMENT_CHARACTER);
                i++;
            }
        }
        return i - index;
    }

    private static int truncatedAtEnd(final int index, final int end, final int position,
                                      final Appendable destination, final boolean resumable) throws IOException {
        if (resumable) {
            return position - index;
        }
        destination.append(REPLACEMENT_CHARACTER);
        return end - index;
    }

    private static boolean isNotContinuation(final int b) {
        return (b & 0xc0) != 0x80;
    }

    private static boolean isMalformed3(final int b1, final int b2) {
        return (b1 == (byte) 0xe0 && (b2 & 0xe0) == 0x80) || isNotContinuation(b2);
    }

    private static boolean isMalformed4(final int lead, final int b2) {
        return lead > 0xf4 || (lead == 0xf0 && (b2 < 0x90 || b2 > 0xbf)) || (lead == 0xf4 && (b2 & 0xf0) != 0x80) ||
                isNotContinuation(b2);
    }

    @Override
    public int putTextUtf8(final int index, final CharSequence value) {
        return putTextUtf8(index, value, 0, value.length());
    }

    @Override
    public int putTextUtf8(final int index, final CharSequence value, final int valueOffset, final int length) {
        final int bytes = Utf8.length(value, valueOffset, length);
        if (expandable) {
            ensureCapacity(index, bytes);
        }
        Objects.checkFromIndexSize(index, bytes, capacity);
        final int end = valueOffset + length;
        int position = index;
        for (int i = valueOffset; i < end; i++) {
            final char ch = value.charAt(i);
            if (ch < 0x80) {
                rawPutByte(position++, (byte) ch);
            } else if (ch < 0x800) {
                rawPutByte(position++, (byte) (0xC0 | (ch >> 6)));
                rawPutByte(position++, (byte) (0x80 | (ch & 0x3F)));
            } else if (!Character.isSurrogate(ch)) {
                rawPutByte(position++, (byte) (0xE0 | (ch >> 12)));
                rawPutByte(position++, (byte) (0x80 | ((ch >> 6) & 0x3F)));
                rawPutByte(position++, (byte) (0x80 | (ch & 0x3F)));
            } else if (Character.isHighSurrogate(ch) && i + 1 < end && Character.isLowSurrogate(value.charAt(i + 1))) {
                final int codePoint = Character.toCodePoint(ch, value.charAt(++i));
                rawPutByte(position++, (byte) (0xF0 | (codePoint >> 18)));
                rawPutByte(position++, (byte) (0x80 | ((codePoint >> 12) & 0x3F)));
                rawPutByte(position++, (byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                rawPutByte(position++, (byte) (0x80 | (codePoint & 0x3F)));
            } else {
                rawPutByte(position++, (byte) '?');
            }
        }
        return bytes;
    }

    @Override
    public String toString() {
        return "MemoryBuffer:" + (isDirect() ? "direct" : "heap") + "|capacity=" + capacity +
                (expandable ? "|expandable" : "");
    }
}
