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

import org.tools4j.mmap.queue.Appender;

import java.nio.ByteBuffer;

/**
 * Message writer offers methods to write different value types for elements of a message.
 */
public interface MessageWriter {
    MessageWriter putBoolean(boolean value);
    MessageWriter putInt8(byte value);
    MessageWriter putInt8(int value);
    MessageWriter putInt16(short value);
    MessageWriter putInt16(int value);
    MessageWriter putInt32(int value);
    MessageWriter putInt64(long value);
    MessageWriter putFloat32(float value);
    MessageWriter putFloat64(double value);
    MessageWriter putCharAscii(char value);
    MessageWriter putChar(char value);
    MessageWriter putStringAscii(CharSequence value);
    MessageWriter putStringUtf8(CharSequence value);
    MessageWriter putString(CharSequence value);
    MessageWriter putBytes(byte[] source);
    MessageWriter putBytes(byte[] source, int sourceOffset, int length);
    MessageWriter putBytes(ByteBuffer source);
    MessageWriter putBytes(ByteBuffer source, int sourceOffset, int length);
    void finishWriteMessage();
}
