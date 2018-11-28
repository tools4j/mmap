/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.mmap.region.api;

import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.agrona.collections.MutableLong;

public interface FileSizeEnsurer {
    FileSizeEnsurer NO_OP = minSize -> true;

    boolean ensureSize(long minSize);


    static FileSizeEnsurer unbounded(final LongSupplier fileSizeGetter,
                                     final LongConsumer fileSizeSetter) {
        return bounded(fileSizeGetter, fileSizeSetter, Long.MAX_VALUE);
    }

    static FileSizeEnsurer bounded(final LongSupplier fileSizeGetter,
                                   final LongConsumer fileSizeSetter,
                                   final long maxSize) {
        Objects.requireNonNull(fileSizeGetter);
        Objects.requireNonNull(fileSizeSetter);
        final MutableLong fileSize = new MutableLong(0);

        return minSize -> {
            if (fileSize.get() < minSize) {
                final long len = fileSizeGetter.getAsLong();
                if (len < minSize) {
                    if (minSize > maxSize) {
                        return false;
                    }
                    fileSizeSetter.accept(minSize);
                    fileSize.set(minSize);
                } else {
                    fileSize.set(len);
                }
            }
            return true;
        };
    }
}
