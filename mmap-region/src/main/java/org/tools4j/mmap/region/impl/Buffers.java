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

import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.RegionMetrics;

enum Buffers {
    ;
    static int wrap(final DirectBuffer buffer,
                    final long address,
                    final long position,
                    final RegionMetrics regionMetrics) {
        return wrap(buffer, address, regionMetrics.regionOffset(position), regionMetrics.regionSize());
    }

    static int wrap(final DirectBuffer buffer,
                    final long address,
                    final int offset,
                    final int regionSize) {
        final int length = regionSize - offset;
        if (buffer != null) {
            buffer.wrap(address + offset, length);
        }
        return length;
    }

    static void unwrap(final DirectBuffer last, final long address, final int regionSize) {
        final long lastAddr = last.addressOffset();
        if (lastAddr >= address && lastAddr < address + regionSize) {
            //NOTE: we only unwrap if it was our region, could have been re-wrapped already
            last.wrap(0, 0);
        }
    }
}
