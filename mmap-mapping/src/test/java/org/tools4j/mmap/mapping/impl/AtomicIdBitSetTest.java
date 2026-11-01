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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link AtomicIdBitSet}; concurrent use is tested through the
 * appender ID pool of a queue.
 */
class AtomicIdBitSetTest {

    @Test
    void acquiresLowestFreeIdBelowMaxIds() {
        final AtomicIdBitSet ids = AtomicIdBitSet.allocate(130);
        for (int id = 0; id < 130; id++) {
            assertEquals(id, ids.acquire());
        }
        assertEquals(AtomicIdBitSet.NO_ID, ids.acquire());
        assertEquals(130, ids.acquired());
        assertTrue(ids.release(65));
        assertFalse(ids.release(65));
        assertEquals(65, ids.acquire());
        assertThrows(IllegalArgumentException.class, () -> ids.release(130));
        assertThrows(IllegalArgumentException.class, () -> ids.release(-1));
    }

    @Test
    void bitSetsWithDifferentMaxIdsShareBuffer() {
        final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(AtomicIdBitSet.bytes(100)));
        final AtomicIdBitSet small = new AtomicIdBitSet(buffer, 2);
        final AtomicIdBitSet large = new AtomicIdBitSet(buffer, 100);
        assertEquals(0, large.acquire());
        assertEquals(1, small.acquire());
        assertEquals(AtomicIdBitSet.NO_ID, small.acquire());
        assertEquals(2, large.acquire());
        assertEquals(3, small.acquired());
    }

    @Test
    void rejectsInvalidArguments() {
        assertEquals(0, AtomicIdBitSet.bytes(0));
        assertEquals(8, AtomicIdBitSet.bytes(64));
        assertEquals(16, AtomicIdBitSet.bytes(65));
        assertThrows(IllegalArgumentException.class, () -> AtomicIdBitSet.allocate(0));
        assertThrows(IllegalArgumentException.class, () -> new AtomicIdBitSet(
                new UnsafeBuffer(ByteBuffer.allocateDirect(8)), 65));
    }
}
