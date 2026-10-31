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
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.unsafe.ConcurrentRollingFileMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RollingFileMapper;


/**
 * Lifecycle listener logging events to SLF4J if present on the class path, and to
 * {@link java.util.logging java.util.logging} otherwise, using a logger named after the resource class.
 * <p>
 * Events are logged at the level of the listener, except for events of internal resources (region mappers, and
 * rolling file mappers whose individual files are logged as well), which are always logged at debug level.
 */
public final class LoggingLifecycleListener implements LifecycleListener {
    /** Listener logging events at debug level */
    public static final LoggingLifecycleListener DEBUG = new LoggingLifecycleListener(LogTarget.Level.DEBUG);
    /** Listener logging events at info level */
    public static final LoggingLifecycleListener INFO = new LoggingLifecycleListener(LogTarget.Level.INFO);
    /** Listener logging events at warn level */
    public static final LoggingLifecycleListener WARN = new LoggingLifecycleListener(LogTarget.Level.WARN);

    private final LogTarget.Level level;

    private LoggingLifecycleListener(final LogTarget.Level level) {
        this.level = level;
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
        final LogTarget.Level level = isInternal(resource) ? LogTarget.Level.DEBUG : this.level;
        if (target.isEnabled(level)) {
            target.log(level, LogTarget.message(event, " ", resource, ""), null);
        }
    }

    private static boolean isInternal(final Describable resource) {
        return resource instanceof RegionMapper || resource instanceof RollingFileMapper ||
                resource instanceof ConcurrentRollingFileMapper;
    }

    @Override
    public String toString() {
        return "LoggingLifecycleListener:level=" + level;
    }
}
