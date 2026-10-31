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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit test for {@link ErrorLogThrottle}.
 */
class ErrorLogThrottleTest {
    private static final long INTERVAL = 1000;
    private static final String FORMAT = "Error at {} of {}";
    private static final String REPEATED_FORMAT = FORMAT + " [{} repetitions since last logged]";

    private final Logger logger = mock(Logger.class);
    private long time;
    private ErrorLogThrottle throttle;

    @BeforeEach
    void setUp() {
        time = 0;
        throttle = new ErrorLogThrottle(logger, () -> time, INTERVAL);
    }

    private static RuntimeException error(final String message) {
        return new RuntimeException(message);
    }

    /** Verifies the varargs overload, which a call with two arguments would not select */
    private static Object[] args(final Object... args) {
        return args;
    }

    /** Same class, message and stack trace as the previous invocation, but a different instance */
    private static RuntimeException[] sameErrors(final int count) {
        final RuntimeException[] errors = new RuntimeException[count];
        for (int i = 0; i < count; i++) {
            errors[i] = error("same");
        }
        return errors;
    }

    @Test
    void repeatedErrorIsLoggedOnceUntilIntervalHasPassed() {
        final RuntimeException[] errors = sameErrors(4);

        throttle.error(FORMAT, 7L, "q", errors[0]);
        time = INTERVAL - 1;
        throttle.error(FORMAT, 7L, "q", errors[1]);
        throttle.error(FORMAT, 7L, "q", errors[2]);
        verify(logger).error(FORMAT, 7L, "q", errors[0]);
        verifyNoMoreInteractions(logger);

        time = INTERVAL;
        throttle.error(FORMAT, 7L, "q", errors[3]);
        verify(logger).error(REPEATED_FORMAT, 7L, "q", 3L, errors[0]);
        verifyNoMoreInteractions(logger);
    }

    @Test
    void errorWithDifferentArgumentIsLogged() {
        final RuntimeException[] errors = sameErrors(2);

        throttle.error(FORMAT, 7L, "q", errors[0]);
        throttle.error(FORMAT, 8L, "q", errors[1]);

        verify(logger).error(FORMAT, 7L, "q", errors[0]);
        verify(logger).error(FORMAT, 8L, "q", errors[1]);
    }

    @Test
    void errorWithDifferentThrowableIsLogged() {
        final RuntimeException first = error("first");
        final RuntimeException otherMessage = error("second");
        final IllegalStateException otherClass = new IllegalStateException("first");
        final RuntimeException otherCause = new RuntimeException("first", error("cause"));

        throttle.error(FORMAT, 7L, "q", first);
        throttle.error(FORMAT, 7L, "q", otherMessage);
        throttle.error(FORMAT, 7L, "q", otherClass);
        throttle.error(FORMAT, 7L, "q", otherCause);

        verify(logger).error(FORMAT, 7L, "q", first);
        verify(logger).error(FORMAT, 7L, "q", otherMessage);
        verify(logger).error(FORMAT, 7L, "q", otherClass);
        verify(logger).error(FORMAT, 7L, "q", otherCause);
    }

    @Test
    void errorWithDifferentStackTraceIsLogged() {
        final RuntimeException first = error("same");
        final RuntimeException otherStackTrace = error("same");
        otherStackTrace.setStackTrace(new StackTraceElement[0]);

        throttle.error(FORMAT, 7L, "q", first);
        throttle.error(FORMAT, 7L, "q", otherStackTrace);

        verify(logger).error(FORMAT, 7L, "q", first);
        verify(logger).error(FORMAT, 7L, "q", otherStackTrace);
    }

    @Test
    void alternatingErrorsAreEachLoggedOnce() {
        for (int i = 0; i < 10; i++) {
            throttle.error(FORMAT, 1L, "q");
            throttle.error(FORMAT, 2L, "q");
        }

        verify(logger).error(FORMAT, args(1L, "q"));
        verify(logger).error(FORMAT, args(2L, "q"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void evictedErrorLogsItsRepetitions() {
        throttle.error(FORMAT, 0L, "q");
        throttle.error(FORMAT, 0L, "q");
        for (long i = 1; i < ErrorLogThrottle.RECENT_ERRORS; i++) {
            throttle.error(FORMAT, i, "q");
        }
        for (long i = 0; i < ErrorLogThrottle.RECENT_ERRORS; i++) {
            verify(logger).error(FORMAT, args(i, "q"));
        }
        verifyNoMoreInteractions(logger);

        throttle.error(FORMAT, (long) ErrorLogThrottle.RECENT_ERRORS, "q");

        verify(logger).error(REPEATED_FORMAT, 0L, "q", 1L);
        verify(logger).error(FORMAT, args((long) ErrorLogThrottle.RECENT_ERRORS, "q"));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void equalThrowablesComparesCauseChain() {
        final RuntimeException[] causes = sameErrors(2);
        final RuntimeException a = new RuntimeException("x", causes[0]);
        final RuntimeException b = new RuntimeException("x", causes[1]);
        b.setStackTrace(a.getStackTrace());

        assertThat(ErrorLogThrottle.equalThrowables(a, b)).isTrue();
        assertThat(ErrorLogThrottle.equalThrowables(a, new RuntimeException("x"))).isFalse();
        assertThat(ErrorLogThrottle.equalThrowables(a, null)).isFalse();
    }
}
