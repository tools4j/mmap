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
 * The buffer implementations compared by the benchmarks.
 */
public enum Implementation {
    /** Agrona's UnsafeBuffer: one class, every access with the byte array (null for direct memory) as base object */
    AGRONA {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return new AgronaBuffer();
        }
    },
    /** Unsafe with one class per memory kind: a constant null base for direct memory, the array for heap memory */
    UNSAFE_SPLIT {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return kind == MemoryKind.DIRECT ? new UnsafeDirectBuffer() : new UnsafeHeapBuffer();
        }
    },
    /** Unsafe with one class branching per access between the array as base and a constant null base */
    UNSAFE_BRANCH {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return new UnsafeBranchBuffer();
        }
    },
    /** FFM with one class branching per access between a byte array view var handle and the global segment */
    FFM_BRANCH {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return new FfmBranchBuffer();
        }
    },
    /** FFM with one class per memory kind: the global segment for direct memory, a byte array view for heap memory */
    FFM_SPLIT {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return kind == MemoryKind.DIRECT ? new FfmDirectBuffer() : new FfmHeapBuffer();
        }
    },
    /** Diagnostic only, not run by default: FFM with a segment allocated per wrap, see {@link FfmSegmentBuffer} */
    FFM_SEGMENT {
        @Override
        BenchBuffer create(final MemoryKind kind) {
            return kind == MemoryKind.DIRECT ? new FfmSegmentBuffer() : new FfmHeapBuffer();
        }
    };

    abstract BenchBuffer create(MemoryKind kind);

    boolean supportsCompareAndSet(final MemoryKind kind) {
        return kind == MemoryKind.DIRECT || this == AGRONA || this == UNSAFE_SPLIT || this == UNSAFE_BRANCH;
    }
}
