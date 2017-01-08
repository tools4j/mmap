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

import java.nio.ByteBuffer;

abstract public class AbstractUnsafeMessageReader extends AbstractMessageReader {

    abstract protected long getAndIncrementAddress(int inc);

    @Override
    public byte getInt8() {
        return UnsafeAccess.UNSAFE.getByte(null, getAndIncrementAddress(1));
    }

    @Override
    public short getInt16() {
        return UnsafeAccess.UNSAFE.getShort(null, getAndIncrementAddress(2));
    }

    @Override
    public int getInt32() {
        return UnsafeAccess.UNSAFE.getInt(null, getAndIncrementAddress(4));
    }

    @Override
    public long getInt64() {
        return UnsafeAccess.UNSAFE.getLong(null, getAndIncrementAddress(8));
    }

    @Override
    public float getFloat32() {
        return UnsafeAccess.UNSAFE.getFloat(null, getAndIncrementAddress(4));
    }

    @Override
    public double getFloat64() {
        return UnsafeAccess.UNSAFE.getDouble(null, getAndIncrementAddress(8));
    }

    @Override
    public char getChar() {
        return UnsafeAccess.UNSAFE.getChar(null, getAndIncrementAddress(2));
    }

    @Override
    protected void bytes(final byte[] target, final int targetOffset, final int length) {
        if (targetOffset < 0 | targetOffset + length > target.length) {
            throw new IndexOutOfBoundsException(String.format("targetOffset=%d, length=%d, target.length=%d", targetOffset, length, target.length));
        }
        UnsafeAccess.UNSAFE.copyMemory(null, getAndIncrementAddress(length), target, UnsafeAccess.ARRAY_BASE_OFFSET + targetOffset, length);
    }

    @Override
    protected void byteBuffer(final ByteBuffer target, final int targetOffset, final int length) {
        if (targetOffset < 0 | targetOffset + length > target.capacity()) {
            throw new IndexOutOfBoundsException(String.format("targetOffset=%d, length=%d, target.capacity=%d", targetOffset, length, target.capacity()));
        }
        final byte[] targetArray = UnsafeAccess.array(target);
        final long targetAddress = UnsafeAccess.address(target);
        UnsafeAccess.UNSAFE.copyMemory(null, getAndIncrementAddress(length), targetArray, targetAddress + targetOffset, length);
    }

}
