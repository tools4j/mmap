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
    void memoryAccessModeDefaultsToUnsafe() {
        assertSame(MemoryAccessMode.UNSAFE, CoreConfigurations.defaultMemoryAccessMode());
        System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, "FFM");
        assertSame(MemoryAccessMode.FFM, CoreConfigurations.defaultMemoryAccessMode());
        System.setProperty(CoreConfigurations.MEMORY_ACCESS_PROPERTY, "ffm");
        assertThrows(IllegalArgumentException.class, CoreConfigurations::defaultMemoryAccessMode);
    }

    @Test
    void errorListenerDefaultsToLogging() {
        assertSame(LoggingErrorListener.INSTANCE, CoreConfigurations.defaultErrorListener());
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, CoreConfigurations.NO_OP_LISTENER);
        assertSame(ErrorListener.NO_OP, CoreConfigurations.defaultErrorListener());
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
    void errorListenerPropertyWithInvalidValueIsRejected() {
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, "no.such.Listener");
        assertThrows(IllegalArgumentException.class, CoreConfigurations::defaultErrorListener);
        System.setProperty(CoreConfigurations.ERROR_LISTENER_PROPERTY, LifecycleListener.class.getName());
        assertThrows(IllegalArgumentException.class, CoreConfigurations::defaultErrorListener);
    }

    @Test
    void errorListenerForName() {
        assertSame(LoggingErrorListener.INSTANCE, CoreConfigurations.errorListenerForName("LOGGING"));
        assertSame(ErrorListener.NO_OP, CoreConfigurations.errorListenerForName("NO_OP"));
        assertNull(CoreConfigurations.errorListenerForName("logging"));
        assertNull(CoreConfigurations.errorListenerForName(null));
    }
}
