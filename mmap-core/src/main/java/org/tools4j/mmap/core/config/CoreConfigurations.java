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
     * Name of the system property to override the default error listener, with {@value #LOGGING_LISTENER},
     * {@value #NO_OP_LISTENER} or the class name of an {@link ErrorListener} with a public no-arg constructor.
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
     *
     * @return the listener given by system property {@value #ERROR_LISTENER_PROPERTY} if set, and by
     *         {@value #ERROR_LISTENER_DEFAULT} otherwise
     * @throws IllegalArgumentException if the system property value is neither a listener name nor the name of an
     *                                  error listener class with a public no-arg constructor
     */
    public static ErrorListener defaultErrorListener() {
        return namedOrClassInstanceProperty(ERROR_LISTENER_PROPERTY, ErrorListener.class, ERROR_LISTENER_DEFAULT,
                CoreConfigurations::errorListenerForName, ERROR_LISTENER_INSTANCE);
    }

    /**
     * Returns the error listener for a name accepted by the error listener properties of all modules.
     *
     * @param name the name, {@value #LOGGING_LISTENER} or {@value #NO_OP_LISTENER}
     * @return the listener for the name, or null if it is not the name of a listener
     */
    public static ErrorListener errorListenerForName(final String name) {
        if (LOGGING_LISTENER.equals(name)) {
            return LoggingErrorListener.INSTANCE;
        }
        if (NO_OP_LISTENER.equals(name)) {
            return ErrorListener.NO_OP;
        }
        return null;
    }
}
