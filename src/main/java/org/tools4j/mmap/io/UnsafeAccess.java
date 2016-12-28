/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 mmap (tools4j), Marco Terzer
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

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/**
 * Access to {@link Unsafe}.
 */
public class UnsafeAccess {

    public static final Unsafe UNSAFE = initUnsafe();

    public static final long ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    private static final long BYTE_BUFFER_HB_FIELD_OFFSET;
    private static final long BYTE_BUFFER_OFFSET_FIELD_OFFSET;

    static {
        try {
            BYTE_BUFFER_HB_FIELD_OFFSET = UNSAFE.objectFieldOffset(ByteBuffer.class.getDeclaredField("hb"));
            BYTE_BUFFER_OFFSET_FIELD_OFFSET = UNSAFE.objectFieldOffset(ByteBuffer.class.getDeclaredField("offset"));
        } catch (final Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static byte[] array(final ByteBuffer buffer) {
        if (buffer.isDirect()) {
            return null;
        } else {
            return (byte[]) UNSAFE.getObject(buffer, BYTE_BUFFER_HB_FIELD_OFFSET);
        }
    }

    public static long address(final ByteBuffer buffer) {
        if (buffer.isDirect()) {
            return ((sun.nio.ch.DirectBuffer) buffer).address();
        } else {
            return ARRAY_BASE_OFFSET + UNSAFE.getInt(buffer, BYTE_BUFFER_OFFSET_FIELD_OFFSET);
        }
    }

    private static final Unsafe initUnsafe() {
        try {
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            final Unsafe unsafe = (Unsafe)field.get(null);
            field.setAccessible(false);
            return unsafe;
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialise Unsafe, e=" + e, e);
        }
    }
}
