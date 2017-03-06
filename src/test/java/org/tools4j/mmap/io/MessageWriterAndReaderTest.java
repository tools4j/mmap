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

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MessageWriterAndReaderTest {

    private final Random rnd = new Random();

    //under test
    private AbstractMessageWriter messageWriter;
    private AbstractMessageReader messageReader;

    @Before
    public void beforeEach() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        messageWriter = new AbstractMessageWriter() {
            @Override
            public MessageWriter putInt8(final byte value) {
                out.write(value);
                return this;
            }

            @Override
            public MessageWriter putBytes(final byte[] source, final int sourceOffset, final int length) {
                Compact.putUnsignedInt(length, this);
                out.write(source, sourceOffset, length);
                return this;
            }

            @Override
            public MessageWriter putBytes(final ByteBuffer source, final int sourceOffset, final int length) {
                Compact.putUnsignedInt(length, this);
                source.position(sourceOffset);
                for (int i = 0; i < length; i++) {
                    putInt8(source.get());
                }
                return this;
            }

            @Override
            public void finishWriteMessage() {
                messageReader = new AbstractMessageReader() {
                    private int pos = 0;
                    private final byte[] buf = out.toByteArray();
                    @Override
                    protected void bytes(final byte[] target, final int targetOffset, final int length) {
                        if (buf.length - pos <= length) {
                            System.arraycopy(buf, pos, target, targetOffset, length);
                            pos += length;
                        } else {
                            throw new IllegalStateException("read after buffer end: " + (pos + length) + " > " + buf.length);
                        }
                    }

                    @Override
                    protected void byteBuffer(final ByteBuffer target, final int targetOffset, final int length) {
                        if (buf.length - pos <= length) {
                            target.position(targetOffset);
                            target.put(buf, pos, length);
                            pos += length;
                        } else {
                            throw new IllegalStateException("read after buffer end: " + (pos + length) + " > " + buf.length);
                        }
                    }

                    @Override
                    protected StringBuilder stringBuilder(final int capacity) {
                        return new StringBuilder(capacity);
                    }

                    @Override
                    public long remaining() {
                        return buf.length - pos;
                    }

                    @Override
                    public byte getInt8() {
                        if (pos < buf.length) {
                            return buf[pos++];
                        }
                        throw new IllegalStateException("read after buffer end: " + pos + " >= " + buf.length);
                    }

                    @Override
                    public void finishReadMessage() {
                        pos = 0;
                    }
                };
                out.reset();
            }
        };
    }

    @Test
    public void writerAndRead_boolean() throws Exception {
        final boolean[] universe = {false, true};
        for (final boolean valueIn : universe) {
            messageWriter.putBoolean(valueIn);
            messageWriter.finishWriteMessage();
            final boolean valueOut = messageReader.getBoolean();
            messageReader.finishReadMessage();
            assertEquals("Boolean should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writerAndRead_int8() throws Exception {
        for (byte valueIn = Byte.MIN_VALUE; valueIn < Byte.MAX_VALUE; valueIn++) {
            messageWriter.putInt8(valueIn);
            messageWriter.finishWriteMessage();
            final byte valueOut = messageReader.getInt8();
            messageReader.finishReadMessage();
            assertEquals("Int8 should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writerAndRead_int8AsInt() throws Exception {
        for (int value = 2*Byte.MIN_VALUE; value < 2*Byte.MAX_VALUE+1; value++) {
            final int valueIn = value & 0xff;
            messageWriter.putInt8(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt8AsInt();
            messageReader.finishReadMessage();
            assertEquals("Int8AsInt should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_Int16() throws Exception {
        for (short valueIn = Short.MIN_VALUE; valueIn < Short.MAX_VALUE; valueIn++) {
            messageWriter.putInt16(valueIn);
            messageWriter.finishWriteMessage();
            final short valueOut = messageReader.getInt16();
            messageReader.finishReadMessage();
            assertEquals("Int16 should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_Int16AsInt() throws Exception {
        for (int value = 2*Short.MIN_VALUE; value < 2*Short.MAX_VALUE+1; value++) {
            final int valueIn = value & 0xffff;
            messageWriter.putInt16(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt16AsInt();
            messageReader.finishReadMessage();
            assertEquals("Int16AsInt should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_Int32() throws Exception {
        for (int valueIn = 2*Short.MIN_VALUE; valueIn < 2*Short.MAX_VALUE+1; valueIn++) {
            messageWriter.putInt32(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt32();
            messageReader.finishReadMessage();
            assertEquals("Int32 should be equal after write/read", valueIn, valueOut);
        }
        final int[] specials = new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (final int valueIn : specials) {
            messageWriter.putInt32(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt32();
            messageReader.finishReadMessage();
            assertEquals("Int32 should be equal after write/read", valueIn, valueOut);
        }
        final int n = 10000;
        for (int i = 0; i < n; i++) {
            final int valueIn = rnd.nextInt();
            messageWriter.putInt32(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt32();
            messageReader.finishReadMessage();
            assertEquals("Int32 should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_Int64() throws Exception {
        for (int valueIn = 2*Short.MIN_VALUE; valueIn < 2*Short.MAX_VALUE+1; valueIn++) {
            messageWriter.putInt32(valueIn);
            messageWriter.finishWriteMessage();
            final int valueOut = messageReader.getInt32();
            messageReader.finishReadMessage();
            assertEquals("Int32 should be equal after write/read", valueIn, valueOut);
        }
        final long[] specials = new long[] {Long.MIN_VALUE, Long.MIN_VALUE + 1, Integer.MIN_VALUE - 1L, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (final long valueIn : specials) {
            messageWriter.putInt64(valueIn);
            messageWriter.finishWriteMessage();
            final long valueOut = messageReader.getInt64();
            messageReader.finishReadMessage();
            assertEquals("Int64 should be equal after write/read", valueIn, valueOut);
        }
        final int n = 10000;
        for (int i = 0; i < n; i++) {
            final long valueIn = rnd.nextLong();
            messageWriter.putInt64(valueIn);
            messageWriter.finishWriteMessage();
            final long valueOut = messageReader.getInt64();
            messageReader.finishReadMessage();
            assertEquals("Int64 should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_Float32() throws Exception {
        for (float valueIn = -100; valueIn < 100; valueIn += 0.01) {
            messageWriter.putFloat32(valueIn);
            messageWriter.finishWriteMessage();
            final float valueOut = messageReader.getFloat32();
            messageReader.finishReadMessage();
            assertEquals("Float32 should be equal after write/read", valueIn, valueOut, 0f);
        }
        final float[] specials = new float[] {Float.NaN, Float.NEGATIVE_INFINITY, -Float.MIN_NORMAL, -Float.MIN_VALUE, -0.0f, +0.0f, Float.MIN_VALUE, Float.MIN_NORMAL, Float.POSITIVE_INFINITY};
        for (final float valueIn : specials) {
            messageWriter.putFloat32(valueIn);
            messageWriter.finishWriteMessage();
            final float valueOut = messageReader.getFloat32();
            messageReader.finishReadMessage();
            assertEquals("Float32 should be equal after write/read", valueIn, valueOut, 0f);
        }
        final int n = 10000;
        for (int i = 0; i < n; i++) {
            final float valueIn = rnd.nextFloat();
            messageWriter.putFloat32(valueIn);
            messageWriter.finishWriteMessage();
            final float valueOut = messageReader.getFloat32();
            messageReader.finishReadMessage();
            assertEquals("Float32 should be equal after write/read", valueIn, valueOut, 0f);
        }
        for (int i = 0; i < n; i++) {
            final float valueIn = (float)rnd.nextGaussian();
            messageWriter.putFloat32(valueIn);
            messageWriter.finishWriteMessage();
            final float valueOut = messageReader.getFloat32();
            messageReader.finishReadMessage();
            assertEquals("Float32 should be equal after write/read", valueIn, valueOut, 0f);
        }
    }

    @Test
    public void writeAndRead_Float64() throws Exception {
        for (double valueIn = -100; valueIn < 100; valueIn += 0.01) {
            messageWriter.putFloat64(valueIn);
            messageWriter.finishWriteMessage();
            final double valueOut = messageReader.getFloat64();
            messageReader.finishReadMessage();
            assertEquals("Float64 should be equal after write/read", valueIn, valueOut, 0.0);
        }
        final double[] specials = new double[] {Double.NaN, Double.NEGATIVE_INFINITY, -Double.MIN_NORMAL, -Double.MIN_VALUE, -0.0f, +0.0f, Double.MIN_VALUE, Double.MIN_NORMAL, Double.POSITIVE_INFINITY};
        for (final double valueIn : specials) {
            messageWriter.putFloat64(valueIn);
            messageWriter.finishWriteMessage();
            final double valueOut = messageReader.getFloat64();
            messageReader.finishReadMessage();
            assertEquals("Float64 should be equal after write/read", valueIn, valueOut, 0.0);
        }
        final int n = 10000;
        for (int i = 0; i < n; i++) {
            final double valueIn = rnd.nextDouble();
            messageWriter.putFloat64(valueIn);
            messageWriter.finishWriteMessage();
            final double valueOut = messageReader.getFloat64();
            messageReader.finishReadMessage();
            assertEquals("Float64 should be equal after write/read", valueIn, valueOut, 0.0);
        }
        for (int i = 0; i < n; i++) {
            final double valueIn = rnd.nextGaussian();
            messageWriter.putFloat64(valueIn);
            messageWriter.finishWriteMessage();
            final double valueOut = messageReader.getFloat64();
            messageReader.finishReadMessage();
            assertEquals("Float64 should be equal after write/read", valueIn, valueOut, 0.0);
        }
    }

    @Test
    public void writeAndRead_Char() throws Exception {
        for (char valueIn = Character.MIN_VALUE; valueIn < Character.MAX_VALUE; valueIn++) {
            messageWriter.putChar(valueIn);
            messageWriter.finishWriteMessage();
            final char valueOut = messageReader.getChar();
            messageReader.finishReadMessage();
            assertEquals("Char should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_CharAscii() throws Exception {
        for (char value = Character.MIN_VALUE; value < Character.MAX_VALUE; value++) {
            final char valueIn = (char)(value & 0xff);
            messageWriter.putCharAscii(valueIn);
            messageWriter.finishWriteMessage();
            final char valueOut = messageReader.getCharAscii();
            messageReader.finishReadMessage();
            assertEquals("CharAscii should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_CharUtf8() throws Exception {
        for (char valueIn = Character.MIN_VALUE; valueIn < Character.MAX_VALUE; valueIn++) {
            messageWriter.putCharUtf8(valueIn);
            messageWriter.finishWriteMessage();
            final char valueOut = messageReader.getCharUtf8();
            messageReader.finishReadMessage();
            assertEquals("CharUtf8 should be equal after write/read", valueIn, valueOut);
        }
    }

    @Test
    public void writeAndRead_StringAscii() throws Exception {
        final String[] specials = new String[] {"", " ", "\n", "\t", "\r\n"};
        for (final String valueIn : specials) {
            messageWriter.putStringAscii(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getStringAscii();
            messageReader.finishReadMessage();
            assertEquals("StringAscii should be equal after write/read", valueIn, valueOut);
            final StringBuilder sb = messageReader.getStringAscii(new StringBuilder("PREFIX"));
            assertEquals("StringAscii should be equal after write/read", valueIn, sb.toString().substring("PREFIX".length()));
        }
        final int n = 1000;
        final StringBuilder sb = new StringBuilder(10000);
        for (int i = 0; i < n; i++) {
            final int len = (1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10));
            for (int j = 0; j < len; j++) {
                final char ch = (char)(0xff & (rnd.nextInt(256) - 128));
                sb.append(ch);
            }
            final String valueIn = sb.toString();
            sb.setLength(0);
            messageWriter.putStringAscii(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getStringAscii();
            messageReader.finishReadMessage();
            assertEquals("StringAscii should be equal after write/read", valueIn, valueOut);
            messageReader.getStringAscii(sb);
            assertEquals("StringAscii should be equal after write/read", valueIn, sb.toString());
            sb.setLength(0);
        }
    }

    @Test
    public void writeAndRead_StringUtf8() throws Exception {
        final String[] specials = new String[] {"", " ", "\n", "\t", "\r\n"};
        for (final String valueIn : specials) {
            messageWriter.putStringUtf8(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getStringUtf8();
            messageReader.finishReadMessage();
            assertEquals("StringUtf8 should be equal after write/read", valueIn, valueOut);
            final StringBuilder sb = messageReader.getStringUtf8(new StringBuilder("PREFIX"));
            assertEquals("StringUtf8 should be equal after write/read", valueIn, sb.toString().substring("PREFIX".length()));
        }
        final int n = 1000;
        final StringBuilder sb = new StringBuilder(10000);
        for (int i = 0; i < n; i++) {
            final int len = (1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10));
            for (int j = 0; j < len; j++) {
                final char ch = (char)rnd.nextInt();
                sb.append(ch);
            }
            final String valueIn = sb.toString();
            sb.setLength(0);
            messageWriter.putStringUtf8(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getStringUtf8();
            messageReader.finishReadMessage();
            assertEquals("StringUtf8 should be equal after write/read: ", valueIn, valueOut);
            messageReader.getStringUtf8(sb);
            assertEquals("StringUtf8 should be equal after write/read", valueIn, sb.toString());
            sb.setLength(0);
        }
    }

    @Test
    public void writeAndRead_String() throws Exception {
        final String[] specials = new String[] {"", " ", "\n", "\t", "\r\n"};
        for (final String valueIn : specials) {
            messageWriter.putString(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getString();
            messageReader.finishReadMessage();
            assertEquals("String should be equal after write/read", valueIn, valueOut);
            final StringBuilder sb = messageReader.getString(new StringBuilder("PREFIX"));
            assertEquals("String should be equal after write/read", valueIn, sb.toString().substring("PREFIX".length()));
        }
        final int n = 1000;
        final StringBuilder sb = new StringBuilder(10000);
        for (int i = 0; i < n; i++) {
            final int len = (1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10))*(1+rnd.nextInt(10));
            for (int j = 0; j < len; j++) {
                final char ch = (char)rnd.nextInt();
                sb.append(ch);
            }
            final String valueIn = sb.toString();
            sb.setLength(0);
            messageWriter.putString(valueIn);
            messageWriter.finishWriteMessage();
            final String valueOut = messageReader.getString();
            messageReader.finishReadMessage();
            assertEquals("String should be equal after write/read", valueIn, valueOut);
            messageReader.getString(sb);
            assertEquals("String should be equal after write/read", valueIn, sb.toString());
            sb.setLength(0);
        }
    }

    @Test
    public void writeAndRead_Bytes() throws Exception {
        final int n = 100;
        final byte[] valueIn = new byte[n];
        for (int i = 0; i < n; i++) {
            valueIn[i] = (byte)(rnd.nextInt() & 0xff);
        }
        messageWriter.putBytes(valueIn);
        messageWriter.finishWriteMessage();
        final byte[] valueOut = messageReader.getBytes();
        messageReader.finishReadMessage();
        assertArrayEquals("Bytes should be equal after write/read", valueIn, valueOut);
        Arrays.fill(valueOut, (byte)0);
        messageReader.getBytes(valueOut);
        messageReader.finishReadMessage();
        assertArrayEquals("Bytes should be equal after write/read", valueIn, valueOut);
        final byte[] valueOut2 = new byte[n + 2];
        messageReader.getBytes(valueOut2, 1, n + 1);
        messageReader.finishReadMessage();
        assertArrayEquals("Bytes should be equal after write/read", valueIn, Arrays.copyOfRange(valueOut2, 1, n+1));
        final ByteBuffer bufOut = ByteBuffer.allocate(n);
        messageReader.getBytes(bufOut, n + 2);
        messageReader.finishReadMessage();
        assertArrayEquals("Bytes should be equal after write/read", valueIn, bufOut.array());
        final ByteBuffer bufOut2 = ByteBuffer.allocate(n + 2);
        messageReader.getBytes(bufOut2, 1, n + 1);
        messageReader.finishReadMessage();
        assertArrayEquals("Bytes should be equal after write/read", valueIn, Arrays.copyOfRange(bufOut2.array(), 1, n+1));
    }


}