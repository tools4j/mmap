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
import org.tools4j.mmap.core.base.LifecycleListener;

/**
 * Lifecycle listener logging events at its level to SLF4J if present on the class path, and to
 * {@link java.util.logging java.util.logging} otherwise, using a logger named after the resource class.
 * <p>
 * To log some resources at another level, such as internal ones at debug level, combine two logging listeners with
 * {@link LifecycleListener#conditional(java.util.function.Predicate, LifecycleListener, LifecycleListener)}.
 */
public final class LoggingLifecycleListener implements LifecycleListener {
    /** Listener logging events at debug level */
    public static final LoggingLifecycleListener DEBUG = new LoggingLifecycleListener(LogTarget.Level.DEBUG);
    /** Listener logging events at info level */
    public static final LoggingLifecycleListener INFO = new LoggingLifecycleListener(LogTarget.Level.INFO);
    /** Listener logging events at warn level */
    public static final LoggingLifecycleListener WARN = new LoggingLifecycleListener(LogTarget.Level.WARN);
    /** Listener logging events at error level */
    public static final LoggingLifecycleListener ERROR = new LoggingLifecycleListener(LogTarget.Level.ERROR);

    private final LogTarget.Level level;

    private LoggingLifecycleListener(final LogTarget.Level level) {
        this.level = level;
    }

    /**
     * Returns the listener logging events at the given level.
     *
     * @param level the log level
     * @return the listener for the level, one of the constants of this class
     */
    public static LoggingLifecycleListener forLevel(final LogTarget.Level level) {
        switch (level) {
            case DEBUG:
                return DEBUG;
            case INFO:
                return INFO;
            case WARN:
                return WARN;
            default:
                return ERROR;
        }
    }

    @Override
    public void onCreated(final Describable resource) {
        log("Created", resource);
    }

    @Override
    public void onOpened(final Describable resource) {
        log("Opened", resource);
    }

    @Override
    public void onClosed(final Describable resource) {
        log("Closed", resource);
    }

    private void log(final String event, final Describable resource) {
        final LogTarget target = LogTarget.forClass(resource.getClass());
        if (target.isEnabled(level)) {
            target.log(level, LogTarget.message(event, " ", resource, ""), null);
        }
    }

    @Override
    public String toString() {
        return "LoggingLifecycleListener:level=" + level;
    }
}
