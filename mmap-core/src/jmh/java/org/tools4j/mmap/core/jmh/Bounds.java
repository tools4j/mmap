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

import java.util.Objects;

/**
 * Bounds checks of the benchmarked prototypes (not of Agrona or ByteBuffer, which do their own). By default with
 * {@link Objects#checkFromIndexSize(int, int, int)}, as Agrona does, or with {@link Objects#checkIndex(int, int)} if
 * system property {@value #CHECK_INDEX_PROPERTY} is true, as ByteBuffer does: an intrinsic, which C2 treats as a range
 * check that it can eliminate from loops.
 */
enum Bounds {
    ;
    static final String CHECK_INDEX_PROPERTY = "bench.checkIndex";
    private static final boolean CHECK_INDEX = Boolean.getBoolean(CHECK_INDEX_PROPERTY);

    static void checkLong(final int index, final int capacity) {
        if (CHECK_INDEX) {
            Objects.checkIndex(index, capacity - Long.BYTES + 1);
        } else {
            Objects.checkFromIndexSize(index, Long.BYTES, capacity);
        }
    }
}
