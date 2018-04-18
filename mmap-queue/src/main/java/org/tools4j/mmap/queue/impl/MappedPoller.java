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
import org.tools4j.mmap.queue.api.Poller;
import org.tools4j.mmap.region.api.RegionAccessor;

import java.util.Objects;

public class MappedPoller implements Poller {
    private final static int LENGTH_SIZE = 4;
    private final RegionAccessor regionAccessor;
    private final UnsafeBuffer unsafeBuffer = new UnsafeBuffer();

    private long position = 0;

    public MappedPoller(final RegionAccessor regionAccessor) {
        this.regionAccessor = Objects.requireNonNull(regionAccessor);
    }

    @Override
    public boolean poll(final DirectBuffer buffer) {
        if (regionAccessor.wrap(position, unsafeBuffer)) {
            final int length = unsafeBuffer.getIntVolatile(0);
            if (length > 0) {
                buffer.wrap(unsafeBuffer, LENGTH_SIZE, length);
                position += LENGTH_SIZE + length;
                return true;
            } else if (length < 0) {
                position += LENGTH_SIZE + -length;
                return poll(buffer);
            }
        }
        return false;
    }


    @Override
    public void close() {
        regionAccessor.close();
    }
}
