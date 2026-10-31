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

import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.SystemEpochClock;
import org.tools4j.mmap.mapping.api.Describable;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Passes errors to an {@link ErrorLog}, but suppresses repetitions of recently logged errors so that an error
 * occurring on every invocation of a busy loop does not flood the log.
 * <p>
 * An error repeats a recent one if it has the same source and message, and a throwable with the same class, message,
 * stack trace and causes. A repeated error is logged again at most once per repeat interval, with the number of
 * repetitions since it was last logged.
 * <p>
 * <b>Note:</b> This class is <b>not</b> thread safe.
 */
final class ErrorLogThrottle {
    public static final long DEFAULT_REPEAT_INTERVAL_MILLIS = 10_000;
    static final int RECENT_ERRORS = 8;

    /**
     * Log for errors passed through the throttle.
     */
    @FunctionalInterface
    public interface ErrorLog {
        /**
         * Logs an error.
         *
         * @param source      the object where the error occurred
         * @param message     the error message
         * @param error       the error
         * @param repetitions zero when the error is logged for the first time, and otherwise the number of
         *                    repetitions since it was last logged
         */
        void log(Describable source, String message, Throwable error, long repetitions);
    }

    private final ErrorLog log;
    private final EpochClock clock;
    private final long repeatIntervalMillis;
    private final RecentError[] recentErrors = new RecentError[RECENT_ERRORS];
    private int nextIndex;

    private static final class RecentError {
        final Describable source;
        final String message;
        final Throwable error;
        long loggedTime;
        long repetitions;

        RecentError(final Describable source, final String message, final Throwable error, final long loggedTime) {
            this.source = source;
            this.message = message;
            this.error = error;
            this.loggedTime = loggedTime;
        }

        boolean matches(final Describable source, final String message, final Throwable error) {
            return this.source == source && this.message.equals(message) && equalThrowables(this.error, error);
        }
    }

    public ErrorLogThrottle(final ErrorLog log) {
        this(log, SystemEpochClock.INSTANCE, DEFAULT_REPEAT_INTERVAL_MILLIS);
    }

    public ErrorLogThrottle(final ErrorLog log, final EpochClock clock, final long repeatIntervalMillis) {
        this.log = requireNonNull(log);
        this.clock = requireNonNull(clock);
        this.repeatIntervalMillis = repeatIntervalMillis;
    }

    /**
     * Logs the error unless it repeats a recently logged error, see class documentation.
     *
     * @param source  the object where the error occurred
     * @param message the error message
     * @param error   the error
     */
    public void error(final Describable source, final String message, final Throwable error) {
        final long time = clock.time();
        final RecentError recent = find(source, message, error);
        if (recent == null) {
            final RecentError evicted = recentErrors[nextIndex];
            if (evicted != null && evicted.repetitions > 0) {
                logRepetitions(evicted);
            }
            recentErrors[nextIndex] = new RecentError(source, message, error, time);
            nextIndex = (nextIndex + 1) % RECENT_ERRORS;
            log.log(source, message, error, 0);
            return;
        }
        recent.repetitions++;
        if (time - recent.loggedTime >= repeatIntervalMillis) {
            logRepetitions(recent);
            recent.loggedTime = time;
        }
    }

    private void logRepetitions(final RecentError recent) {
        log.log(recent.source, recent.message, recent.error, recent.repetitions);
        recent.repetitions = 0;
    }

    private RecentError find(final Describable source, final String message, final Throwable error) {
        for (final RecentError recent : recentErrors) {
            if (recent != null && recent.matches(source, message, error)) {
                return recent;
            }
        }
        return null;
    }

    static boolean equalThrowables(Throwable a, Throwable b) {
        while (a != b) {
            if (a == null || b == null || a.getClass() != b.getClass() ||
                    !Objects.equals(a.getMessage(), b.getMessage()) ||
                    !Arrays.equals(a.getStackTrace(), b.getStackTrace())) {
                return false;
            }
            a = a.getCause();
            b = b.getCause();
        }
        return true;
    }
}
