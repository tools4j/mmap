/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.BitUtil;
import org.agrona.collections.IntObjConsumer;
import org.agrona.collections.IntObjPredicate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * A lock free thread safe utility with array-like API similar to {@link AtomicReferenceArray} but its length can grow
 * dynamically as needed.
 *
 * @param <E> the element type
 */
public class AtomicArray<E> {
    public static final int DEFAULT_INITIAL_LENGTH = 64;
    private static final int MAX_BITS = Integer.SIZE - 1;

    private final int firstBlockLength;
    private final int firstBlockBits;
    /**
     * <pre>
     * NOTES: 1) block[0] has initial length, which is a power of two
     *        2) for i>0, appending a block[i] doubles the total capacity
     *
     *      from this it follows
     *        i) from (2) we get for i>0: length of block[i] must be equal to the sum of all block lengths before it
     *       ii) block[1] has same length as block[0]
     *      iii) block[i].length = 2*block[i-1].length for all i>1
     *      iii) all blocks have power of 2 lengths
     *       iv) if n=#blocks, then the total capacity is initialCapacity * 2^(n-1)
     *        v) for all i>=0, block[i].length = initialCapacity * 2^max(0, i-1)
     * </pre>
     */
    private final AtomicReferenceArray<AtomicReferenceArray<E>> blocks;

    /**
     * Constructs an atomic array with {@link #DEFAULT_INITIAL_LENGTH}.
     */
    public AtomicArray() {
        this(DEFAULT_INITIAL_LENGTH);
    }

    /**
     * Constructs an atomic array with the specified initial length, which must be a power of two.
     * @param initialLength the initial length of the atomic array, a power of two
     * @throws IllegalArgumentException if initial length is not a positive power of two
     */
    public AtomicArray(final int initialLength) {
        if (!BitUtil.isPowerOfTwo(initialLength)) {
            throw new IllegalArgumentException("Initial length must be a positive power of two but was " + initialLength);
        }
        firstBlockLength = initialLength;
        firstBlockBits = blockBits(initialLength);
        blocks = new AtomicReferenceArray<>(MAX_BITS - firstBlockBits);
        blocks.set(0, new AtomicReferenceArray<>(firstBlockLength));
    }

