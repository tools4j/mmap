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
package org.tools4j.mmap.core.jmh;

/**
 * The benchmarked operations, shared by the benchmarks and by {@link #pollute(Implementation, MemoryKind, int)}, so
 * that polluting the profiles of these call sites with one memory kind affects the measurements with the other.
 */
public enum BufferOperations {
    ;
    private static final int POLLUTION_ROUNDS = 20_000;

    static long readLongs(final BenchBuffer buffer, final int size) {
        long sum = 0;
        for (int index = 0; index < size; index += Long.BYTES) {
            sum += buffer.getLong(index);
        }
        return sum;
    }

    static void writeLongs(final BenchBuffer buffer, final int size, final long value) {
        for (int index = 0; index < size; index += Long.BYTES) {
            buffer.putLong(index, value + index);
        }
    }

    static long readLongsAcquire(final BenchBuffer buffer, final int size) {
        long sum = 0;
        for (int index = 0; index < size; index += Long.BYTES) {
            sum += buffer.getLongAcquire(index);
        }
        return sum;
    }

    static void writeLongsRelease(final BenchBuffer buffer, final int size, final long value) {
        for (int index = 0; index < size; index += Long.BYTES) {
            buffer.putLongRelease(index, value + index);
        }
    }

    static void copy(final BenchBuffer target, final BenchBuffer source, final int size) {
        target.putBytes(0, source, 0, size);
    }

    static boolean increment(final BenchBuffer buffer) {
        final long value = buffer.getLongAcquire(0);
        return buffer.compareAndSetLong(0, value, value + 1);
    }

    static long wrapAndRead(final BenchBuffer buffer, final MemoryBlock block) {
        block.wrapInto(buffer);
        return buffer.getLong(0);
    }

    /**
     * Runs all operations with buffers of the given implementation and memory kind, so that their call sites have
     * seen this memory kind before the benchmark measures the other.
     *
     * @return a sum of the results, to be consumed so that the work is not eliminated
     */
    static long pollute(final Implementation implementation, final MemoryKind kind, final int size) {
        final boolean compareAndSet = implementation.supportsCompareAndSet(kind);
        final BenchBuffer buffer = implementation.create(kind);
        final BenchBuffer source = implementation.create(kind);
        final MemoryBlock block = MemoryBlock.allocate(kind, size);
        MemoryBlock.allocate(kind, size).wrapInto(source);
        long sum = 0;
        for (int round = 0; round < POLLUTION_ROUNDS; round++) {
            sum += wrapAndRead(buffer, block);
            writeLongs(buffer, size, round);
            sum += readLongs(buffer, size);
            writeLongsRelease(buffer, size, round);
            sum += readLongsAcquire(buffer, size);
            copy(buffer, source, size);
            if (compareAndSet) {
                sum += increment(buffer) ? 1 : 0;
            }
        }
        return sum;
    }

    /**
     * Checks that the buffers read what was written, so that a broken implementation fails instead of measuring.
     */
    static void verify(final BenchBuffer buffer, final BenchBuffer source, final int size,
                       final boolean compareAndSet) {
        writeLongs(buffer, size, 1);
        check(readLongs(buffer, size) == expectedSum(size, 1), "putLong/getLong");
        writeLongsRelease(buffer, size, 2);
        check(readLongsAcquire(buffer, size) == expectedSum(size, 2), "putLongRelease/getLongAcquire");
        writeLongs(source, size, 3);
        copy(buffer, source, size);
        check(readLongs(buffer, size) == expectedSum(size, 3), "putBytes");
        if (!compareAndSet) {
            return;
        }
        final long first = buffer.getLong(0);
        check(increment(buffer) && buffer.getLong(0) == first + 1, "compareAndSetLong");
        check(!buffer.compareAndSetLong(0, first, 0), "compareAndSetLong with unexpected value");
    }

    private static long expectedSum(final int size, final long value) {
        long sum = 0;
        for (int index = 0; index < size; index += Long.BYTES) {
            sum += value + index;
        }
        return sum;
    }

    private static void check(final boolean condition, final String operation) {
        if (!condition) {
            throw new IllegalStateException("Verification failed: " + operation);
        }
    }
}
