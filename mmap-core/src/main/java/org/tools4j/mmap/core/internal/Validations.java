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

import org.tools4j.mmap.core.base.Closeable;

/**
 * Generic argument and state validations throwing {@link IllegalArgumentException} or {@link IllegalStateException}.
 */
public enum Validations {
    ;

    public static void validateNonNegative(final String name, final long value) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " value cannot be negative, but was " + value);
        }
    }

    public static void validateGreaterThanZero(final String name, final long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " value must be positive, but was " + value);
        }
    }

    public static void validatePowerOfTwo(final String name, final int value) {
        if (value <= 0 || (value & (value - 1)) != 0) {
            throw new IllegalArgumentException(name + " must be a power of two but was " + value);
        }
    }

    public static void validateNotClosed(final Closeable closeable) {
        if (closeable.isClosed()) {
            throw alreadyClosedException(closeable);
        }
    }

    public static IllegalStateException alreadyClosedException(final Closeable closeable) {
        return new IllegalStateException("Already closed: " + closeable);
    }
}
