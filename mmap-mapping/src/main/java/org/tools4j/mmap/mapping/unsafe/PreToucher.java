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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.core.unsafe.Unsafe;
import org.tools4j.mmap.core.unsafe.UnsafeAccess;

import java.util.concurrent.atomic.AtomicLong;

import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;

@Unsafe
final class PreToucher {
    /** The OS page size; not the mapping granularity, which is larger on Windows (64k with 4k pages) */
    //TODO FFM: page size without Unsafe, e.g. by a downcall to sysconf / GetSystemInfo
    private static final int PAGE_SIZE = UnsafeAccess.pageSize();

    private final AtomicLong touchedSize = new AtomicLong();

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
        for (long i = 0; i < length; i += PAGE_SIZE) {
            //TODO FFM: touch through the MemoryAccess in use (getAndAddLong) instead of Unsafe
            UnsafeAccess.touchPage(address + i);
        }
    }

    void reset() {
        touchedSize.set(0);
    }

}
