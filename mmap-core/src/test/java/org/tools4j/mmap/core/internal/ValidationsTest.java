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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.tools4j.mmap.core.base.Closeable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.tools4j.mmap.core.internal.Validations.alreadyClosedException;
import static org.tools4j.mmap.core.internal.Validations.validateGreaterThanZero;
import static org.tools4j.mmap.core.internal.Validations.validateNonNegative;
import static org.tools4j.mmap.core.internal.Validations.validateNotClosed;
import static org.tools4j.mmap.core.internal.Validations.validatePowerOfTwo;

/**
 * Unit test for {@link Validations}.
 */
class ValidationsTest {

    private static void valid(final Executable validation) {
        assertDoesNotThrow(validation);
    }

    private static void invalid(final Executable validation) {
        assertThrows(IllegalArgumentException.class, validation);
    }

    @Test
    void signValidations() {
        valid(() -> validateNonNegative("x", 0));
        invalid(() -> validateNonNegative("x", -1));
        valid(() -> validateGreaterThanZero("x", 1));
        invalid(() -> validateGreaterThanZero("x", 0));
        invalid(() -> validateGreaterThanZero("x", -1));
        assertEquals("x value must be positive, but was 0",
                assertThrows(IllegalArgumentException.class, () -> validateGreaterThanZero("x", 0)).getMessage());
    }

    @Test
    void powerOfTwoValidation() {
        valid(() -> validatePowerOfTwo("x", 1));
        valid(() -> validatePowerOfTwo("x", 2));
        valid(() -> validatePowerOfTwo("x", 1 << 30));
        invalid(() -> validatePowerOfTwo("x", 0));
        invalid(() -> validatePowerOfTwo("x", 3));
        invalid(() -> validatePowerOfTwo("x", -2));
        invalid(() -> validatePowerOfTwo("x", Integer.MIN_VALUE));
    }

    @Test
    void notClosedValidation() {
        valid(() -> validateNotClosed(closeable(false)));
        final IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validateNotClosed(closeable(true)));
        assertEquals("Already closed: closeable", e.getMessage());
        assertEquals("Already closed: closeable", alreadyClosedException(closeable(true)).getMessage());
        assertSame(IllegalStateException.class, alreadyClosedException(closeable(true)).getClass());
    }

    private static Closeable closeable(final boolean closed) {
        return new Closeable() {
            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public void close() {
                //no-op
            }

            @Override
            public String toString() {
                return "closeable";
            }
        };
    }
}