    private static int blockBits(final int length) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(length - 1);
    }

    public int length() {
        final int n = blocks.length();
        for (int block = 1; block < n; block++) {
            if (blocks.get(block) == null) {
                return length(block - 1);
            }
        }
        return length(n - 1);
    }

    private int length(final int block) {
        return 1 << (firstBlockBits + block);
    }

    private int blockIndex(final int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index cannot be negative: " + index);
        }
        return Math.max(0, blockBits(index + 1) - firstBlockBits);
    }

    private int blockOffset(final int index, final int block) {
        return block == 0 ? index : index - length(block - 1);
    }

    private int blockLength(final int block) {
        return block <= 1 ? firstBlockLength : length(block - 1);
    }

    public E get(final int index) {
        final int block = blockIndex(index);
        final AtomicReferenceArray<E> blockData = blocks.get(block);
        if (blockData == null) {
            return null;
        }
        final int offset = blockOffset(index, block);
        return blockData.get(offset);
    }

    public E setIfAbsent(final int index, final E defaultValue) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        final E value = blockData.get(offset);
        return value != null ? value : setIfAbsent(blockData, offset, defaultValue);
    }

    public E computeIfAbsent(final int index, final IntFunction<? extends E> valueFactory) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        final E value = blockData.get(offset);
        if (value != null) {
            return value;
        }
        return setIfAbsent(blockData, offset, valueFactory.apply(index));
    }

    private static <E> E setIfAbsent(final AtomicReferenceArray<E> block, final int offset, final E defaultValue) {
        E value;
        do {
            if (block.compareAndSet(offset, null, defaultValue)) {
                value = defaultValue;
            } else {
                value = block.get(offset);
            }
        } while (value == null);
        //NOTE: it should always be only 1 iteration, unless another thread wins the CAS, and then
        //      yet another thread sets the value back to null, which is highly unlikely
        //      BUT: we want to guarantee that non-null value is returned
        return value;
    }

    public void set(final int index, final E element) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        blockData.set(offset, element);
    }

    public E getAndSet(final int index, final E element) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        return blockData.getAndSet(offset, element);
    }

    public boolean compareAndSet(final int index, final E expected, final E update) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = expected == null ? getOrCreateBlock(block) : blocks.get(block);
        return blockData != null && blockData.compareAndSet(offset, expected, update);
    }

    private AtomicReferenceArray<E> getOrCreateBlock(final int block) {
        AtomicReferenceArray<E> data = blocks.get(block);
        if (data != null) {
            return data;
        }
        for (int b = 0; b <= block; b++) {
            data = b < block ? blocks.get(b) : null;
            if (data == null) {
                final int len = blockLength(b);
                data = new AtomicReferenceArray<>(len);
                if (!blocks.compareAndSet(b, null, data)) {
                    if (b == block) {
                        data = blocks.get(block);
                    }
                }
            }
        }
        assert data != null;
        return data;
    }

    public int forEach(final Consumer<? super E> consumer) {
        return loopCount(loopForward(consumerForEach(), consumer));
    }

    public int forEach(final IntObjConsumer<? super E> consumer) {
        return loopCount(loopForward(intObjConsumerForEach(), consumer));
    }

    public int indexOf(final E value) {
        final LoopCondition<E, E> untilEqual = untilEqual();
        return matchIndex(loopForward(untilEqual, value), untilEqual, value);
    }

    public int indexOf(final IntObjPredicate<? super E> matcher) {
        final LoopCondition<E, IntObjPredicate<? super E>> untilMatching = untilMatching();
        return matchIndex(loopForward(untilMatching, matcher), untilMatching, matcher);
    }

    public int lastIndexOf(final E value) {
        return matchIndex(loopBackward(untilEqual(), value));
    }

    public int lastIndexOf(final IntObjPredicate<? super E> matcher) {
        return matchIndex(loopBackward(untilMatching(), matcher));
    }

    public int forEachWhile(final IntObjPredicate<? super E> predicate) {
        return loopCount(loopForward(whileMatching(), predicate));
    }

    private <T> int loopForward(final LoopCondition<E, T> condition, final T attendant) {
        final int nBlocks = blocks.length();
        int index = 0;
        int block = 0;
        AtomicReferenceArray<E> data = blocks.get(block);
        while (data != null) {
            final int n = data.length();
            for (int i = 0; i < n; i++) {
                if (condition.abortLoop(index, data.get(i), attendant)) {
                    return index;
                }
                index++;
            }
            block++;
            data = block < nBlocks ? blocks.get(block) : null;
        }
        return -(index + 1);
    }

    private <T> int loopBackward(final LoopCondition<E, T> condition, final T attendant) {
        //find last block
        final int nBlocks = blocks.length();
        int block;
        AtomicReferenceArray<E> data = null;
        for (block = 0; block < nBlocks; block++) {
            final AtomicReferenceArray<E> dataBlock = blocks.get(block);
            if (dataBlock == null) {
                block--;
                break;
            }
            data = dataBlock;
        }
        //iterate from last block
        final int length = length(block);
        int index = length;
        while (data != null) {
            final int n = data.length();
            for (int i = n - 1; i >= 0; i--) {
                index--;
                if (condition.abortLoop(index, data.get(i), attendant)) {
                    return index;
                }
            }
            block--;
            data = block >= 0 ? blocks.get(block) : null;
        }
        return -(length + 1);
    }

    @Override
    public String toString() {
        return "AtomicArray:length=" + length();
    }

    @FunctionalInterface
    private interface LoopCondition<E, T> {
        boolean abortLoop(int index, E value, T object);
    }

    private static int loopCount(final int loopResult) {
        return loopResult >= 0 ? loopResult : -(loopResult + 1);
    }

    private static int matchIndex(final int loopResult) {
        return loopResult >= 0 ? loopResult : -1;
    }

    /**
     * Version of {@link #matchIndex(int)} for the case where the next non-existent null value would be a match.
     * Only possible when forward-looping and e.g. matching against null.
     */
    private <T> int matchIndex(final int loopResult, final LoopCondition<E, T> loopCondition, final T loopAttendant) {
        final int matchIndex = matchIndex(loopResult);
        if (matchIndex >= 0) {
            return matchIndex;
        }
        final int nextIndex = -(loopResult + 1);
        if (loopCondition.abortLoop(nextIndex, null, loopAttendant)) {
            return nextIndex;
        }
        return -1;
    }

    private static <E> LoopCondition<E, E> untilEqual() {
        return (index, value, attendant) -> Objects.equals(value, attendant);
    }

    private static <E> LoopCondition<E, IntObjPredicate<? super E>> untilMatching() {
        return (index, value, object) -> object.test(index, value);
    }

    private static <E> LoopCondition<E, IntObjPredicate<? super E>> whileMatching() {
        return (index, value, predicate) -> !predicate.test(index, value);
    }

    private static <E> LoopCondition<E, Consumer<? super E>> consumerForEach() {
        return (index, value, consumer) -> {
            consumer.accept(value);
            return false;
        };
    }

    private static <E> LoopCondition<E, IntObjConsumer<? super E>> intObjConsumerForEach() {
        return (index, value, consumer) -> {
            consumer.accept(index, value);
            return false;
        };
    }

}
