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

import org.junit.jupiter.api.Test;
import org.tools4j.mmap.core.base.Describable;
import org.tools4j.mmap.core.logging.LoggingErrorListener;
import org.tools4j.mmap.mapping.config.MappingConfigurations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Smoke test for {@link LoggingLifecycleListener} and {@link LoggingErrorListener}, the default listeners.
 */
class LoggingListenersTest {
    private static final Describable RESOURCE = sb -> sb.append("Resource:name=test");

    @Test
    void loggingListenersAreTheDefaults() {
        assertThat(MappingConfigurations.defaultLifecycleListener()).isSameAs(LoggingLifecycleListener.DEBUG);
        assertThat(MappingConfigurations.defaultErrorListener()).isSameAs(LoggingErrorListener.INSTANCE);
    }

    @Test
    void lifecycleEventsAreLogged() {
        assertThatCode(() -> {
            for (final LoggingLifecycleListener listener : new LoggingLifecycleListener[] {
                    LoggingLifecycleListener.DEBUG, LoggingLifecycleListener.INFO, LoggingLifecycleListener.WARN}) {
                listener.onCreated(RESOURCE);
                listener.onOpened(RESOURCE);
                listener.onClosed(RESOURCE);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void errorsAndWarningsAreLogged() {
        assertThatCode(() -> {
            final RuntimeException error = new RuntimeException("test error");
            for (int i = 0; i < 3; i++) {
                LoggingErrorListener.INSTANCE.onError(RESOURCE, "Test error", error);
            }
            LoggingErrorListener.INSTANCE.onWarning(RESOURCE, "Test warning");
        }).doesNotThrowAnyException();
    }
}
