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
package org.tools4j.mmap.region.impl;

import org.agrona.DirectBuffer;

public enum DirectBuffers {
    ;
    public static boolean equals(final DirectBuffer buffer1,
                                 final int offset1,
                                 final DirectBuffer buffer2,
                                 final int offset2,
                                 final int length) {
        final int length8 = length & 0xfffffff8;
        for (int i = 0; i < length8; i += Long.BYTES) {
            if (buffer1.getLong(offset1 + i) != buffer2.getLong(offset2 + i)) {
                return false;
            }
        }
        int idx = length8;
        if (idx + Integer.BYTES <= length) {
            if (buffer1.getInt(offset1 + idx) != buffer2.getInt(offset2 + idx)) {
                return false;
            }
            idx += Integer.BYTES;
        }
        for (int i = idx; i < length; i++) {
            if (buffer1.getByte(offset1 + idx) != buffer2.getByte(offset2 + idx)) {
                return false;
            }
        }
        return true;
    }
}
