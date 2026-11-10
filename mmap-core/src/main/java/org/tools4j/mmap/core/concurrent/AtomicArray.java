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
package org.tools4j.mmap.core.concurrent;

import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * A lock free thread safe utility with array-like API similar to {@link AtomicReferenceArray} but its length can grow
 * dynamically as needed.
 *
 * @param <E> the element type
 */
public class AtomicArray<E> {
    /**
     * Consumer of an element and its index.
     *
     * @param <E> the element type
     */
    @FunctionalInterface
    public interface IndexedConsumer<E> {
        /**
         * Consumes an element.
         *
         * @param index   the index of the element
         * @param element the element
         */
        void accept(int index, E element);
    }

    /**
     * Predicate on an element and its index.
     *
     * @param <E> the element type
     */
    @FunctionalInterface
    public interface IndexedPredicate<E> {
        /**
         * Tests an element.
         *
         * @param index   the index of the element
         * @param element the element
         * @return true if the element matches
         */
        boolean test(int index, E element);
    }

    /**
     * Function of an element and its index.
     *
     * @param <E> the element type
     * @param <R> the result type
     */
    @FunctionalInterface
    public interface IndexedFunction<E, R> {
        /**
         * Applies the function to an element.
         *
         * @param index   the index of the element
         * @param element the element
         * @return the result
         */
        R apply(int index, E element);
    }

    /** The initial length used by the {@linkplain #AtomicArray() default constructor}. */
    public static final int DEFAULT_INITIAL_LENGTH = 64;
    private static final int MAX_BITS = Integer.SIZE - 1;

    private final int firstBlockLength;
    private final int firstBlockBits;
    /**
     * <pre>
     * NOTES:
     *    A. Block lengths
     *       (1) block[0] has initial length, which is a power of two
     *       (2) for i>0, appending a block[i] doubles the total capacity
     *
     *       from this it follows
     *         i) from (2) we get for i>0: length of block[i] must be equal to the sum of all block lengths before it
     *        ii) block[1] has same length as block[0]
     *       iii) block[i].length = 2*block[i-1].length for all i>1
     *        iv) all blocks have power of 2 lengths
     *         v) if n=#blocks, then the total capacity is initialCapacity * 2^(n-1)
     *        vi) for all i>=0, block[i].length = initialCapacity * 2^max(0, i-1)
     *
     *    B. Block nullability
     *       (1) block[0] is never null
     *       (2) block[i>0] can be null
     *       (3) if block[i] != null, then block[j] != null for all j <= i
     *
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
        if (initialLength <= 0 || (initialLength & (initialLength - 1)) != 0) {
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

    /**
     * Returns the length allocated so far, the initial length doubled once per allocated block; indices beyond it
     * read as null and allocate blocks when written.
     *
     * @return the allocated length
     */
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

    /**
     * Returns whether the block holding the given index is allocated.
     *
     * @param index the index, not negative
     * @return true if the index is allocated
     */
    public boolean isAllocated(final int index) {
        return blocks.get(blockIndex(index)) != null;
    }

    /**
     * Returns the element at the given index with volatile access, null if none was set or the index is not
     * allocated.
     *
     * @param index the index, not negative
     * @return the element, or null
     */
    public E get(final int index) {
        final int block = blockIndex(index);
        final AtomicReferenceArray<E> blockData = blocks.get(block);
        if (blockData == null) {
            return null;
        }
        final int offset = blockOffset(index, block);
        return blockData.get(offset);
    }

    /**
     * Returns the element at the given index with acquire access, null if none was set or the index is not
     * allocated.
     *
     * @param index the index, not negative
     * @return the element, or null
     */
    public E getAcquire(final int index) {
        final int block = blockIndex(index);
        final AtomicReferenceArray<E> blockData = blocks.getAcquire(block);
        if (blockData == null) {
            return null;
        }
        final int offset = blockOffset(index, block);
        return blockData.getAcquire(offset);
    }

