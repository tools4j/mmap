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
package org.tools4j.mmap.core.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.tools4j.mmap.core.internal.SystemProperties.PropertyInstance;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit test for {@link SystemProperties}.
 */
class SystemPropertiesTest {

    private static final String PROPERTY = "mmap.test.systemPropertiesTest";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY);
    }

    @Test
    void namedOrClassInstancePropertyFallsBackToDefaultInstanceIfNotSet() {
        final AtomicReference<PropertyInstance> instance = new AtomicReference<>();
        assertEquals("default", SystemProperties.namedOrClassInstanceProperty(PROPERTY, CharSequence.class,
                () -> "default", name -> "named:" + name, instance));
        System.setProperty(PROPERTY, "value");
        assertEquals("named:value", SystemProperties.namedOrClassInstanceProperty(PROPERTY, CharSequence.class,
                () -> "default", name -> "named:" + name, instance));
        System.setProperty(PROPERTY, StringBuilder.class.getName());
        final CharSequence created = SystemProperties.namedOrClassInstanceProperty(PROPERTY, CharSequence.class,
                () -> "default", name -> null, instance);
        assertSame(StringBuilder.class, created.getClass());
        assertSame(created, SystemProperties.namedOrClassInstanceProperty(PROPERTY, CharSequence.class,
                () -> "default", name -> null, instance));
    }
}
