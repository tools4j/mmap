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

import org.tools4j.mmap.mapping.api.Describable;

/**
 * Log target used by the logging listeners, writing to SLF4J if present on the class path, and to
 * {@link java.util.logging java.util.logging} otherwise.
 */
interface LogTarget {
    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    boolean isEnabled(Level level);

    void log(Level level, String message, Throwable error);

    /**
     * Returns the log target named after the given class.
     *
     * @param type the class logging
     * @return the log target for the class
     */
    static LogTarget forClass(final Class<?> type) {
        return LogTargets.TARGETS.get(type);
    }

    /**
     * Returns the message consisting of text, separator, description and suffix, built with a string builder of the
     * calling thread.
     *
     * @param text        the message text
     * @param separator   the separator between text and description
     * @param describable the object to describe
     * @param suffix      the suffix
     * @return the message
     */
    static String message(final CharSequence text,
                          final String separator,
                          final Describable describable,
                          final String suffix) {
        final StringBuilder sb = LogTargets.BUILDER.get();
        sb.setLength(0);
        sb.append(text).append(separator);
        describable.describeTo(sb);
        return sb.append(suffix).toString();
    }
}

enum LogTargets {
    ;
    static final ThreadLocal<StringBuilder> BUILDER = ThreadLocal.withInitial(() -> new StringBuilder(256));
    static final ClassValue<LogTarget> TARGETS = new ClassValue<>() {
        @Override
        protected LogTarget computeValue(final Class<?> type) {
            return SLF4J_PRESENT ? Slf4jLogTarget.create(type) : JulLogTarget.create(type);
        }
    };
    private static final boolean SLF4J_PRESENT = isSlf4jPresent();

    private static boolean isSlf4jPresent() {
        try {
            Class.forName("org.slf4j.LoggerFactory", false, LogTargets.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