    /**
     * Sets the element at the given index to a default value if it is null, allocating the index if necessary.
     *
     * @param index        the index, not negative
     * @param defaultValue the value to set if the element is null
     * @return the element now at the index: the one found, or the default value
     */
    public E setIfAbsent(final int index, final E defaultValue) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        final E value = blockData.get(offset);
        return value != null ? value : setIfAbsent(blockData, offset, defaultValue);
    }

    /**
     * Sets the element at the given index to a value created by a factory if it is null, allocating the index if
     * necessary; the factory may be invoked by several racing threads, and only one value is set.
     *
     * @param index        the index, not negative
     * @param valueFactory the factory creating the value from the index
     * @return the element now at the index: the one found, or the created value
     */
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

    /**
     * Replaces the element at the given index if it is not null.
     *
     * @param index    the index, not negative
     * @param newValue the value to replace the element with
     * @return true if the element was replaced, false if it was null
     */
    public boolean setIfPresent(final int index, final E newValue) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = blocks.get(block);
        return blockData != null && setIfPresent(blockData, offset, newValue);
    }

    /**
     * Atomically replaces the element at the given index, if it is not null, with the value computed from the index
     * and the element; the function may be invoked several times when other threads change the element.
     *
     * @param index            the index, not negative
     * @param valueAccumulator the function computing the new value from the index and the element
     * @return the new value, or null if the element was null
     */
    public E computeIfPresent(final int index, final IndexedFunction<? super E, ? extends E> valueAccumulator) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = blocks.get(block);
        return blockData == null ? null : computeIfPresent(blockData, index, offset, valueAccumulator);
    }

    private static <E> E setIfAbsent(final AtomicReferenceArray<E> block, final int offset, final E defaultValue) {
        E value;
        do {
            if (block.compareAndSet(offset, null, defaultValue)) {
                value = defaultValue;
            } else {
                value = block.get(offset);
            }
        } while (value == null && defaultValue != null);
        //NOTE: it should always be only 1 iteration, unless another thread wins the CAS, and then
        //      yet another thread sets the value back to null, which is highly unlikely
        //      BUT: we want to guarantee that non-null value is returned
        return value;
    }

    private static <E> boolean setIfPresent(final AtomicReferenceArray<E> block, final int offset, final E newValue) {
        return block.getAndAccumulate(offset, newValue, (vOld, vNew) -> vOld == null ? null : vNew) != null;
    }

    private static <E> E computeIfPresent(final AtomicReferenceArray<E> block, final int index, final int offset, final IndexedFunction<? super E, ? extends E> valueAccumulator) {
        E vOld = block.getAcquire(offset);
        while (vOld != null) {
            final E vNew = valueAccumulator.apply(index, vOld);
            final E witness = block.compareAndExchange(offset, vOld, vNew);
            if (witness == vOld) {
                return vNew;
            }
            vOld = witness;
        }
        return null;
    }

    /**
     * Sets the element at the given index with volatile access, allocating the index if necessary.
     *
     * @param index   the index, not negative
     * @param element the element, or null
     */
    public void set(final int index, final E element) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        blockData.set(offset, element);
    }

    /**
     * Atomically sets the element at the given index and returns the previous one, allocating the index if
     * necessary.
     *
     * @param index   the index, not negative
     * @param element the element, or null
     * @return the previous element, or null
     */
    public E getAndSet(final int index, final E element) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = getOrCreateBlock(block);
        return blockData.getAndSet(offset, element);
    }

    /**
     * Atomically sets the element at the given index to the update if it is the expected one, compared by identity;
     * allocates the index only if null is expected.
     *
     * @param index    the index, not negative
     * @param expected the expected element, or null
     * @param update   the element to set
     * @return true if the element was updated
     */
    public boolean compareAndSet(final int index, final E expected, final E update) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = expected == null ? getOrCreateBlock(block) : blocks.get(block);
        return blockData != null && blockData.compareAndSet(offset, expected, update);
    }

    /**
     * Atomically sets the element at the given index to the update if it is the expected one, compared by identity,
     * and returns the element found; allocates the index only if null is expected.
     *
     * @param index    the index, not negative
     * @param expected the expected element, or null
     * @param update   the element to set
     * @return the element found, the expected one if the element was updated
     */
    public E compareAndExchange(final int index, final E expected, final E update) {
        final int block = blockIndex(index);
        final int offset = blockOffset(index, block);
        final AtomicReferenceArray<E> blockData = expected == null ? getOrCreateBlock(block) : blocks.get(block);
        return blockData != null ? blockData.compareAndExchange(offset, expected, update) : null;
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

    /**
     * Passes every element up to the allocated length to a consumer, including nulls.
     *
     * @param consumer the consumer of the elements
     * @return the number of elements passed to the consumer
     */
    public int forEach(final Consumer<? super E> consumer) {
        return loopCount(loopForward(consumerForEach(), consumer));
    }

    /**
     * Passes every element up to the allocated length and its index to a consumer, including nulls.
     *
     * @param consumer the consumer of the indices and elements
     * @return the number of elements passed to the consumer
     */
    public int forEach(final IndexedConsumer<? super E> consumer) {
        return loopCount(loopForward(indexedConsumerForEach(), consumer));
    }

    /**
     * Returns the first index whose element equals the value; for null, this can be the allocated length, where the
     * next unallocated element would be null.
     *
     * @param value the value, or null
     * @return the first index of the value, or -1 if not found
     */
    public int indexOf(final E value) {
        final LoopCondition<E, E> untilEqual = untilEqual();
        return matchIndex(loopForward(untilEqual, value), untilEqual, value);
    }

    /**
     * Returns the first index whose element matches; if none does, the allocated length if a null element there
     * would match.
     *
     * @param matcher the predicate on index and element
     * @return the first matching index, or -1 if none
     */
    public int indexOf(final IndexedPredicate<? super E> matcher) {
        final LoopCondition<E, IndexedPredicate<? super E>> untilMatching = untilMatching();
        return matchIndex(loopForward(untilMatching, matcher), untilMatching, matcher);
    }

    /**
     * Returns the last allocated index whose element equals the value.
     *
     * @param value the value, or null
     * @return the last index of the value, or -1 if not found
     */
    public int lastIndexOf(final E value) {
        return matchIndex(loopBackward(untilEqual(), value));
    }

    /**
     * Returns the last allocated index whose element matches.
     *
     * @param matcher the predicate on index and element
     * @return the last matching index, or -1 if none
     */
    public int lastIndexOf(final IndexedPredicate<? super E> matcher) {
        return matchIndex(loopBackward(untilMatching(), matcher));
    }

    /**
     * Returns the first index of the smallest element up to the allocated length; the comparator also receives null
     * elements.
     *
     * @param comparator the comparator, accepting null
     * @return the first index of the smallest element
     */
    public int indexOfMin(final Comparator<? super E> comparator) {
        return indexOfComparison(comparator, cmp -> cmp < 0);
    }

    /**
     * Returns the last index of the smallest element up to the allocated length; the comparator also receives null
     * elements.
     *
     * @param comparator the comparator, accepting null
     * @return the last index of the smallest element
     */
    public int lastIndexOfMin(final Comparator<? super E> comparator) {
        return indexOfComparison(comparator, cmp -> cmp <= 0);
    }

    /**
     * Returns the first index of the largest element up to the allocated length; the comparator also receives null
     * elements.
     *
     * @param comparator the comparator, accepting null
     * @return the first index of the largest element
     */
    public int indexOfMax(final Comparator<? super E> comparator) {
        return indexOfComparison(comparator, cmp -> cmp > 0);
    }

    /**
     * Returns the last index of the largest element up to the allocated length; the comparator also receives null
     * elements.
     *
     * @param comparator the comparator, accepting null
     * @return the last index of the largest element
     */
    public int lastIndexOfMax(final Comparator<? super E> comparator) {
        return indexOfComparison(comparator, cmp -> cmp >= 0);
    }

    private int indexOfComparison(final Comparator<? super E> comparator, final IntPredicate matcher) {
        final int nBlocks = blocks.length();
        int index = 0;
        int bestIndex = -1;
        E bestValue = null;
        int block = 0;
        AtomicReferenceArray<E> data = blocks.get(block);
        while (data != null) {
            final int n = data.length();
            for (int i = 0; i < n; i++, index++) {
                final E value = data.get(i);
                if (bestIndex < 0 || matcher.test(comparator.compare(value, bestValue))) {
                    bestIndex = index;
                    bestValue = value;
                }
            }
            block++;
            data = block < nBlocks ? blocks.get(block) : null;
        }
        return bestIndex;
    }

    /**
     * Tests the elements up to the allocated length and their indices, including nulls, until the predicate fails.
     *
     * @param predicate the predicate on index and element
     * @return the number of elements for which the predicate held, the index of the first failing one
     */
    public int forEachWhile(final IndexedPredicate<? super E> predicate) {
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

    private static <E> LoopCondition<E, IndexedPredicate<? super E>> untilMatching() {
        return (index, value, predicate) -> predicate.test(index, value);
    }

    private static <E> LoopCondition<E, IndexedPredicate<? super E>> whileMatching() {
        return (index, value, predicate) -> !predicate.test(index, value);
    }

    private static <E> LoopCondition<E, Consumer<? super E>> consumerForEach() {
        return (index, value, consumer) -> {
            consumer.accept(value);
            return false;
        };
    }

    private static <E> LoopCondition<E, IndexedConsumer<? super E>> indexedConsumerForEach() {
        return (index, value, consumer) -> {
            consumer.accept(index, value);
            return false;
        };
    }
}
