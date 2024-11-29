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

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

public class AtomicArray<E> {
    public static final int DEFAULT_INITIAL_LENGTH = 64;
    private static final int MAX_BITS = Integer.SIZE - 1;

    private final int firstBlockLength;
    private final int firstBlockBits;
    private final AtomicReferenceArray<AtomicReferenceArray<E>> blocks;

    public AtomicArray() {
        this(DEFAULT_INITIAL_LENGTH);
    }

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
        final int len = blocks.length();
        for (int block = 0; block < len; block++) {
            final AtomicReferenceArray<E> data = blocks.get(block);
            if (data == null) {
                return block == 0 ? 0 : length(block - 1);
            }
            final int n = data.length();
            for (int i = 0; i < n; i++) {
                consumer.accept(data.get(i));
            }
        }
        return length(len - 1);
    }

    public int forEach(final IntObjConsumer<? super E> consumer) {
        final int len = blocks.length();
        int index = 0;
        for (int block = 0; block < len; block++) {
            final AtomicReferenceArray<E> data = blocks.get(block);
            if (data == null) {
                return index;
            }
            final int n = data.length();
            for (int i = 0; i < n; i++) {
                consumer.accept(index, data.get(i));
                index++;
            }
        }
        return length(len - 1);
    }

    public int forEachWhile(final IntObjPredicate<? super E> predicate) {
        final int len = blocks.length();
        int index = 0;
        for (int block = 0; block < len; block++) {
            final AtomicReferenceArray<E> data = blocks.get(block);
            if (data == null) {
                return index;
            }
            final int n = data.length();
            for (int i = 0; i < n; i++) {
                if (!predicate.test(index, data.get(i))) {
                    return index;
                }
                index++;
            }
        }
        return length(len - 1);
    }

    @Override
    public String toString() {
        return "AtomicArray:length=" + length();
    }
}
