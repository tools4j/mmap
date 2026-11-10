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
package org.tools4j.mmap.core.logging;

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;

import static org.tools4j.mmap.core.logging.LogTarget.Level.ERROR;
import static org.tools4j.mmap.core.logging.LogTarget.Level.WARN;

/**
 * Error listener logging errors and warnings to SLF4J if present on the class path, and to
 * {@link java.util.logging java.util.logging} otherwise, using a logger named after the source class.
 * <p>
 * Repetitions of recently logged errors are suppressed per thread, see {@link ErrorLogThrottle}.
 */
public final class LoggingErrorListener implements ErrorListener {
    /** The logging error listener, the only instance. */
    public static final LoggingErrorListener INSTANCE = new LoggingErrorListener();

    private final ThreadLocal<ErrorLogThrottle> throttle = ThreadLocal.withInitial(
            () -> new ErrorLogThrottle(LoggingErrorListener::logError));

    private LoggingErrorListener() {}

    @Override
    public void onError(final Describable source, final String message, final Throwable error) {
        throttle.get().error(source, message, error);
    }

    @Override
    public void onWarning(final Describable source, final CharSequence message) {
        final LogTarget target = LogTarget.forClass(source.getClass());
        if (target.isEnabled(WARN)) {
            target.log(WARN, LogTarget.message(message, ": ", source, ""), null);
        }
    }

    private static void logError(final Describable source,
                                 final String message,
                                 final Throwable error,
                                 final long repetitions) {
        final LogTarget target = LogTarget.forClass(source.getClass());
        if (target.isEnabled(ERROR)) {
            final String suffix = repetitions == 0 ? "" : " [" + repetitions + " repetitions since last logged]";
            target.log(ERROR, LogTarget.message(message, " in ", source, suffix), error);
        }
    }

    @Override
    public String toString() {
        return "LoggingErrorListener";
    }
}
