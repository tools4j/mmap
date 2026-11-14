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

import org.tools4j.mmap.core.buffer.MemoryAccessMode;
import org.tools4j.mmap.core.buffer.MemoryBinding;
import org.tools4j.mmap.core.buffer.MemoryBuffer;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MemoryBuffers;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.core.text.Utf8;
import org.tools4j.mmap.core.unsafe.MemoryAccess;
import org.tools4j.mmap.core.unsafe.Unsafe;
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
import static org.tools4j.mmap.core.buffer.MemoryBinding.EXPANDABLE;
import static org.tools4j.mmap.core.buffer.MemoryBinding.FIXED;
import static org.tools4j.mmap.core.buffer.MemoryBinding.WRAPPABLE;

/**
 * The one implementation of all memory buffer interfaces, so that call sites stay monomorphic whatever memory they
 * access.
 * <p>
 * Memory is an address, or a heap base and an offset, for the {@link MemoryAccess} chosen once per JVM: a null base
 * and the address for direct memory, else the base and offset of a {@code byte[]} or {@code long[]}. Every access
 * branches on the base, calling the overload for direct memory, for a {@code byte[]}, if the access supports it, or
 * for other heap memory, so that each branch compiles to a pure off-heap or on-heap access of a known type, small
 * enough to be inlined also in a branch that is rarely taken; the branch is moved out of loops.
 * Multi-byte values are little endian unless another byte order is given. Each accessor has one implementation
 * taking the byte order, which converts a value at most once, only if the order is not the native one; the default
 * passes little endian, so that the comparison constant-folds away on all platforms.
 * <p>
 * Bounds are checked with the intrinsic {@link Objects#checkIndex(int, int)}, which C2 eliminates in loops.
 * Expandable buffers grow before the bounds check of a write, so that the check stays the same for all buffers.
 * Writes check against a write capacity of their own, the capacity or 0 for read-only memory, so that writing to
 * read-only memory, such as a read-only memory mapping, throws instead of crashing the JVM, at no extra cost. The
 * write capacity is set on every wrap, from the wrapped memory, so read-only is a property of the memory, not of the
 * buffer. Ordered accesses check against ordered capacities in the same way, 0 for memory backed by a {@code byte[]},
 * which has no ordered access, so that rejecting it costs nothing either; their alignment check can be disabled, see
 * {@link CoreConfigurations#ALIGNMENT_CHECK_PROPERTY}.
 * <p>
 * Only flyweights can be wrapped: all buffers are instances of this class, and a cast must not let anyone re-wrap an
 * allocated buffer or a shared constant such as {@link MemoryBuffers#EMPTY}.
 */
public final class MemoryBufferImpl implements MemoryBufferFlyweight {

    private static final int FFM_JAVA_VERSION = 22;
    private static final String FFM_MEMORY_ACCESS = "org.tools4j.mmap.core.unsafe.FfmMemoryAccess";
    private static final MemoryAccessMode MODE = CoreConfigurations.systemMemoryAccessMode();
    private static final MemoryAccess ACCESS = memoryAccess();
    private static final boolean ALIGNMENT_CHECK = CoreConfigurations.systemAlignmentCheck();
    private static final ByteOrder NATIVE_ORDER = ByteOrder.nativeOrder();
    private static final byte[] NO_BYTES = {};
    private static final int DIRECT_ALIGNMENT = 64;
    private static final int MAX_CAPACITY = Integer.MAX_VALUE - DIRECT_ALIGNMENT;
    private static final int MIN_EXPANDED_CAPACITY = 64;
    /** The bits of {@link #addressOrOffset} holding the misalignment of index 0 of memory with ordered access. */
    private static final int MISALIGNMENT_MASK = Long.BYTES - 1;
    private static final char REPLACEMENT_CHARACTER = '�';

    private final MemoryBinding binding;

    private Object heapBase;
    /**
     * The address, or the offset in the heap base; its low bits are the misalignment of index 0 for all memory with
     * ordered access, so that no field of its own holds it. The offset of a {@code long[]} is a multiple of 8, which
     * the JDK's own atomic accesses to its elements require, e.g. by {@code AtomicLongArray}, and the tests check.
     */
    private long addressOrOffset;
    private int capacity;
    /**
     * The capacity for writes, checked by every write instead of the capacity: equal to it, or 0 for read-only memory,
     * so that a write to read-only memory throws instead of crashing the JVM, at no extra cost.
     */
    private int writeCapacity;
    /**
     * The capacity for ordered reads, checked by them instead of the capacity: equal to it, or 0 for memory without
     * ordered access, so that rejecting such an access costs no more than the bounds check it replaces.
     */
    private int orderedCapacity;
    /**
     * The capacity for ordered writes: equal to the capacity, or 0 for read-only memory or memory without ordered
     * access.
     */
    private int orderedWriteCapacity;
    private int wrapOffset;
    private Object owner;

