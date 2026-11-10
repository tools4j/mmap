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

import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Log target writing to {@link java.util.logging java.util.logging}, used if SLF4J is not present on the class path.
 */
final class JulLogTarget implements LogTarget {
    private final Logger logger;

    private JulLogTarget(final Logger logger) {
        this.logger = requireNonNull(logger);
    }

    static LogTarget create(final Class<?> type) {
        return new JulLogTarget(Logger.getLogger(type.getName()));
    }

    private static java.util.logging.Level julLevel(final Level level) {
        return switch (level) {
            case DEBUG -> java.util.logging.Level.FINE;
            case INFO -> java.util.logging.Level.INFO;
            case WARN -> java.util.logging.Level.WARNING;
            case ERROR -> java.util.logging.Level.SEVERE;
        };
    }

    @Override
    public boolean isEnabled(final Level level) {
        return logger.isLoggable(julLevel(level));
    }

    @Override
    public void log(final Level level, final String message, final Throwable error) {
        logger.log(julLevel(level), message, error);
    }
}
