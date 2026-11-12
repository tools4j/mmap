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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.buffer.MemoryAccessMode;
import org.tools4j.mmap.core.logging.LoggingErrorListener;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link CoreConfigurations}.
 */
class CoreConfigurationsTest {

    /** Custom error listener, instantiated via the error listener property */
    public static final class CustomErrorListener implements ErrorListener {}

    @AfterEach
    void clearProperties() {
        System.clearProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY);
        System.clearProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY);
    }

    @Test
    void systemMemoryAccessModeDefaultsToUnsafe() {
        assertSame(MemoryAccessMode.UNSAFE, CoreConfigurations.systemMemoryAccessMode());
        System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, "FFM");
        assertSame(MemoryAccessMode.FFM, CoreConfigurations.systemMemoryAccessMode());
        System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, "ffm");
        assertThrows(IllegalArgumentException.class, CoreConfigurations::systemMemoryAccessMode);
    }

    @Test
    void errorListenerDefaultsToLogging() {
        assertSame(LoggingErrorListener.INSTANCE, CoreConfigurations.defaultErrorListener());
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CoreConfigurations.NO_OP_LISTENER);
        assertSame(ErrorListener.NO_OP, CoreConfigurations.defaultErrorListener());
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CoreConfigurations.SYSTEM_ERR_LISTENER);
        assertSame(LoggingErrorListener.SYSTEM_ERR, CoreConfigurations.defaultErrorListener());
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CoreConfigurations.LOGGING_LISTENER);
        assertSame(LoggingErrorListener.INSTANCE, CoreConfigurations.defaultErrorListener());
    }

    @Test
    void errorListenerPropertyWithClassNameCreatesOneInstancePerPropertyValue() {
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CustomErrorListener.class.getName());
        final ErrorListener listener = CoreConfigurations.defaultErrorListener();
        assertInstanceOf(CustomErrorListener.class, listener);
        assertSame(listener, CoreConfigurations.defaultErrorListener());
    }

    @Test
    void errorListenerPropertyWithInvalidValueFallsBackToSystemErrReportingTheValueOnce() {
        final PrintStream err = System.err;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
        try {
            System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, "no.such.Listener");
            assertSame(LoggingErrorListener.SYSTEM_ERR, CoreConfigurations.defaultErrorListener());
            assertSame(LoggingErrorListener.SYSTEM_ERR, CoreConfigurations.defaultErrorListener());
            System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, LifecycleListener.class.getName());
            assertSame(LoggingErrorListener.SYSTEM_ERR, CoreConfigurations.defaultErrorListener());
            System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CustomErrorListener.class.getName());
            assertInstanceOf(CustomErrorListener.class, CoreConfigurations.defaultErrorListener());
        } finally {
            System.setErr(err);
        }
        final String reported = output.toString(StandardCharsets.UTF_8);
        final String invalidValue = "Invalid value for system property: " +
                CoreConfigurations.ERROR_LISTENER_PROPERTY + "=";
        assertThat(reported)
                .containsOnlyOnce(invalidValue + "no.such.Listener")
                .containsOnlyOnce(invalidValue + LifecycleListener.class.getName())
                .contains("ERROR", "Errors and warnings go to System.err because of an invalid value in system " +
                        "property " + CoreConfigurations.ERROR_LISTENER_PROPERTY);
    }

    @Test
    void errorListenerForName() {
        assertSame(LoggingErrorListener.INSTANCE, CoreConfigurations.errorListenerForName("LOGGING"));
        assertSame(ErrorListener.NO_OP, CoreConfigurations.errorListenerForName("NO_OP"));
        assertSame(LoggingErrorListener.SYSTEM_ERR, CoreConfigurations.errorListenerForName("SYSTEM_ERR"));
        assertNull(CoreConfigurations.errorListenerForName("logging"));
        assertNull(CoreConfigurations.errorListenerForName(null));
    }
}