    private MemoryBufferImpl(final MemoryBinding binding) {
        this.binding = binding;
        clear();
    }

    private static MemoryAccess memoryAccess() {
        return MODE == MemoryAccessMode.FFM ? ffmMemoryAccess() : UnsafeMemoryAccess.INSTANCE;
    }

    private static MemoryAccess ffmMemoryAccess() {
        if (Runtime.version().feature() < FFM_JAVA_VERSION) {
            throw new IllegalStateException("Memory access through FFM needs Java " + FFM_JAVA_VERSION +
                    " or later, running on " + Runtime.version());
        }
        try {
            return (MemoryAccess) Class.forName(FFM_MEMORY_ACCESS).getEnumConstants()[0];
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Memory access through FFM is not in this build of mmap-core, " +
                    "built with Java " + (FFM_JAVA_VERSION - 1) + " or earlier", e);
        }
    }

    /**
     * Returns the memory access mode of all buffers, chosen when this class is initialised.
     *
     * @return the mode
     */
    public static MemoryAccessMode memoryAccessMode() {
        return MODE;
    }

    //--- factories, used by MemoryBuffers ---

    /**
     * Returns a new flyweight wrapping no memory.
     *
     * @return the flyweight
     */
    public static MemoryBufferFlyweight flyweight() {
        return new MemoryBufferImpl(WRAPPABLE);
    }

    /**
     * Returns a new buffer with capacity 0 that cannot be wrapped, to be shared as a constant.
     *
     * @return the buffer
     */
    public static MutableMemoryBuffer empty() {
        return new MemoryBufferImpl(FIXED);
    }

    /**
     * Allocates a heap buffer backed by a {@code long[]}.
     *
     * @param capacity the capacity in bytes
     * @return the buffer
     */
    public static MutableMemoryBuffer allocate(final int capacity) {
        validateCapacity(capacity);
        final MemoryBufferImpl buffer = new MemoryBufferImpl(FIXED);
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
        final MemoryBufferImpl buffer = new MemoryBufferImpl(FIXED);
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
        final MemoryBufferImpl buffer = new MemoryBufferImpl(FIXED);
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
        final MemoryBufferImpl buffer = new MemoryBufferImpl(EXPANDABLE);
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
        final MemoryBufferImpl buffer = new MemoryBufferImpl(EXPANDABLE);
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
        this.addressOrOffset = ACCESS.heapOffset(array, byteOffset);
        this.capacity = length;
        this.writeCapacity = length;
        this.orderedCapacity = 0;
        this.orderedWriteCapacity = 0;
        this.wrapOffset = wrapOffset;
        this.owner = owner;
    }

    private void wrapHeap(final long[] array, final int byteOffset, final int length) {
        this.heapBase = array == owner ? heapBase : ACCESS.heapBase(array);
        this.addressOrOffset = ACCESS.heapOffset(array, byteOffset);
        this.capacity = length;
        this.writeCapacity = length;
        this.orderedCapacity = length;
        this.orderedWriteCapacity = length;
        this.wrapOffset = byteOffset;
        this.owner = array;
    }

    private void wrapDirect(final long address, final int capacity, final int wrapOffset, final Object owner) {
        this.heapBase = null;
        this.addressOrOffset = address;
        this.capacity = capacity;
        this.writeCapacity = capacity;
        this.orderedCapacity = capacity;
        this.orderedWriteCapacity = capacity;
        this.wrapOffset = wrapOffset;
        this.owner = owner;
    }

    private void wrapAllocatedDirect(final int capacity) {
        final ByteBuffer memory = ByteBuffer.allocateDirect(capacity + DIRECT_ALIGNMENT - 1);
        final long address = ACCESS.address(memory);
        final long aligned = (address + DIRECT_ALIGNMENT - 1) & -DIRECT_ALIGNMENT;
        wrapDirect(aligned, capacity, 0, memory);
    }

    @Unsafe
    @Override
    public void wrap(final long address, final int capacity) {
        checkWrappable();
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        if (address == MemoryBuffers.NULL_ADDRESS) {
            if (capacity > 0) {
                throw new IllegalArgumentException("Cannot wrap " + capacity + " bytes at the null address");
            }
            clear();
            return;
        }
        wrapDirect(address, capacity, 0, null);
    }

    @Override
    public void wrap(final byte[] array) {
        checkWrappable();
        wrapHeap(array, 0, array.length, 0, array);
    }

    @Override
    public void wrap(final byte[] array, final int offset, final int length) {
        checkWrappable();
        Objects.checkFromIndexSize(offset, length, array.length);
        wrapHeap(array, offset, length, offset, array);
    }

    @Override
    public void wrap(final ByteBuffer buffer) {
        checkWrappable();
        wrap(buffer, 0, buffer.capacity());
    }

    @Override
    public void wrap(final ByteBuffer buffer, final int offset, final int length) {
        checkWrappable();
        Objects.checkFromIndexSize(offset, length, buffer.capacity());
        if (buffer.isDirect()) {
            wrapDirect(directAddress(buffer) + offset, length, offset, buffer);
        } else {
            wrapHeap(buffer, offset, length);
        }
        if (buffer.isReadOnly()) {
            this.writeCapacity = 0;
            this.orderedWriteCapacity = 0;
        }
    }

    /**
     * Wraps the memory of a heap byte buffer, readable through the memory access even if the byte buffer is
     * read-only, i.e. without accessible array; plain access only, as for a {@code byte[]}.
     */
    private void wrapHeap(final ByteBuffer buffer, final int offset, final int length) {
        final Object base = buffer == owner ? heapBase : ACCESS.heapBase(buffer);
        this.addressOrOffset = ACCESS.heapOffset(buffer, offset);
        this.heapBase = base;
        this.capacity = length;
        this.writeCapacity = length;
        this.orderedCapacity = 0;
        this.orderedWriteCapacity = 0;
        this.wrapOffset = offset;
        this.owner = buffer;
    }

    /**
     * Returns the address of a direct byte buffer, without asking the memory access again if it is already wrapped,
     * since FFM creates a segment to find it out.
     */
    private long directAddress(final ByteBuffer buffer) {
        return buffer == owner ? addressOrOffset - wrapOffset : ACCESS.address(buffer);
    }

    @Override
    public void wrap(final MemoryBuffer buffer) {
        checkWrappable();
        wrapRange(buffer, 0, buffer.capacity());
    }

    @Override
    public void wrap(final MemoryBuffer buffer, final int index, final int length) {
        checkWrappable();
        wrapRange(buffer, index, length);
    }

    private void wrapRange(final MemoryBuffer buffer, final int index, final int length) {
        if (!(buffer instanceof MemoryBufferImpl source)) {
            throw new IllegalArgumentException("Cannot wrap a buffer of another implementation, such as a test " +
                    "double, whose memory is unknown: wrap its memory directly, e.g. its address or array: " + buffer);
        }
        wrapRange(source, index, length);
    }

    private void wrapRange(final MemoryBufferImpl source, final int index, final int length) {
        Objects.checkFromIndexSize(index, length, source.capacity);
        this.heapBase = source.heapBase;
        this.addressOrOffset = source.addressOrOffset + index;
        this.capacity = length;
        //each capacity of the source is its capacity, at least index + length, or 0
        this.writeCapacity = Math.min(source.writeCapacity, length);
        this.orderedCapacity = Math.min(source.orderedCapacity, length);
        this.orderedWriteCapacity = Math.min(source.orderedWriteCapacity, length);
        this.wrapOffset = source.wrapOffset + index;
        this.owner = source.owner;
    }

    @Override
    public void makeReadOnly() {
        checkWrappable();
        this.writeCapacity = 0;
        this.orderedWriteCapacity = 0;
    }

    @Override
    public void unwrap() {
        checkWrappable();
        clear();
    }

    private void clear() {
        wrapHeap(NO_BYTES, 0, 0, 0, NO_BYTES);
    }

    private void checkWrappable() {
        if (binding != WRAPPABLE) {
            throw new UnsupportedOperationException("Only flyweights can be wrapped and unwrapped, not this " +
                    binding.name().toLowerCase() + " buffer");
        }
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
    public boolean isReadOnly() {
        return writeCapacity < capacity;
    }

    @Override
    public MemoryBinding binding() {
        return binding;
    }

    @Override
    public long alignment() {
        if (heapBase == null) {
            return Long.lowestOneBit(addressOrOffset);
        }
        if (!(owner instanceof long[])) {
            return 1;//a byte[] or heap byte buffer
        }
        final int misalignment = (int) addressOrOffset & MISALIGNMENT_MASK;
        return misalignment == 0 ? Long.BYTES : Integer.lowestOneBit(misalignment);
    }

    @Override
    public int wrapOffset() {
        return wrapOffset;
    }

    @Unsafe
    @Override
    public long address() {
        return heapBase == null ? addressOrOffset : MemoryBuffers.NULL_ADDRESS;
    }

    //--- checks ---

    private void checkBounds(final int index, final int size) {
        Objects.checkIndex(index, capacity - size + 1);
    }

    /**
     * Checks the bounds of a write against the write capacity, the failure being on a path of its own, so that the
     * check costs the same as a read's and the exception tells read-only memory apart from an index out of bounds.
     */
    private void checkWriteBounds(final int index, final int size) {
        try {
            Objects.checkIndex(index, writeCapacity - size + 1);
        } catch (final IndexOutOfBoundsException e) {
            throw readOnlyFailure(index, size);
        }
    }

    private void checkWriteRange(final int index, final int length) {
        try {
            Objects.checkFromIndexSize(index, length, writeCapacity);
        } catch (final IndexOutOfBoundsException e) {
            throw readOnlyRangeFailure(index, length);
        }
    }

    /** Throws the exception of a read beyond the capacity, else returns the one for a write to read-only memory. */
    private ReadOnlyBufferException readOnlyFailure(final int index, final int size) {
        checkBounds(index, size);
        return new ReadOnlyBufferException();
    }

    private ReadOnlyBufferException readOnlyRangeFailure(final int index, final int length) {
        Objects.checkFromIndexSize(index, length, capacity);
        return new ReadOnlyBufferException();
    }

    /** Checks the bounds of an ordered read against the ordered capacity, and its alignment unless disabled. */
    private void checkOrderedBounds(final int index, final int size) {
        try {
            Objects.checkIndex(index, orderedCapacity - size + 1);
        } catch (final IndexOutOfBoundsException e) {
            throw orderedFailure(index, size);
        }
        if (ALIGNMENT_CHECK) {
            checkAlignment(index, size);
        }
    }

    /** Checks the bounds of an ordered write against the ordered write capacity, and its alignment unless disabled. */
    private void checkOrderedWriteBounds(final int index, final int size) {
        try {
            Objects.checkIndex(index, orderedWriteCapacity - size + 1);
        } catch (final IndexOutOfBoundsException e) {
            throw orderedWriteFailure(index, size);
        }
        if (ALIGNMENT_CHECK) {
            checkAlignment(index, size);
        }
    }

    private void checkAlignment(final int index, final int size) {
        if (((addressOrOffset + index) & (size - 1)) != 0) {
            throw misalignedAccess(index, size);
        }
    }

    /** Throws the exception of a read beyond the capacity, else returns the one for memory without ordered access. */
    private UnsupportedOperationException orderedFailure(final int index, final int size) {
        checkBounds(index, size);
        return unsupportedOrderedAccess();
    }

    /**
     * Throws the exception of a read beyond the capacity, else returns the one for a write to read-only memory, else
     * the one for memory without ordered access.
     */
    private RuntimeException orderedWriteFailure(final int index, final int size) {
        checkBounds(index, size);
        return writeCapacity < capacity ? new ReadOnlyBufferException() : unsupportedOrderedAccess();
    }

    private static UnsupportedOperationException unsupportedOrderedAccess() {
        return new UnsupportedOperationException("Ordered access to multi-byte values is not supported for memory " +
                "backed by a byte[], whose elements have no guaranteed alignment");
    }

    private IllegalStateException misalignedAccess(final int index, final int size) {
        return new IllegalStateException("Misaligned ordered access of " + size + " bytes at index " + index +
                " with alignment " + alignment());
    }

    @Override
    public void ensureCapacity(final int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative: " + capacity);
        }
        ensureCapacity((long) capacity);
    }

    @Override
    public void ensureCapacity(final int index, final int size) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index cannot be negative: " + index);
        }
        if (size < 0) {
            throw new IllegalArgumentException("Size cannot be negative: " + size);
        }
        ensureCapacity((long) index + size);
    }

    private void ensureCapacity(final long required) {
        if (required <= capacity) {
            return;
        }
        if (binding != EXPANDABLE) {
            throw new UnsupportedOperationException("Cannot grow this " + binding.name().toLowerCase() +
                    " buffer from capacity " + capacity + " to " + required);
        }
        if (required > MAX_CAPACITY) {
            throw new IllegalArgumentException("Capacity " + required + " exceeds the maximum " + MAX_CAPACITY);
        }
        grow(required);
    }

    private void expandFor(final int index, final int size) {
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
        if (heapBase == null) {
            final long oldAddress = addressOrOffset;
            final int oldCapacity = capacity;
            final Object oldOwner = owner;
            wrapAllocatedDirect(newCapacity);
            ACCESS.copyMemory(null, oldAddress, null, addressOrOffset, oldCapacity);
            Reference.reachabilityFence(oldOwner);
        } else {
            wrapHeap(Arrays.copyOf((long[]) owner, longsFor(newCapacity)), 0, newCapacity);
        }
    }

    //--- byte order ---

    /** Converts a value between native byte order and the given one, in either direction. */
    private static short reorder(final short value, final ByteOrder byteOrder) {
        return byteOrder == NATIVE_ORDER ? value : Short.reverseBytes(value);
    }

    private static char reorder(final char value, final ByteOrder byteOrder) {
        return byteOrder == NATIVE_ORDER ? value : Character.reverseBytes(value);
    }

    private static int reorder(final int value, final ByteOrder byteOrder) {
        return byteOrder == NATIVE_ORDER ? value : Integer.reverseBytes(value);
    }

    private static long reorder(final long value, final ByteOrder byteOrder) {
        return byteOrder == NATIVE_ORDER ? value : Long.reverseBytes(value);
    }

    //--- plain reads ---

    @Override
    public byte getByte(final int index) {
        checkBounds(index, Byte.BYTES);
        return rawGetByte(index);
    }

    private byte rawGetByte(final int index) {
        final Object base = heapBase;
        return base == null ? ACCESS.getByte(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getByte(bytes, addressOrOffset + index)
                : ACCESS.getByte(base, addressOrOffset + index);
    }

    @Override
    public short getShort(final int index) {
        return getShort(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public short getShort(final int index, final ByteOrder byteOrder) {
        checkBounds(index, Short.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getShort(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getShort(bytes, addressOrOffset + index)
                : ACCESS.getShort(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public char getChar(final int index) {
        return getChar(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public char getChar(final int index, final ByteOrder byteOrder) {
        checkBounds(index, Character.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getChar(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getChar(bytes, addressOrOffset + index)
                : ACCESS.getChar(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public int getInt(final int index) {
        return getInt(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getInt(final int index, final ByteOrder byteOrder) {
        checkBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getInt(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getInt(bytes, addressOrOffset + index)
                : ACCESS.getInt(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public long getLong(final int index) {
        return getLong(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getLong(final int index, final ByteOrder byteOrder) {
        checkBounds(index, Long.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getLong(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getLong(bytes, addressOrOffset + index)
                : ACCESS.getLong(base, addressOrOffset + index), byteOrder);
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
        if (binding == EXPANDABLE) {
            expandFor(index, Byte.BYTES);
        }
        checkWriteBounds(index, Byte.BYTES);
        rawPutByte(index, value);
    }

    private void rawPutByte(final int index, final byte value) {
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putByte(addressOrOffset + index, value);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putByte(bytes, addressOrOffset + index, value);
        } else {
            ACCESS.putByte(base, addressOrOffset + index, value);
        }
    }

    @Override
    public void putShort(final int index, final short value) {
        putShort(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putShort(final int index, final short value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Short.BYTES);
        }
        checkWriteBounds(index, Short.BYTES);
        final Object base = heapBase;
        final short reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putShort(addressOrOffset + index, reordered);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putShort(bytes, addressOrOffset + index, reordered);
        } else {
            ACCESS.putShort(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putChar(final int index, final char value) {
        putChar(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putChar(final int index, final char value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Character.BYTES);
        }
        checkWriteBounds(index, Character.BYTES);
        final Object base = heapBase;
        final char reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putChar(addressOrOffset + index, reordered);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putChar(bytes, addressOrOffset + index, reordered);
        } else {
            ACCESS.putChar(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putInt(final int index, final int value) {
        putInt(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putInt(final int index, final int value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putInt(addressOrOffset + index, reordered);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putInt(bytes, addressOrOffset + index, reordered);
        } else {
            ACCESS.putInt(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putLong(final int index, final long value) {
        putLong(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putLong(final int index, final long value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putLong(addressOrOffset + index, reordered);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putLong(bytes, addressOrOffset + index, reordered);
        } else {
            ACCESS.putLong(base, addressOrOffset + index, reordered);
        }
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
        return base == null ? ACCESS.getByteVolatile(addressOrOffset + index)
                : base instanceof byte[] bytes ? ACCESS.getByteVolatile(bytes, addressOrOffset + index)
                : ACCESS.getByteVolatile(base, addressOrOffset + index);
    }

    @Override
    public void putByteVolatile(final int index, final byte value) {
        if (binding == EXPANDABLE) {
            expandFor(index, Byte.BYTES);
        }
        checkWriteBounds(index, Byte.BYTES);
        final Object base = heapBase;
        if (base == null) {
            ACCESS.putByteVolatile(addressOrOffset + index, value);
        } else if (base instanceof byte[] bytes) {
            ACCESS.putByteVolatile(bytes, addressOrOffset + index, value);
        } else {
            ACCESS.putByteVolatile(base, addressOrOffset + index, value);
        }
    }

    @Override
    public short getShortVolatile(final int index) {
        return getShortVolatile(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public short getShortVolatile(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Short.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getShortVolatile(addressOrOffset + index) :
                ACCESS.getShortVolatile(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public void putShortVolatile(final int index, final short value) {
        putShortVolatile(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putShortVolatile(final int index, final short value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Short.BYTES);
        }
        checkOrderedWriteBounds(index, Short.BYTES);
        final Object base = heapBase;
        final short reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putShortVolatile(addressOrOffset + index, reordered);
        } else {
            ACCESS.putShortVolatile(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public char getCharVolatile(final int index) {
        return getCharVolatile(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public char getCharVolatile(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Character.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getCharVolatile(addressOrOffset + index) :
                ACCESS.getCharVolatile(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public void putCharVolatile(final int index, final char value) {
        putCharVolatile(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putCharVolatile(final int index, final char value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Character.BYTES);
        }
        checkOrderedWriteBounds(index, Character.BYTES);
        final Object base = heapBase;
        final char reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putCharVolatile(addressOrOffset + index, reordered);
        } else {
            ACCESS.putCharVolatile(base, addressOrOffset + index, reordered);
        }
    }

    //--- ordered and atomic int access ---

    @Override
    public int getIntOpaque(final int index) {
        return getIntOpaque(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getIntOpaque(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getIntOpaque(addressOrOffset + index) :
                ACCESS.getIntOpaque(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public int getIntAcquire(final int index) {
        return getIntAcquire(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getIntAcquire(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getIntAcquire(addressOrOffset + index) :
                ACCESS.getIntAcquire(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public int getIntVolatile(final int index) {
        return getIntVolatile(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getIntVolatile(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getIntVolatile(addressOrOffset + index) :
                ACCESS.getIntVolatile(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public void putIntOpaque(final int index, final int value) {
        putIntOpaque(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putIntOpaque(final int index, final int value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putIntOpaque(addressOrOffset + index, reordered);
        } else {
            ACCESS.putIntOpaque(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putIntRelease(final int index, final int value) {
        putIntRelease(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putIntRelease(final int index, final int value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putIntRelease(addressOrOffset + index, reordered);
        } else {
            ACCESS.putIntRelease(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putIntVolatile(final int index, final int value) {
        putIntVolatile(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putIntVolatile(final int index, final int value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putIntVolatile(addressOrOffset + index, reordered);
        } else {
            ACCESS.putIntVolatile(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public boolean compareAndSetInt(final int index, final int expectedValue, final int updateValue) {
        return compareAndSetInt(index, expectedValue, updateValue, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public boolean compareAndSetInt(final int index, final int expectedValue, final int updateValue,
                                    final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int expected = reorder(expectedValue, byteOrder);
        final int update = reorder(updateValue, byteOrder);
        return base == null ? ACCESS.compareAndSetInt(addressOrOffset + index, expected, update) :
                ACCESS.compareAndSetInt(base, addressOrOffset + index, expected, update);
    }

    @Override
    public int compareAndExchangeInt(final int index, final int expectedValue, final int updateValue) {
        return compareAndExchangeInt(index, expectedValue, updateValue, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int compareAndExchangeInt(final int index, final int expectedValue, final int updateValue,
                                     final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int expected = reorder(expectedValue, byteOrder);
        final int update = reorder(updateValue, byteOrder);
        return reorder(base == null ? ACCESS.compareAndExchangeInt(addressOrOffset + index, expected, update) :
                ACCESS.compareAndExchangeInt(base, addressOrOffset + index, expected, update), byteOrder);
    }

    @Override
    public int getAndAddInt(final int index, final int delta) {
        return getAndAddInt(index, delta, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getAndAddInt(final int index, final int delta, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        if (byteOrder != NATIVE_ORDER) {
            return getAndAddIntByCompareAndSet(index, delta, byteOrder);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        return base == null ? ACCESS.getAndAddInt(addressOrOffset + index, delta) :
                ACCESS.getAndAddInt(base, addressOrOffset + index, delta);
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
        return getAndSetInt(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getAndSetInt(final int index, final int value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Integer.BYTES);
        }
        checkOrderedWriteBounds(index, Integer.BYTES);
        final Object base = heapBase;
        final int reordered = reorder(value, byteOrder);
        return reorder(base == null ? ACCESS.getAndSetInt(addressOrOffset + index, reordered) :
                ACCESS.getAndSetInt(base, addressOrOffset + index, reordered), byteOrder);
    }

    //--- ordered and atomic long access ---

    @Override
    public long getLongOpaque(final int index) {
        return getLongOpaque(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getLongOpaque(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Long.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getLongOpaque(addressOrOffset + index) :
                ACCESS.getLongOpaque(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public long getLongAcquire(final int index) {
        return getLongAcquire(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getLongAcquire(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Long.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getLongAcquire(addressOrOffset + index) :
                ACCESS.getLongAcquire(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public long getLongVolatile(final int index) {
        return getLongVolatile(index, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getLongVolatile(final int index, final ByteOrder byteOrder) {
        checkOrderedBounds(index, Long.BYTES);
        final Object base = heapBase;
        return reorder(base == null ? ACCESS.getLongVolatile(addressOrOffset + index) :
                ACCESS.getLongVolatile(base, addressOrOffset + index), byteOrder);
    }

    @Override
    public void putLongOpaque(final int index, final long value) {
        putLongOpaque(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putLongOpaque(final int index, final long value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putLongOpaque(addressOrOffset + index, reordered);
        } else {
            ACCESS.putLongOpaque(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putLongRelease(final int index, final long value) {
        putLongRelease(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putLongRelease(final int index, final long value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putLongRelease(addressOrOffset + index, reordered);
        } else {
            ACCESS.putLongRelease(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public void putLongVolatile(final int index, final long value) {
        putLongVolatile(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public void putLongVolatile(final int index, final long value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long reordered = reorder(value, byteOrder);
        if (base == null) {
            ACCESS.putLongVolatile(addressOrOffset + index, reordered);
        } else {
            ACCESS.putLongVolatile(base, addressOrOffset + index, reordered);
        }
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue) {
        return compareAndSetLong(index, expectedValue, updateValue, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public boolean compareAndSetLong(final int index, final long expectedValue, final long updateValue,
                                     final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long expected = reorder(expectedValue, byteOrder);
        final long update = reorder(updateValue, byteOrder);
        return base == null ? ACCESS.compareAndSetLong(addressOrOffset + index, expected, update) :
                ACCESS.compareAndSetLong(base, addressOrOffset + index, expected, update);
    }

    @Override
    public long compareAndExchangeLong(final int index, final long expectedValue, final long updateValue) {
        return compareAndExchangeLong(index, expectedValue, updateValue, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long compareAndExchangeLong(final int index, final long expectedValue, final long updateValue,
                                       final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long expected = reorder(expectedValue, byteOrder);
        final long update = reorder(updateValue, byteOrder);
        return reorder(base == null ? ACCESS.compareAndExchangeLong(addressOrOffset + index, expected, update) :
                ACCESS.compareAndExchangeLong(base, addressOrOffset + index, expected, update), byteOrder);
    }

    @Override
    public long getAndAddLong(final int index, final long delta) {
        return getAndAddLong(index, delta, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getAndAddLong(final int index, final long delta, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        if (byteOrder != NATIVE_ORDER) {
            return getAndAddLongByCompareAndSet(index, delta, byteOrder);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        return base == null ? ACCESS.getAndAddLong(addressOrOffset + index, delta) :
                ACCESS.getAndAddLong(base, addressOrOffset + index, delta);
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
        return getAndSetLong(index, value, ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public long getAndSetLong(final int index, final long value, final ByteOrder byteOrder) {
        if (binding == EXPANDABLE) {
            expandFor(index, Long.BYTES);
        }
        checkOrderedWriteBounds(index, Long.BYTES);
        final Object base = heapBase;
        final long reordered = reorder(value, byteOrder);
        return reorder(base == null ? ACCESS.getAndSetLong(addressOrOffset + index, reordered) :
                ACCESS.getAndSetLong(base, addressOrOffset + index, reordered), byteOrder);
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
        ACCESS.copyMemory(heapBase, addressOrOffset + index, ACCESS.heapBase(destination),
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
            ACCESS.copyMemory(heapBase, addressOrOffset + index, null, ACCESS.address(destination) + destinationOffset,
                    length);
            Reference.reachabilityFence(destination);
        } else {
            final byte[] array = destination.array();
            ACCESS.copyMemory(heapBase, addressOrOffset + index, ACCESS.heapBase(array),
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
        if (binding == EXPANDABLE) {
            expandFor(index, length);
        }
        checkWriteRange(index, length);
        ACCESS.copyMemory(ACCESS.heapBase(source), ACCESS.heapOffset(source, sourceOffset), heapBase,
                addressOrOffset + index, length);
    }

    @Override
    public void putBytes(final int index, final MemoryBuffer source, final int sourceIndex, final int length) {
        Objects.checkFromIndexSize(sourceIndex, length, source.capacity());
        if (binding == EXPANDABLE) {
            expandFor(index, length);
        }
        checkWriteRange(index, length);
        if (source instanceof MemoryBufferImpl buffer) {
            copyMemory(buffer.heapBase, buffer.addressOrOffset + sourceIndex, heapBase, addressOrOffset + index,
                    length);
            Reference.reachabilityFence(buffer.owner);
        } else {
            putOtherBytes(index, source, sourceIndex, length);
        }
    }

    /**
     * Copies memory with a constant null base for direct memory, branching on both bases like every access, so that
     * each branch compiles to a pure off-heap or on-heap copy; a {@code long[]} base is passed with its type, so that
     * the check of Unsafe that a heap base is a primitive array folds away, which it cannot for an {@code Object}.
     * <p>
     * Each branch is a call site of its own: C2 does not inline one that was rare when it compiled the caller (below
     * {@code MinInlineFrequencyRatio}), e.g. direct copies after many heap copies, and keeps calling it as long as the
     * compiled code lives. Such a branch costs about 2 ns more, but still less than a single call site for all kinds.
     */
    private static void copyMemory(final Object sourceBase, final long sourceAddressOrOffset,
                                   final Object targetBase, final long targetAddressOrOffset, final int length) {
        if (sourceBase == null) {
            if (targetBase == null) {
                ACCESS.copyMemory(null, sourceAddressOrOffset, null, targetAddressOrOffset, length);
            } else if (targetBase instanceof long[] target) {
                ACCESS.copyMemory(null, sourceAddressOrOffset, target, targetAddressOrOffset, length);
            } else {
                ACCESS.copyMemory(null, sourceAddressOrOffset, targetBase, targetAddressOrOffset, length);
            }
        } else if (sourceBase instanceof long[] source) {
            if (targetBase == null) {
                ACCESS.copyMemory(source, sourceAddressOrOffset, null, targetAddressOrOffset, length);
            } else if (targetBase instanceof long[] target) {
                ACCESS.copyMemory(source, sourceAddressOrOffset, target, targetAddressOrOffset, length);
            } else {
                ACCESS.copyMemory(source, sourceAddressOrOffset, targetBase, targetAddressOrOffset, length);
            }
        } else if (targetBase == null) {
            ACCESS.copyMemory(sourceBase, sourceAddressOrOffset, null, targetAddressOrOffset, length);
        } else if (targetBase instanceof long[] target) {
            ACCESS.copyMemory(sourceBase, sourceAddressOrOffset, target, targetAddressOrOffset, length);
        } else {
            ACCESS.copyMemory(sourceBase, sourceAddressOrOffset, targetBase, targetAddressOrOffset, length);
        }
    }

    /**
     * Copies from a buffer of another implementation, such as a test double, byte by byte through its API: its address
     * may not be the address of its memory, and copying from there could crash the JVM.
     */
    private void putOtherBytes(final int index, final MemoryBuffer source, final int sourceIndex, final int length) {
        for (int i = 0; i < length; i++) {
            rawPutByte(index + i, source.getByte(sourceIndex + i));
        }
    }

    @Override
    public void putBytes(final int index, final ByteBuffer source, final int sourceOffset, final int length) {
        Objects.checkFromIndexSize(sourceOffset, length, source.capacity());
        if (binding == EXPANDABLE) {
            expandFor(index, length);
        }
        checkWriteRange(index, length);
        if (source.isDirect()) {
            ACCESS.copyMemory(null, ACCESS.address(source) + sourceOffset, heapBase, addressOrOffset + index, length);
            Reference.reachabilityFence(source);
        } else if (source.hasArray()) {
            final byte[] array = source.array();
            ACCESS.copyMemory(ACCESS.heapBase(array), ACCESS.heapOffset(array, source.arrayOffset() + sourceOffset),
                    heapBase, addressOrOffset + index, length);
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
        if (binding == EXPANDABLE) {
            expandFor(index, length);
        }
        checkWriteRange(index, length);
        ACCESS.setMemory(heapBase, addressOrOffset + index, length, value);
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
        if (binding == EXPANDABLE) {
            expandFor(index, length);
        }
        checkWriteRange(index, length);
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
        if (binding == EXPANDABLE) {
            expandFor(index, bytes);
        }
        checkWriteRange(index, bytes);
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
                (isReadOnly() ? "|read-only" : "") + "|" + binding.name().toLowerCase();
    }
}
