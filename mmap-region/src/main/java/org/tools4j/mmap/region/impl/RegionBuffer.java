/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.nio.ByteBuffer;

/**
 * Unsafe buffer with wrap(..) methods overridden to throw {@link UnsupportedOperationException}.
 */
public final class RegionBuffer extends UnsafeBuffer {
    private static final byte[] EMPTY_ARRAY = {};

    /**
     * Constant for empty buffer.
     */
    public static final RegionBuffer EMPTY = new RegionBuffer();

    public RegionBuffer() {
        super(EMPTY_ARRAY);
    }

    @Override
    public void wrap(final byte[] buffer) {
        if (buffer == EMPTY_ARRAY) {
            //invocation from constructor
            return;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final byte[] buffer, final int offset, final int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final ByteBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final ByteBuffer buffer, final int offset, final int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final DirectBuffer buffer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void wrap(final long address, final int length) {
        throw new UnsupportedOperationException();
    }

    void wrapInternal(final long address, final int length) {
        super.wrap(address, length);
    }
    void unwrapInternal() {
        super.wrap(0, 0);
    }
}
