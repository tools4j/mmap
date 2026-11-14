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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Access to {@code jdk.internal.misc.Unsafe}, which needs the package exported to the caller, as Agrona does, with
 * {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED} (or {@code --add-opens}).
 */
@Unsafe
public enum UnsafeAccess {
    ;
    static final jdk.internal.misc.Unsafe UNSAFE = unsafe();
    private static final int PAGE_SIZE = UNSAFE.pageSize();

    private static jdk.internal.misc.Unsafe unsafe() {
        try {
            return jdk.internal.misc.Unsafe.getUnsafe();
        } catch (final IllegalAccessError e) {
            throw new IllegalStateException("Access to jdk.internal.misc.Unsafe needs " +
                    "--add-exports java.base/jdk.internal.misc=ALL-UNNAMED (or --add-opens)", e);
        }
    }

    /**
     * Returns the page size of the OS, which is not the granularity of memory mappings: that is larger on Windows,
     * 64 KB with 4 KB pages.
     *
     * @return the page size in bytes, a power of two
     */
    public static int pageSize() {
        return PAGE_SIZE;
    }

    /**
     * Returns the trusted lookup of the JDK, with access to all members of all classes, also of packages the JDK does
     * not open, such as its file dispatcher mapping files.
     *
     * @return the trusted lookup
     * @throws IllegalStateException if the JDK has no trusted lookup
     */
    public static MethodHandles.Lookup trustedLookup() {
        final Field field;
        try {
            field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        } catch (final NoSuchFieldException e) {
            throw new IllegalStateException("The JDK has no trusted lookup", e);
        }
        return (MethodHandles.Lookup)UNSAFE.getReference(UNSAFE.staticFieldBase(field),
                UNSAFE.staticFieldOffset(field));
    }

    /**
     * Writes to the memory at the given address without changing it, to fault in its page writable, also while other
     * threads or processes write to the page: adding zero always writes, unlike a compare-and-set failing on aarch64
     * without LSE, and needs no memory ordering.
     *
     * @param address the address of the 8 bytes to touch, aligned to 8 bytes
     */
    public static void touchPage(final long address) {
        UNSAFE.getAndAddLongRelease(null, address, 0L);
    }
}
