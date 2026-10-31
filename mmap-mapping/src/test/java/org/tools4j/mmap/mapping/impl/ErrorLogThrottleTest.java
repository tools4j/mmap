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
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.impl.ErrorLogThrottle.ErrorLog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit test for {@link ErrorLogThrottle}.
 */
class ErrorLogThrottleTest {
    private static final long INTERVAL = 1000;
    private static final String MESSAGE = "Test error";
    private static final Describable SOURCE = sb -> sb.append("source");

    private final ErrorLog log = mock(ErrorLog.class);
    private long time;
    private ErrorLogThrottle throttle;

    @BeforeEach
    void setUp() {
        time = 0;
        throttle = new ErrorLogThrottle(log, () -> time, INTERVAL);
    }

    private static RuntimeException error(final String message) {
        return new RuntimeException(message);
    }

    /** Same class, message and stack trace, but different instances */
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

        throttle.error(SOURCE, MESSAGE, errors[0]);
        time = INTERVAL - 1;
        throttle.error(SOURCE, MESSAGE, errors[1]);
        throttle.error(SOURCE, MESSAGE, errors[2]);
        verify(log).log(SOURCE, MESSAGE, errors[0], 0);
        verifyNoMoreInteractions(log);

        time = INTERVAL;
        throttle.error(SOURCE, MESSAGE, errors[3]);
        verify(log).log(SOURCE, MESSAGE, errors[0], 3);
        verifyNoMoreInteractions(log);
    }

    @Test
    void errorWithDifferentSourceOrMessageIsLogged() {
        final RuntimeException[] errors = sameErrors(3);
        final Describable otherSource = sb -> sb.append("other");

        throttle.error(SOURCE, MESSAGE, errors[0]);
        throttle.error(otherSource, MESSAGE, errors[1]);
        throttle.error(SOURCE, "Other error", errors[2]);

        verify(log).log(SOURCE, MESSAGE, errors[0], 0);
        verify(log).log(otherSource, MESSAGE, errors[1], 0);
        verify(log).log(SOURCE, "Other error", errors[2], 0);
    }

    @Test
    void errorWithDifferentThrowableIsLogged() {
        final RuntimeException first = error("first");
        final RuntimeException otherMessage = error("second");
        final IllegalStateException otherClass = new IllegalStateException("first");
        final RuntimeException otherCause = new RuntimeException("first", error("cause"));

        throttle.error(SOURCE, MESSAGE, first);
        throttle.error(SOURCE, MESSAGE, otherMessage);
        throttle.error(SOURCE, MESSAGE, otherClass);
        throttle.error(SOURCE, MESSAGE, otherCause);

        verify(log).log(SOURCE, MESSAGE, first, 0);
        verify(log).log(SOURCE, MESSAGE, otherMessage, 0);
        verify(log).log(SOURCE, MESSAGE, otherClass, 0);
        verify(log).log(SOURCE, MESSAGE, otherCause, 0);
    }

    @Test
    void errorWithDifferentStackTraceIsLogged() {
        final RuntimeException first = error("same");
        final RuntimeException otherStackTrace = error("same");
        otherStackTrace.setStackTrace(new StackTraceElement[0]);

        throttle.error(SOURCE, MESSAGE, first);
        throttle.error(SOURCE, MESSAGE, otherStackTrace);

        verify(log).log(SOURCE, MESSAGE, first, 0);
        verify(log).log(SOURCE, MESSAGE, otherStackTrace, 0);
    }

    @Test
    void alternatingErrorsAreEachLoggedOnce() {
        final RuntimeException a = error("a");
        final RuntimeException b = error("b");
        for (int i = 0; i < 10; i++) {
            throttle.error(SOURCE, MESSAGE, a);
            throttle.error(SOURCE, MESSAGE, b);
        }

        verify(log).log(SOURCE, MESSAGE, a, 0);
        verify(log).log(SOURCE, MESSAGE, b, 0);
        verifyNoMoreInteractions(log);
    }

    @Test
    void evictedErrorLogsItsRepetitions() {
        final RuntimeException[] errors = new RuntimeException[ErrorLogThrottle.RECENT_ERRORS + 1];
        for (int i = 0; i < errors.length; i++) {
            errors[i] = error("error-" + i);
        }
        throttle.error(SOURCE, MESSAGE, errors[0]);
        throttle.error(SOURCE, MESSAGE, errors[0]);
        for (int i = 1; i < ErrorLogThrottle.RECENT_ERRORS; i++) {
            throttle.error(SOURCE, MESSAGE, errors[i]);
        }
        for (int i = 0; i < ErrorLogThrottle.RECENT_ERRORS; i++) {
            verify(log).log(SOURCE, MESSAGE, errors[i], 0);
        }
        verifyNoMoreInteractions(log);

        throttle.error(SOURCE, MESSAGE, errors[ErrorLogThrottle.RECENT_ERRORS]);

        verify(log).log(SOURCE, MESSAGE, errors[0], 1);
        verify(log).log(SOURCE, MESSAGE, errors[ErrorLogThrottle.RECENT_ERRORS], 0);
        verifyNoMoreInteractions(log);
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
