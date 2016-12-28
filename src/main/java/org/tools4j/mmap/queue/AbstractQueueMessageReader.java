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

abstract class AbstractQueueMessageReader extends AbstractUnsafeMessageReader {

    protected final MappedFilePointer ptr;
    protected long messageEndPosition = -1;
    private StringBuilder stringBuilder;

    public AbstractQueueMessageReader(final MappedFile file) {
        this.ptr = new MappedFilePointer(file, MappedRegion.Mode.READ_ONLY);
    }

    @Override
    protected long getAndIncrementAddress(final int add) {
        final long pos = ptr.ensureNotClosed().getPosition();
        if (pos + add <= messageEndPosition) {
            return ptr.getAndIncrementAddress(add, false);
        }
        throw new IllegalStateException("Attempt to read beyond message end: " + (pos + add) + " > " + messageEndPosition);
    }

    @Override
    public long remaining() {
        return messageEndPosition < 0 ? 0 : messageEndPosition - ptr.ensureNotClosed().getPosition();
    }

    @Override
    protected void bytes(final byte[] target, final int targetOffset, final int length) {
        if (targetOffset < 0 | targetOffset + length > target.length) {
            throw new IndexOutOfBoundsException(String.format("targetOffset=%d, length=%d, target.length=%d", targetOffset, length, target.length));
        }
        final long pos = ptr.ensureNotClosed().getPosition();
        if (pos + length > messageEndPosition) {
            throw new IllegalStateException("Attempt to read beyond message end: " + (pos + length) + " > " + messageEndPosition);
        }
        int done = 0;
        while (done < length) {
            final int copyLen = (int)Math.min(length - done, ptr.getBytesRemaining());
            UNSAFE.copyMemory(null, getAndIncrementAddress(copyLen), target, UnsafeAccess.ARRAY_BASE_OFFSET + targetOffset + done, copyLen);
            done += copyLen;
            ptr.getAndIncrementAddress(copyLen, true);
        }
    }

    @Override
    protected void byteBuffer(final ByteBuffer target, final int targetOffset, final int length) {
        if (targetOffset < 0 | targetOffset + length > target.capacity()) {
            throw new IndexOutOfBoundsException(String.format("targetOffset=%d, length=%d, target.capacity=%d", targetOffset, length, target.capacity()));
        }
        final long pos = ptr.ensureNotClosed().getPosition();
        if (pos + length > messageEndPosition) {
            throw new IllegalStateException("Attempt to read beyond message end: " + (pos + length) + " > " + messageEndPosition);
        }
        int done = 0;
        while (done < length) {
            final int copyLen = (int)Math.min(length - done, ptr.getBytesRemaining());
            UNSAFE.copyMemory(null, getAndIncrementAddress(copyLen), target, UnsafeAccess.ARRAY_BASE_OFFSET + targetOffset + done, copyLen);
            done += copyLen;
            ptr.getAndIncrementAddress(copyLen, true);
        }
    }

    @Override
    protected StringBuilder stringBuilder(final int capacity) {
        if (stringBuilder == null) {
            stringBuilder = new StringBuilder(Math.max(capacity, 256));
        }
        return stringBuilder;
    }

    protected void close() {
        ptr.close();
    }

}
