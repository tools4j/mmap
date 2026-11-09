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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * FFM access: direct memory through one global segment spanning all addresses, so wrapping allocates no segment,
 * heap memory through a byte array view var handle. Since Java 22 or so (supported on 21, not on 25), byte array views
 * support plain access only, as do byte array segments (alignment 1): ordered access is emulated with fences, and
 * compare-and-set is not available for heap memory without Unsafe.
 */
enum Ffm {
    ;
    static final MemorySegment ALL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
    static final VarHandle LONG = ValueLayout.JAVA_LONG.varHandle();
    static final VarHandle LONG_UNALIGNED = ValueLayout.JAVA_LONG_UNALIGNED.varHandle();
    static final VarHandle HEAP_LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    static long getLongAcquire(final byte[] array, final int index) {
        final long value = (long) HEAP_LONG.get(array, index);
        VarHandle.acquireFence();
        return value;
    }

    static void putLongRelease(final byte[] array, final int index, final long value) {
        VarHandle.releaseFence();
        HEAP_LONG.set(array, index, value);
    }

    static UnsupportedOperationException noHeapCompareAndSet() {
        return new UnsupportedOperationException("No compare-and-set for heap memory with FFM");
    }
}
