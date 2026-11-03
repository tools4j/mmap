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
package org.tools4j.mmap.mapping.unsafe;

import org.agrona.BufferUtil;
import org.agrona.UnsafeApi;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.mapping.api.AccessMode;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test for {@link PreTouchHelper}.
 */
class PreTouchHelperTest {

    private static final int PAGE_SIZE = UnsafeApi.pageSize();
    private static final int PAGES = 4;
    private static final int LENGTH = PAGES * PAGE_SIZE;
    private static final long MAX_WAIT_MILLIS = 10_000;

    private final ByteBuffer memory = BufferUtil.allocateDirectAligned(LENGTH, PAGE_SIZE);
    private final long address = BufferUtil.address(memory);
    private final UnsafeBuffer buffer = new UnsafeBuffer(address, LENGTH);

    @Test
    void preTouchLeavesDataUnchanged() {
        for (int offset = 0; offset < LENGTH; offset += Long.BYTES) {
            //every other page has data, including the first word that is touched
            buffer.putLong(offset, (offset / PAGE_SIZE) % 2 == 0 ? offset + 1 : 0);
        }
        new PreTouchHelper(AccessMode.READ_WRITE).preTouch(0, LENGTH, address);
        for (int offset = 0; offset < LENGTH; offset += Long.BYTES) {
            assertEquals((offset / PAGE_SIZE) % 2 == 0 ? offset + 1 : 0, buffer.getLong(offset), "offset=" + offset);
        }
    }

    @Test
    void preTouchLosesNoValueWrittenConcurrently() throws InterruptedException {
        final int writes = 100_000;
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final Thread writer = new Thread(() -> {
            try {
                for (int value = 1; value <= writes; value++) {
                    for (int page = 0; page < PAGES; page++) {
                        buffer.putLongVolatile(page * PAGE_SIZE, value);
                    }
                }
            } catch (final Throwable t) {
                error.set(t);
            }
        }, "writer");
        final PreTouchHelper helper = new PreTouchHelper(AccessMode.READ_WRITE);
        writer.start();
        while (writer.isAlive()) {
            helper.reset();
            helper.preTouch(0, LENGTH, address);
        }
        writer.join(MAX_WAIT_MILLIS);
        assertFalse(writer.isAlive(), "writer still running");
        assertNull(error.get());
        for (int page = 0; page < PAGES; page++) {
            assertEquals(writes, buffer.getLongVolatile(page * PAGE_SIZE), "page=" + page);
        }
    }
}
