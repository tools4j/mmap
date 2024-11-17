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
package org.tools4j.mmap.region.unsafe;

import org.agrona.UnsafeAccess;
import org.tools4j.mmap.region.api.AccessMode;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.impl.Constants.REGION_SIZE_GRANULARITY;

final class PreTouchHelper {

    private final AccessMode accessMode;
    private final AtomicLong touchedSize = new AtomicLong();

    PreTouchHelper(final AccessMode accessMode) {
        this.accessMode = requireNonNull(accessMode);
        reset();
    }

    void preTouch(final long position, final int length, final long address) {
        if (address == NULL_ADDRESS) {
            return;
        }
        final long end = position + length;
        long touched = touchedSize.get();
        if (end <= touched) {
            return;
        }
        while (!touchedSize.compareAndSet(touched, end)) {
            touched = touchedSize.get();
            if (end <= touched) {
                return;
            }
        }
        final sun.misc.Unsafe unsafe = UnsafeAccess.UNSAFE;
        for (long i = 0; i < length; i += REGION_SIZE_GRANULARITY) {
            unsafe.compareAndSwapLong(null, address + i, 0L, 0L);
        }
    }

    void reset() {
        //NOTE: for read-only access we do not need to pre-touch
        touchedSize.set(accessMode == AccessMode.READ_ONLY ? Long.MAX_VALUE : 0);
    }

}
