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
package org.tools4j.mmap.queue;

import org.tools4j.mmap.io.*;

import java.nio.ByteBuffer;

import static org.tools4j.mmap.io.UnsafeAccess.UNSAFE;

abstract class AbstractQueueMessageWriter extends AbstractUnsafeMessageWriter {

    private static final byte ZERO = 0;

    protected final MappedFilePointer ptr;
    protected long messageStartPosition = -1;

    public AbstractQueueMessageWriter(final MappedFile file) {
        this.ptr = new MappedFilePointer(file, MappedRegion.Mode.READ_WRITE);
    }

    @Override
    protected long getAndIncrementAddress(final int add) {
        if (messageStartPosition < 0) {
            throw new IllegalStateException("Message not started");
        }
        return ptr.ensureNotClosed().getAndIncrementAddress(add, true);
    }

    //POSTCONDITION: guaranteed that we can write a 8 byte msg len after padding
    protected void padMessageEnd() {
        final long pad = 8 - (int) (ptr.getPosition() & 0x7);
        if (pad < 8) {
            UnsafeAccess.UNSAFE.setMemory(null, ptr.getAndIncrementAddress(pad, false), pad, ZERO);
        }
        if (ptr.getBytesRemaining() < 8) {
            ptr.getAndIncrementAddress(8, true);
        }
    }

    @Override
    public MessageWriter putBytes(final byte[] source, final int sourceOffset, final int length) {
        if (sourceOffset < 0 | sourceOffset + length > source.length) {
            throw new IndexOutOfBoundsException(String.format("sourceOffset=%d, length=%d, source.length=%d", sourceOffset, length, source.length));
        }
        int done = 0;
        while (done < length) {
            final int copyLen = (int) Math.min(length - done, ptr.getBytesRemaining());
            UNSAFE.copyMemory(source, UnsafeAccess.ARRAY_BASE_OFFSET + sourceOffset + done, null, getAndIncrementAddress(copyLen), copyLen);
            done += copyLen;
            ptr.getAndIncrementAddress(copyLen, true);
        }
        return this;
    }

    @Override
    public MessageWriter putBytes(final ByteBuffer source, final int sourceOffset, final int length) {
        if (sourceOffset < 0 | sourceOffset + length > source.capacity()) {
            throw new IndexOutOfBoundsException(String.format("sourceOffset=%d, length=%d, source.capacity=%d", sourceOffset, length, source.capacity()));
        }
        final byte[] sourceArray = UnsafeAccess.array(source);
        final long sourceAddress = UnsafeAccess.address(source);
        int done = 0;
        while (done < length) {
            final int copyLen = (int) Math.min(length - done, ptr.getBytesRemaining());
            UNSAFE.copyMemory(sourceArray, sourceAddress + sourceOffset + done, null, getAndIncrementAddress(copyLen), copyLen);
            done += copyLen;
            ptr.getAndIncrementAddress(copyLen, true);
        }
        return this;
    }

    public void close() {
        if (!ptr.isClosed()) {
            if (messageStartPosition >= 0) {
                finishWriteMessage();
            }
            ptr.close();
        }
    }
}
