/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.mapping.impl;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link EmptyBuffer}.
 */
class EmptyBufferTest {

    @Test
    void hasZeroCapacity() {
        assertEquals(0, EmptyBuffer.INSTANCE.capacity());
        assertEquals(0, new EmptyBuffer().capacity());
    }

    @Test
    void wrapIsUnsupported() {
        final EmptyBuffer buffer = new EmptyBuffer();
        final byte[] bytes = new byte[8];
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(8);
        final UnsafeBuffer directBuffer = new UnsafeBuffer(bytes);
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(bytes));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(bytes, 0, 4));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(byteBuffer));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(byteBuffer, 0, 4));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(directBuffer));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(directBuffer, 0, 4));
        assertThrows(UnsupportedOperationException.class, () -> buffer.wrap(directBuffer.addressOffset(), 4));
        assertEquals(0, buffer.capacity());
    }
}
