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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link Idler} and {@link IdleMode}, with the implementations {@link BackoffIdler} and
 * {@link StatelessIdler}.
 */
class IdlerTest {

    @Test
    void statelessIdlersAreShared() {
        assertSame(Idler.noOp(), IdleMode.NO_OP.newIdler());
        assertSame(Idler.busySpin(), IdleMode.BUSY_SPIN.newIdler());
        assertSame(Idler.yielding(), IdleMode.YIELDING.newIdler());
        Idler.noOp().idle();
        Idler.busySpin().idle();
        Idler.yielding().idle();
        Idler.busySpin().reset();
        assertEquals("NO_OP", Idler.noOp().toString());
        assertEquals("BUSY_SPIN", Idler.busySpin().toString());
        assertEquals("YIELDING", Idler.yielding().toString());
    }

    @Test
    void backoffIdlersAreNewInstances() {
        final Idler idler = IdleMode.BACKOFF.newIdler();
        assertInstanceOf(BackoffIdler.class, idler);
        assertNotSame(idler, IdleMode.BACKOFF.newIdler());
        assertNotSame(Idler.backoff(), Idler.backoff());
        assertEquals("BACKOFF:maxSpins=10|maxYields=5|minParkNanos=1000|maxParkNanos=1000000", idler.toString());
    }

    @Test
    void backoffSpinsThenYieldsThenParksForDoublingPeriodsUntilReset() {
        final BackoffIdler idler = (BackoffIdler) Idler.backoff(2, 3, 1_000, 5_000);
        for (int i = 0; i < 2 + 3; i++) {
            idler.idle();
            assertEquals(1_000, idler.parkNanos(), "spinning or yielding, idle " + i);
        }
        idler.idle();
        assertEquals(2_000, idler.parkNanos());
        idler.idle();
        assertEquals(4_000, idler.parkNanos());
        idler.idle();
        assertEquals(5_000, idler.parkNanos(), "capped");
        idler.idle();
        assertEquals(5_000, idler.parkNanos(), "capped");

        idler.reset();
        assertEquals(1_000, idler.parkNanos());
        for (int i = 0; i < 2 + 3; i++) {
            idler.idle();
        }
        assertEquals(1_000, idler.parkNanos(), "spinning and yielding again after reset");
    }

    @Test
    void backoffDoublingDoesNotOverflow() {
        final BackoffIdler idler = (BackoffIdler) Idler.backoff(0, 0, 1, Long.MAX_VALUE);
        //interrupted, parking returns immediately
        Thread.currentThread().interrupt();
        try {
            for (int i = 0; i < 70; i++) {
                idler.idle();
            }
        } finally {
            Thread.interrupted();
        }
        assertEquals(Long.MAX_VALUE, idler.parkNanos());
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
}
