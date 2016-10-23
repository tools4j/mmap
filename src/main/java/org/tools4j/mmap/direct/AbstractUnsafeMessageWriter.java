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
package org.tools4j.mmap.direct;

abstract public class AbstractUnsafeMessageWriter extends AbstractMessageWriter {

    abstract protected long getAndIncrementAddress(final int len);

    @Override
    public MessageWriter putInt8(byte value) {
        UnsafeAccess.UNSAFE.putByte(null, getAndIncrementAddress(1), value);
        return this;
    }

    @Override
    public MessageWriter putInt16(final short value) {
        UnsafeAccess.UNSAFE.putShort(null, getAndIncrementAddress(2), value);
        return this;
    }

    @Override
    public MessageWriter putInt32(final int value) {
        UnsafeAccess.UNSAFE.putInt(null, getAndIncrementAddress(4), value);
        return this;
    }

    @Override
    public MessageWriter putInt64(final long value) {
        UnsafeAccess.UNSAFE.putLong(null, getAndIncrementAddress(8), value);
        return this;
    }

    @Override
    public MessageWriter putFloat32(final float value) {
        UnsafeAccess.UNSAFE.putFloat(null, getAndIncrementAddress(4), value);
        return this;
    }

    @Override
    public MessageWriter putFloat64(final double value) {
        UnsafeAccess.UNSAFE.putDouble(null, getAndIncrementAddress(8), value);
        return this;
    }

    @Override
    public MessageWriter putChar(final char value) {
        UnsafeAccess.UNSAFE.putChar(null, getAndIncrementAddress(2), value);
        return this;
    }

}
