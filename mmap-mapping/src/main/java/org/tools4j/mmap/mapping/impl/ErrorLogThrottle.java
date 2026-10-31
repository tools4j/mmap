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
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Logs errors like {@link Logger#error(String, Object...)}, but suppresses repetitions of recently logged errors so
 * that an error occurring on every invocation of a busy loop does not flood the log.
 * <p>
 * An error repeats a recent one if format and arguments are equal, where a throwable argument equals another if both
 * have the same class, message, stack trace and causes. A repeated error is logged again at most once per repeat
 * interval, with the number of repetitions since it was last logged.
 * <p>
 * <b>Note:</b> This class is <b>not</b> thread safe.
 */
public final class ErrorLogThrottle {
    public static final long DEFAULT_REPEAT_INTERVAL_MILLIS = 10_000;
    static final int RECENT_ERRORS = 8;
    private static final String REPETITIONS_SUFFIX = " [{} repetitions since last logged]";

    private final Logger logger;
    private final EpochClock clock;
    private final long repeatIntervalMillis;
    private final RecentError[] recentErrors = new RecentError[RECENT_ERRORS];
    private int nextIndex;

    private static final class RecentError {
        final String format;
        final Object[] arguments;
        long loggedTime;
        long repetitions;

        RecentError(final String format, final Object[] arguments, final long loggedTime) {
            this.format = format;
            this.arguments = arguments;
            this.loggedTime = loggedTime;
        }
    }

    public ErrorLogThrottle(final Logger logger) {
        this(logger, SystemEpochClock.INSTANCE, DEFAULT_REPEAT_INTERVAL_MILLIS);
    }

    public ErrorLogThrottle(final Logger logger, final EpochClock clock, final long repeatIntervalMillis) {
        this.logger = requireNonNull(logger);
        this.clock = requireNonNull(clock);
        this.repeatIntervalMillis = repeatIntervalMillis;
    }

    /**
     * Logs the error unless it repeats a recently logged error, see class documentation.
     *
     * @param format    the message format with {@code {}} placeholders
     * @param arguments the arguments, optionally followed by a throwable to log with its stack trace
     */
    public void error(final String format, final Object... arguments) {
        final long time = clock.time();
        final RecentError recent = find(format, arguments);
        if (recent == null) {
            final RecentError evicted = recentErrors[nextIndex];
            if (evicted != null && evicted.repetitions > 0) {
                logRepetitions(evicted);
            }
            recentErrors[nextIndex] = new RecentError(format, arguments, time);
            nextIndex = (nextIndex + 1) % RECENT_ERRORS;
            logger.error(format, arguments);
            return;
        }
        recent.repetitions++;
        if (time - recent.loggedTime >= repeatIntervalMillis) {
            logRepetitions(recent);
            recent.loggedTime = time;
        }
    }

    private void logRepetitions(final RecentError recent) {
        final Object[] arguments = recent.arguments;
        final int length = arguments.length;
        final Object[] withRepetitions = Arrays.copyOf(arguments, length + 1);
        if (length > 0 && arguments[length - 1] instanceof Throwable) {
            withRepetitions[length - 1] = recent.repetitions;
            withRepetitions[length] = arguments[length - 1];
        } else {
            withRepetitions[length] = recent.repetitions;
        }
        logger.error(recent.format + REPETITIONS_SUFFIX, withRepetitions);
        recent.repetitions = 0;
    }

    private RecentError find(final String format, final Object[] arguments) {
        for (final RecentError recent : recentErrors) {
            if (recent != null && recent.format.equals(format) && equalArguments(recent.arguments, arguments)) {
                return recent;
            }
        }
        return null;
    }

    private static boolean equalArguments(final Object[] a, final Object[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            final boolean equal = a[i] instanceof Throwable && b[i] instanceof Throwable
                    ? equalThrowables((Throwable) a[i], (Throwable) b[i])
                    : Objects.equals(a[i], b[i]);
            if (!equal) {
                return false;
            }
        }
        return true;
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
