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
package org.tools4j.mmap.core.idle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Idler} and {@link IdleMode}, with the implementations {@link BackoffIdler} and
 * {@link StatelessIdler}.
 */
class IdlerTest {

    @Test
    void idlersAreShared() {
        assertSame(Idler.noOp(), IdleMode.NO_OP.idler());
        assertSame(Idler.busySpin(), IdleMode.BUSY_SPIN.idler());
        assertSame(Idler.yielding(), IdleMode.YIELDING.idler());
        assertSame(Idler.backoff(), IdleMode.BACKOFF.idler());
        assertInstanceOf(BackoffIdler.class, Idler.backoff());
        assertEquals("NO_OP", Idler.noOp().toString());
        assertEquals("BUSY_SPIN", Idler.busySpin().toString());
        assertEquals("YIELDING", Idler.yielding().toString());
        assertEquals("BACKOFF:maxSpins=10|maxYields=5|minParkNanos=1000|maxParkNanos=1000000",
                Idler.backoff().toString());
    }

    @Test
    void idlersIncrementIdleCountSaturating() {
        for (final IdleMode mode : IdleMode.values()) {
            final Idler idler = mode.idler();
            assertEquals(1, idler.idle(0), mode.name());
            assertEquals(2, idler.idle(1), mode.name());
            interruptedSoParkingReturnsImmediately(() ->
                    assertEquals(Integer.MAX_VALUE, idler.idle(Integer.MAX_VALUE), mode.name()));
        }
    }

    @Test
    void backoffSpinsThenYieldsThenParksForDoublingPeriods() {
        final BackoffIdler idler = (BackoffIdler) Idler.backoff(2, 3, 1_000, 5_000);
        assertEquals(1_000, idler.parkNanos(0));
        assertEquals(2_000, idler.parkNanos(1));
        assertEquals(4_000, idler.parkNanos(2));
        assertEquals(5_000, idler.parkNanos(3), "capped");
        assertEquals(5_000, idler.parkNanos(Integer.MAX_VALUE), "capped");
        int idleCount = 0;
        for (int i = 0; i < 2 + 3; i++) {
            idleCount = idler.idle(idleCount);//spinning or yielding, without parking
        }
        assertEquals(5, idleCount);
    }

    @Test
    void backoffDoublingDoesNotOverflow() {
        final BackoffIdler idler = (BackoffIdler) Idler.backoff(0, 0, 1, Long.MAX_VALUE);
        assertEquals(1L << 62, idler.parkNanos(62));
        assertEquals(Long.MAX_VALUE, idler.parkNanos(63));
        assertEquals(Long.MAX_VALUE, idler.parkNanos(Integer.MAX_VALUE));
        final BackoffIdler fixed = (BackoffIdler) Idler.backoff(0, 0, 7, 7);
        assertEquals(7, fixed.parkNanos(0));
        assertEquals(7, fixed.parkNanos(100));
    }

    @Test
    void backoffRejectsInvalidLimits() {
        assertThrows(IllegalArgumentException.class, () -> Idler.backoff(-1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Idler.backoff(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Idler.backoff(0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Idler.backoff(0, 0, 2, 1));
    }

    @Test
    void idleModeForName() {
        for (final IdleMode mode : IdleMode.values()) {
            assertSame(mode, IdleMode.forName(mode.name()));
        }
        assertNull(IdleMode.forName("backoff"));
        assertNull(IdleMode.forName(null));
    }

    private static void interruptedSoParkingReturnsImmediately(final Runnable idle) {
        Thread.currentThread().interrupt();
        try {
            idle.run();
        } finally {
            assertTrue(Thread.interrupted(), "interrupt status kept");
        }
    }
}
