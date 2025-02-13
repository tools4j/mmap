/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.dictionary.impl;

import org.agrona.DirectBuffer;

import static java.lang.Long.rotateLeft;
import static java.nio.ByteOrder.LITTLE_ENDIAN;

/**
 * Contains fast static hash code functions.
 */
public enum Hash {
    ;
    private static final long PRIME64_1 = -7046029288634856825L; //11400714785074694791
    private static final long PRIME64_2 = -4417276706812531889L; //14029467366897019727
    private static final long PRIME64_3 = 1609587929392839161L;
    private static final long PRIME64_4 = -8796714831421723037L; //9650029242287828579
    private static final long PRIME64_5 = 2870177450012600261L;

    /**
     * Fast XXH64 has as per <a href="https://github.com/Cyan4973/xxHash">https://github.com/Cyan4973/xxHash</a>
     *
     * @param buffer buffer with data to hash
     * @param offset offset in buffer
     * @param length length of data
     * @return the XXH64 hash code for given data with zero seed
     */
    public static long xxHash64(final DirectBuffer buffer, final int offset, final int length) {
        return xxHash64(0L, buffer, offset, length);
    }

    /**
     * Fast XXH64 has as per <a href="https://github.com/Cyan4973/xxHash">https://github.com/Cyan4973/xxHash</a>
     *
     * @param seed   seed for hash code calculation
     * @param buffer buffer with data to hash
     * @param offset offset in buffer
     * @param length length of data
     * @return the XXH64 hash code for given seed and data
     */
    @SuppressWarnings("PointlessArithmeticExpression")
    public static long xxHash64(final long seed, final DirectBuffer buffer, final int offset, final int length) {
        final int end = offset + length;
        int off = offset;
        long h64;

        if (length >= 32) {
            final int limit = end - 32;
            long v1 = seed + PRIME64_1 + PRIME64_2;
            long v2 = seed + PRIME64_2;
            long v3 = seed + 0;
            long v4 = seed - PRIME64_1;
            do {
                v1 += buffer.getLong(off, LITTLE_ENDIAN) * PRIME64_2;
                v1 = rotateLeft(v1, 31);
                v1 *= PRIME64_1;
                off += 8;

                v2 += buffer.getLong(off, LITTLE_ENDIAN) * PRIME64_2;
                v2 = rotateLeft(v2, 31);
                v2 *= PRIME64_1;
                off += 8;

                v3 += buffer.getLong(off, LITTLE_ENDIAN) * PRIME64_2;
                v3 = rotateLeft(v3, 31);
                v3 *= PRIME64_1;
                off += 8;

                v4 += buffer.getLong(off, LITTLE_ENDIAN) * PRIME64_2;
                v4 = rotateLeft(v4, 31);
                v4 *= PRIME64_1;
                off += 8;
            } while (off <= limit);

            h64 = rotateLeft(v1, 1) + rotateLeft(v2, 7) + rotateLeft(v3, 12) + rotateLeft(v4, 18);

            v1 *= PRIME64_2; v1 = rotateLeft(v1, 31); v1 *= PRIME64_1; h64 ^= v1;
            h64 = h64 * PRIME64_1 + PRIME64_4;

            v2 *= PRIME64_2; v2 = rotateLeft(v2, 31); v2 *= PRIME64_1; h64 ^= v2;
            h64 = h64 * PRIME64_1 + PRIME64_4;

            v3 *= PRIME64_2; v3 = rotateLeft(v3, 31); v3 *= PRIME64_1; h64 ^= v3;
            h64 = h64 * PRIME64_1 + PRIME64_4;

            v4 *= PRIME64_2; v4 = rotateLeft(v4, 31); v4 *= PRIME64_1; h64 ^= v4;
            h64 = h64 * PRIME64_1 + PRIME64_4;
        } else {
            h64 = seed + PRIME64_5;
        }

        h64 += length;

        while (off <= end - 8) {
            long k1 = buffer.getLong(off, LITTLE_ENDIAN);
            k1 *= PRIME64_2; k1 = rotateLeft(k1, 31); k1 *= PRIME64_1; h64 ^= k1;
            h64 = rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4;
            off += 8;
        }

        if (off <= end - 4) {
            h64 ^= (buffer.getInt(off, LITTLE_ENDIAN) & 0xFFFFFFFFL) * PRIME64_1;
            h64 = rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3;
            off += 4;
        }

        while (off < end) {
            h64 ^= (buffer.getByte(off) & 0xFF) * PRIME64_5;
            h64 = rotateLeft(h64, 11) * PRIME64_1;
            ++off;
        }

        h64 ^= h64 >>> 33;
        h64 *= PRIME64_2;
        h64 ^= h64 >>> 29;
        h64 *= PRIME64_3;
        h64 ^= h64 >>> 32;

        return h64;
    }
}
