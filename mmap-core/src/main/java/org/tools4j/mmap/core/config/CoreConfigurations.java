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
package org.tools4j.mmap.core.config;

import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.buffer.MemoryAccessMode;
import org.tools4j.mmap.core.internal.SystemProperties.PropertyInstance;
import org.tools4j.mmap.core.logging.LoggingErrorListener;

import java.util.concurrent.atomic.AtomicReference;

import static org.tools4j.mmap.core.internal.SystemProperties.enumProperty;
import static org.tools4j.mmap.core.internal.SystemProperties.namedOrClassInstanceProperty;

/**
 * Defaults of mmap-core configured by system properties: the memory access mode of all buffers, and the error
 * listener, which the other modules use unless their own error listener property is set.
 */
public enum CoreConfigurations {
    ;

    /**
     * Name of the system property choosing the {@link MemoryAccessMode} of all buffers, read once per JVM when the
     * first buffer is created.
     */
    public static final String MEMORY_ACCESS_PROPERTY = "mmap.core.memoryAccess";
    /** The default memory access mode, used if system property {@value #MEMORY_ACCESS_PROPERTY} is not set. */
    public static final MemoryAccessMode MEMORY_ACCESS_DEFAULT = MemoryAccessMode.UNSAFE;

    /** Error listener property value for a listener ignoring all errors and warnings. */
    public static final String NO_OP_LISTENER = "NO_OP";
    /**
     * Error listener property value for a listener logging errors and warnings to SLF4J if present on the class path,
     * and to java.util.logging otherwise, see {@link LoggingErrorListener}.
     */
    public static final String LOGGING_LISTENER = "LOGGING";
    /**
     * Error listener property value for a listener logging errors and warnings to {@link System#err}, see
     * {@link LoggingErrorListener#SYSTEM_ERR}; also used if the property value is invalid.
     */
    public static final String SYSTEM_ERR_LISTENER = "SYSTEM_ERR";
    /**
     * Name of the system property to override the default error listener, with {@value #LOGGING_LISTENER},
     * {@value #SYSTEM_ERR_LISTENER}, {@value #NO_OP_LISTENER} or the class name of an {@link ErrorListener} with a
     * public no-arg constructor.
     */
    public static final String ERROR_LISTENER_PROPERTY = "mmap.core.errorListener";
    /** The default error listener, used if system property {@value #ERROR_LISTENER_PROPERTY} is not set. */
    public static final String ERROR_LISTENER_DEFAULT = LOGGING_LISTENER;

    private static final AtomicReference<PropertyInstance> ERROR_LISTENER_INSTANCE = new AtomicReference<>();

    /**
     * Returns the memory access mode of all buffers.
     *
     * @return the mode given by system property {@value #MEMORY_ACCESS_PROPERTY} if set, and
     *         {@link #MEMORY_ACCESS_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is not the name of a memory access mode
     */
    public static MemoryAccessMode defaultMemoryAccessMode() {
        return enumProperty(MEMORY_ACCESS_PROPERTY, MemoryAccessMode.class, MEMORY_ACCESS_DEFAULT);
    }

    /**
     * Returns the default error listener, which the other modules use unless their own error listener property is set.
     * A listener given by class name is created once and reused while the property value is unchanged.
     * <p>
     * An invalid property value, neither a listener name nor the name of an error listener class with a public no-arg
     * constructor, doesn't throw, since errors must still be reported somewhere, often by callers that cannot throw:
     * errors and warnings then go to {@link System#err}, starting with the error about the property value, reported
     * once per value.
     *
     * @return the listener given by system property {@value #ERROR_LISTENER_PROPERTY} if set and valid,
     *         {@value #SYSTEM_ERR_LISTENER} if set but invalid, and {@value #ERROR_LISTENER_DEFAULT} if not set
     */
    public static ErrorListener defaultErrorListener() {
        try {
            return namedOrClassInstanceProperty(ERROR_LISTENER_PROPERTY, ErrorListener.class, ERROR_LISTENER_DEFAULT,
                    CoreConfigurations::errorListenerForName, ERROR_LISTENER_INSTANCE);
        } catch (final IllegalArgumentException e) {
            return systemErrForInvalidErrorListener(e);
        }
    }

    /**
     * Returns the System.err listener, used for the invalid property value from now on, and reports the value once:
     * the thread publishing the fallback for the value reports it.
     */
    private static ErrorListener systemErrForInvalidErrorListener(final IllegalArgumentException invalid) {
        final String value = System.getProperty(ERROR_LISTENER_PROPERTY, ERROR_LISTENER_DEFAULT);
        final PropertyInstance current = ERROR_LISTENER_INSTANCE.get();
        final boolean published = current != null && current.value().equals(value);
        if (!published && ERROR_LISTENER_INSTANCE.compareAndSet(current,
                new PropertyInstance(value, LoggingErrorListener.SYSTEM_ERR))) {
            LoggingErrorListener.SYSTEM_ERR.onError(ErrorListenerProperty.INSTANCE,
                    "Errors and warnings go to System.err because of an invalid value", invalid);
        }
        return LoggingErrorListener.SYSTEM_ERR;
    }

    /** The source of the error about an invalid error listener property value. */
    private enum ErrorListenerProperty implements Describable {
        INSTANCE;

        @Override
        public StringBuilder describeTo(final StringBuilder sb) {
            return sb.append("system property ").append(ERROR_LISTENER_PROPERTY);
        }
    }

    /**
     * Returns the error listener for a name accepted by the error listener properties of all modules.
     *
     * @param name the name, {@value #LOGGING_LISTENER}, {@value #SYSTEM_ERR_LISTENER} or {@value #NO_OP_LISTENER}
     * @return the listener for the name, or null if it is not the name of a listener
     */
    public static ErrorListener errorListenerForName(final String name) {
        if (LOGGING_LISTENER.equals(name)) {
            return LoggingErrorListener.INSTANCE;
        }
        if (SYSTEM_ERR_LISTENER.equals(name)) {
            return LoggingErrorListener.SYSTEM_ERR;
        }
        if (NO_OP_LISTENER.equals(name)) {
            return ErrorListener.NO_OP;
        }
        return null;
    }
}
