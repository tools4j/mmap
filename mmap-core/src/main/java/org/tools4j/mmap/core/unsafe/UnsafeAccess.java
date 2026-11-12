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
package org.tools4j.mmap.core.unsafe;

/**
 * Access to {@code jdk.internal.misc.Unsafe}, which needs the package exported to the caller, as Agrona does, with
 * {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED} (or {@code --add-opens}).
 */
@Unsafe
enum UnsafeAccess {
    ;
    static final jdk.internal.misc.Unsafe UNSAFE = unsafe();

    private static jdk.internal.misc.Unsafe unsafe() {
        try {
            return jdk.internal.misc.Unsafe.getUnsafe();
        } catch (final IllegalAccessError e) {
            throw new IllegalStateException("Memory access through jdk.internal.misc.Unsafe needs " +
                    "--add-exports java.base/jdk.internal.misc=ALL-UNNAMED (or --add-opens)", e);
        }
    }
}
