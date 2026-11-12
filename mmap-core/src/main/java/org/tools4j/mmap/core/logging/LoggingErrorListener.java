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

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.core.logging.LogTarget.Level.ERROR;
import static org.tools4j.mmap.core.logging.LogTarget.Level.WARN;

/**
 * Error listener logging errors and warnings with a logger named after the source class: {@link #INSTANCE} to the
 * {@link LogTarget}, i.e. SLF4J if present on the class path, java.util.logging otherwise, and {@link System#err} if
 * neither is present; {@link #SYSTEM_ERR} always to {@code System.err}.
 * <p>
 * Repetitions of recently logged errors are suppressed per thread, see {@link ErrorLogThrottle}.
 */
public final class LoggingErrorListener implements ErrorListener {
    /** The error listener logging to the {@link LogTarget}. */
    public static final LoggingErrorListener INSTANCE = new LoggingErrorListener(LogTargets.TARGETS::get,
            "LoggingErrorListener");
    /** The error listener logging to {@link System#err}, info and above. */
    public static final LoggingErrorListener SYSTEM_ERR = new LoggingErrorListener(LogTargets.SYSTEM_ERR_TARGETS::get,
            "LoggingErrorListener.SYSTEM_ERR");

    private final Function<Class<?>, LogTarget> targets;
    private final String name;
    private final ThreadLocal<ErrorLogThrottle> throttle = ThreadLocal.withInitial(
            () -> new ErrorLogThrottle(this::logError));

    private LoggingErrorListener(final Function<Class<?>, LogTarget> targets, final String name) {
        this.targets = requireNonNull(targets);
        this.name = requireNonNull(name);
    }

    @Override
    public void onError(final Describable source, final String message, final Throwable error) {
        throttle.get().error(source, message, error);
    }

    @Override
    public void onWarning(final Describable source, final CharSequence message) {
        final LogTarget target = targets.apply(source.getClass());
        if (target.isEnabled(WARN)) {
            target.log(WARN, LogTarget.message(message, ": ", source, ""), null);
        }
    }

    private void logError(final Describable source,
                          final String message,
                          final Throwable error,
                          final long repetitions) {
        final LogTarget target = targets.apply(source.getClass());
        if (target.isEnabled(ERROR)) {
            final String suffix = repetitions == 0 ? "" : " [" + repetitions + " repetitions since last logged]";
            target.log(ERROR, LogTarget.message(message, " in ", source, suffix), error);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
