/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hover-raft (tools4j), Anton Anufriev, Marco Terzer
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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.queue.api.Enumerator;
import org.tools4j.mmap.region.api.RegionAccessor;

import java.util.Objects;

public class MappedEnumerator implements Enumerator {
    private final static int LENGTH_SIZE = 4;
    private final RegionAccessor regionAccessor;
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer();
    private final UnsafeBuffer messageBuffer = new UnsafeBuffer();

    private long position = 0;
    private int nextLength = 0;

    public MappedEnumerator(final RegionAccessor regionAccessor) {
        this.regionAccessor = Objects.requireNonNull(regionAccessor);
    }

    @Override
    public boolean hasNextMessage() {
        if (regionAccessor.wrap(position, unsafeBuffer)) {
            final int length = unsafeBuffer.getIntVolatile(0);
            if (length > 0) {
                nextLength = length;
                return true;
            } else if (length < 0) {
                position += LENGTH_SIZE + -length;
                return hasNextMessage();
            }
        }
        return false;
    }

    @Override
    public DirectBuffer readNextMessage() {
        if (nextLength > 0) {
            messageBuffer.wrap(unsafeBuffer, LENGTH_SIZE, nextLength);
            position += LENGTH_SIZE + nextLength;
            nextLength = 0;
            return messageBuffer;
        } else {
            throw new IllegalStateException("Not ready to read message");
        }
    }

    @Override
    public Enumerator skipNextMessage() {
        if (nextLength > 0) {
            position += LENGTH_SIZE + nextLength;
            nextLength = 0;
        } else {
            throw new IllegalStateException("Not ready to read message");
        }
        return this;
    }

    @Override
    public void close() {
        regionAccessor.close();
    }
}
