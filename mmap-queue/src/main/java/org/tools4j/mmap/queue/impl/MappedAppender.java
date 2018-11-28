/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.mmap.queue.impl;

import java.util.Objects;

import org.agrona.BitUtil;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.region.api.AccessibleRegion;

public class MappedAppender implements Appender {
    private final static int LENGTH_SIZE = 4;
    private final AccessibleRegion accessibleRegion;
    private final int alignment;
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer();

    private long position = 0;

    public MappedAppender(final AccessibleRegion accessibleRegion, final int alignment) {
        this.accessibleRegion = Objects.requireNonNull(accessibleRegion);
        this.alignment = alignment;
        if (Integer.bitCount(alignment) > 1) throw new IllegalArgumentException("alignment " + alignment + " must be power of two");
        moveToLastAppendPosition();
    }

    private void moveToLastAppendPosition() {
        if (position == 0) {
            int length = 0;
            do {
                position += length;
                if (accessibleRegion.wrap(position, unsafeBuffer)) {
                    length = Math.abs(unsafeBuffer.getInt(0));
                }
            } while (length > 0);
        }
    }

    @Override
    public boolean append(final DirectBuffer buffer, final int offset, final int length) {
        final int messageLength = length + LENGTH_SIZE;

        final int paddedMessageLength = BitUtil.align(messageLength, alignment);

        if (paddedMessageLength > accessibleRegion.size()) {
            throw new IllegalStateException("Length is too big for accessibleRegion size: " +
                    paddedMessageLength + " > " + accessibleRegion.size());
        }

        if (accessibleRegion.wrap(position, unsafeBuffer)) {
            final int capacity = unsafeBuffer.capacity();

            if (capacity < paddedMessageLength) {
                unsafeBuffer.setMemory(LENGTH_SIZE, length, (byte) 0);
                unsafeBuffer.putIntOrdered(0, -(capacity - LENGTH_SIZE));
                position += capacity;
                append(buffer, offset, length);
            }

            buffer.getBytes(offset, unsafeBuffer, LENGTH_SIZE, length);

            unsafeBuffer.putIntOrdered(0, paddedMessageLength - LENGTH_SIZE);
            position += paddedMessageLength;
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (accessibleRegion instanceof AutoCloseable) {
            CloseHelper.quietClose((AutoCloseable) accessibleRegion);
        }
    }
}
