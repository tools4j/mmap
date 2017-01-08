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

import java.io.IOException;
import java.nio.ByteBuffer;

abstract public class AbstractMessageReader implements MessageReader {

    @Override
    public boolean getBoolean() {
        return getInt8() != 0;
    }

    @Override
    public int getInt8AsInt() {
        return 0xff & getInt8();
    }

    @Override
    public short getInt16() {
        return (short)((getInt8AsInt() << 8) | getInt8AsInt());
    }

    @Override
    public int getInt16AsInt() {
        return 0xffff & getInt16();
    }

    @Override
    public int getInt32() {
        return (getInt8AsInt() << 24) |
               (getInt8AsInt() << 16) |
               (getInt8AsInt() << 8)  |
                getInt8AsInt();
    }

    @Override
    public long getInt64() {
        return ((0xffffffffL & getInt32()) << 32) |
                (0xffffffffL & getInt32());
    }

    @Override
    public float getFloat32() {
        return Float.intBitsToFloat(getInt32());
    }

    @Override
    public double getFloat64() {
        return Double.longBitsToDouble(getInt64());
    }

    @Override
    public char getChar() {
        return (char)getInt16();
    }

    @Override
    public char getCharAscii() {
        return (char)getInt8();
    }

    @Override
    public String getStringAscii() {
        final int len = Compact.getUnsignedInt(this);
        final StringBuilder sb = stringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(getCharAscii());
        }
        return sb.toString();
    }

    @Override
    public <A extends Appendable> A getStringAscii(final A appendable) {
        try {
            final int len = Compact.getUnsignedInt(this);
            for (int i = 0; i < len; i++) {
                appendable.append(getCharAscii());
            }
            return appendable;
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getStringUtf8() {
        final int utflen = Compact.getUnsignedInt(this);
        return getStringUtf8(stringBuilder(utflen)).toString();
    }

    /**
     * {@link java.io.DataInputStream#readUTF(java.io.DataInput)}
     */
    @Override
    public <A extends Appendable> A getStringUtf8(final A appendable) {
        final int utflen = Compact.getUnsignedInt(this);
        return getStringUtf8(appendable, utflen);
    }

    private <A extends Appendable> A getStringUtf8(final A appendable, final int utflen) {
        try {
            int count = 0;

            int char1 = 0;
            while (count < utflen) {
                char1 = getInt8AsInt();
                if (char1 > 127) break;
                count++;
                appendable.append((char) char1);
            }

            while (true) {
                switch (char1 >> 4) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7: {
                        /* 0xxxxxxx*/
                        appendable.append((char) char1);
                        break;
                    }
                    case 12:
                    case 13: {
                        /* 110x xxxx   10xx xxxx*/
                        count += 2;
                        if (count > utflen)
                            throw new RuntimeException(
                                    "UTF malformed input: partial character at end");
                        final int char2 = getInt8AsInt();
                        if ((char2 & 0xC0) != 0x80)
                            throw new RuntimeException(
                                    "UTF malformed input around byte " + count);
                        appendable.append((char) (((char1 & 0x1F) << 6) | (char2 & 0x3F)));
                        break;
                    }
                    case 14: {
                        /* 1110 xxxx  10xx xxxx  10xx xxxx */
                        count += 3;
                        if (count > utflen)
                            throw new RuntimeException(
                                    "UTF malformed input: partial character at end");
                        final int char2 = getInt8AsInt();
                        final int char3 = getInt8AsInt();
                        if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                            throw new RuntimeException(
                                    "UTF malformed input around byte " + (count - 1));
                        appendable.append((char) (((char1 & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0)));
                        break;
                    }
                    default:
                        /* 10xx xxxx,  1111 xxxx */
                        throw new RuntimeException(
                                "UTF malformed input around byte " + count);
                }
                if (count < utflen) {
                    char1 = getInt8AsInt();
                } else {
                    break;
                }
            }
            // The number of chars produced may be less than utflen
            return appendable;
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String getString() {
        final int len = Compact.getUnsignedInt(this);
        final StringBuilder sb = stringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(getChar());
        }
        return sb.toString();
    }

    @Override
    public <A extends Appendable> A getString(final A appendable) {
        try {
            final int len = Compact.getUnsignedInt(this);
            for (int i = 0; i < len; i++) {
                appendable.append(getChar());
            }
            return appendable;
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        final int length = Compact.getUnsignedInt(this);
        final byte[] result = new byte[length];
        bytes(result, 0, length);
        return result;
    }

    @Override
    public int getBytes(final byte[] target) {
        return getBytes(target, 0, target.length);
    }

    @Override
    public int getBytes(final byte[] target, final int targetOffset, final int maxLength) {
        final int length = Compact.getUnsignedInt(this);
        final int readLen = Math.min(length, maxLength);
        bytes(target, targetOffset, readLen);
        return readLen;
    }

    @Override
    public int getBytes(final ByteBuffer target, final int maxLength) {
        return getBytes(target, target.position(), maxLength);
    }

    @Override
    public int getBytes(final ByteBuffer target, final int targetOffset, final int maxLength) {
        final int length = Compact.getUnsignedInt(this);
        final int readLen = Math.min(length, maxLength);
        byteBuffer(target, targetOffset, readLen);
        return readLen;
    }

    abstract protected void bytes(byte[] target, int targetOffset, int length);

    abstract protected void byteBuffer(ByteBuffer target, int targetOffset, int length);

    abstract protected StringBuilder stringBuilder(int capacity);
}
