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

import org.tools4j.mmap.queue.UInts;

abstract public class AbstractMessageWriter<T> implements MessageWriter<T> {

    public MessageWriter putBoolean(final boolean value) {
        return putInt8(value ? (byte)1 : (byte)0);
    }

    public MessageWriter putInt8(final int value) {
        return putInt8((byte)(value & 0xff));
    }

    public MessageWriter putInt16(final short value) {
        putInt8((byte)(value >>> 8));
        return putInt8((byte)(value & 0xff));
    }

    public MessageWriter putInt16(final int value) {
        return putInt16((short)(value & 0xffff));
    }

    public MessageWriter putInt32(final int value) {
        putInt8(value >>> 24);
        putInt8(value >>> 16);
        putInt8(value >>> 8);
        return putInt8(value);
    }

    public MessageWriter putInt64(final long value) {
        putInt32((int)(value >>> 32));
        return putInt32((int)(value & 0xffffffff));
    }

    public MessageWriter putFloat32(final float value) {
        return putInt32(Float.floatToIntBits(value));
    }

    public MessageWriter putFloat64(final double value) {
        return putInt64(Double.doubleToLongBits(value));
    }

    public MessageWriter putChar(final char value) {
        putInt8(value >>> 8);
        return putInt8(value);
    }

    @Override
    public MessageWriter putCharAscii(final char value) {
        return putInt8(value);
    }

    public MessageWriter putStringAscii(final CharSequence value) {
        final int len = value.length();
        UInts.writeUIntCompact(len, this);
        for (int i = 0; i < len; i++) {
            putCharAscii(value.charAt(i));
        }
        return this;
    }

    /**
     * {@link java.io.DataOutputStream#writeUTF(String, java.io.DataOutput)}
     */
    public MessageWriter putStringUtf8(final CharSequence value) {
        final int strlen = value.length();
        int utflen = 0;
        int count = 0;

        /* use charAt instead of copying String to char array */
        for (int i = 0; i < strlen; i++) {
            final char ch = value.charAt(i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                utflen++;
            } else if (ch > 0x07FF) {
                utflen += 3;
            } else {
                utflen += 2;
            }
        }
        UInts.writeUIntCompact(utflen, this);

        int i;
        for (i=0; i<strlen; i++) {
            final char ch = value.charAt(i);
            if (!((ch >= 0x0001) && (ch <= 0x007F))) break;
            putInt8(ch);
        }

        for (;i < strlen; i++){
            final char ch = value.charAt(i);
            if ((ch >= 0x0001) && (ch <= 0x007F)) {
                putInt8(ch);
            } else if (ch > 0x07FF) {
                putInt8(0xE0 | ((ch >> 12) & 0x0F));
                putInt8(0x80 | ((ch >>  6) & 0x3F));
                putInt8(0x80 | ((ch >>  0) & 0x3F));
            } else {
                putInt8(0xC0 | ((ch >>  6) & 0x1F));
                putInt8(0x80 | ((ch >>  0) & 0x3F));
            }
        }
        return this;
    }

    public MessageWriter putString(final CharSequence value) {
        final int len = value.length();
        UInts.writeUIntCompact(len, this);
        for (int i = 0; i < len; i++) {
            putChar(value.charAt(i));
        }
        return this;
    }
}
