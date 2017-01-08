/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017 mmap (tools4j), Marco Terzer
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
package org.tools4j.mmap.io;

import org.tools4j.mmap.io.MessageReader;
import org.tools4j.mmap.io.MessageWriter;

/**
 * Utility class dealing with unsigned ints.
 */
public class Compact {

    @SuppressWarnings("unused")
    private static final int SENTINEL_1 = 0x00;//0xxx xxxx
    private static final int SENTINEL_2 = 0x80;//10xx xxxx [8x]
    private static final int SENTINEL_4 = 0xc0;//110x xxxx [8x] [8x] [8x]
    private static final int SENTINEL_5 = 0xe0;//1110 xxxx [8x] [8x] [8x] [8x]
    private static final int SENTINEL_8 = 0xe0;//1110 xxxx [8x] [8x] [8x] [8x] [8x] [8x]
    private static final int SENTINEL_9 = 0xff;//1111 xxxx [8x] [8x] [8x] [8x] [8x] [8x] [8x]

    public static void putUnsignedInt(final int unsigned, final MessageWriter writer) {
        if (unsigned >= 0) {
            if (unsigned < (1<<7)) {
                writer.putInt8(unsigned);
                return;
            }
            if (unsigned < (1<<14)) {
                writer.putInt8(SENTINEL_2 | (unsigned >>> 8));
                writer.putInt8(unsigned & 0xff);
                return;
            }
            if (unsigned < (1<<29)) {
                writer.putInt8(SENTINEL_4 | (unsigned >>> 24));
                writer.putInt8((unsigned >>> 16) & 0xff);
                writer.putInt8((unsigned >>> 8) & 0xff);
                writer.putInt8(unsigned & 0xff);
                return;
            }
        }
        writer.putInt8(SENTINEL_5);
        writer.putInt32(unsigned);
    }

    public static void putUnsignedLong(final long unsigned, final MessageWriter writer) {
        if (unsigned >= 0) {
            if (unsigned < (1 << 29)) {
                putUnsignedInt((int) unsigned, writer);
                return;
            }
            if (unsigned < (1L<<60)) {
                writer.putInt8(SENTINEL_8 | (int)(unsigned >>> 56));
                writer.putInt8((int)((unsigned >>> 48) & 0xff));
                writer.putInt8((int)((unsigned >>> 40) & 0xff));
                writer.putInt8((int)((unsigned >>> 32) & 0xff));
                writer.putInt32((int)(unsigned & 0xffffffff));
                return;
            }
        }
        writer.putInt8(SENTINEL_9);
        writer.putInt64(unsigned);
    }

    public static int getUnsignedInt(final MessageReader reader) {
        final int int0 = reader.getInt8();
        return getUnsignedInt(reader, int0);
    }

    private static int getUnsignedInt(final MessageReader reader, final int int0) {
        if ((int0 & SENTINEL_2) != SENTINEL_2) {
            return int0;
        }
        final int int1 = reader.getInt8();
        if ((int0 & SENTINEL_4) != SENTINEL_4) {
            return ((int0 ^ SENTINEL_2) << 8) | int1;
        }
        final int int2 = reader.getInt8();
        final int int3 = reader.getInt8();
        if ((int0 & SENTINEL_5) != SENTINEL_5) {
            return ((int0 ^ SENTINEL_4) << 24) | (int1 << 16) | (int2 << 8) | int3;
        }
        final int int4 = reader.getInt8();
        return (int1 << 24) | (int2 << 16) | (int3 << 8) | int4;
    }

    public static long getUnsignedLong(final MessageReader reader) {
        final int int0 = reader.getInt8();
        if ((int0 & SENTINEL_8) != SENTINEL_8) {
            return getUnsignedInt(reader, int0);
        }
        if ((int0 & SENTINEL_9) != SENTINEL_9) {
            final long lint0 = int0 ^ SENTINEL_8;
            final long lint1 = reader.getInt8();
            final long lint2 = reader.getInt8();
            final long lint3 = reader.getInt8();
            final long lint4_7 = reader.getInt32() & 0xffffffffL;
            return (lint0 << 56) | (lint1 << 48) | (lint2 << 40) | (lint3 << 32) | lint4_7;
        }
        return reader.getInt64();
    }
}
