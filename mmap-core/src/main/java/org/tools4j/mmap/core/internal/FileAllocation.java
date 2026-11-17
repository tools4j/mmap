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

/**
 * The result of allocating file space natively with {@code fallocate}, see
 * {@link FileChannelLock#allocate(long, long, boolean)}: {@link #ALLOCATED}, or why the space was not allocated, so
 * that the caller writes zeros instead.
 */
public enum FileAllocation {
    /** The space was allocated and reads as zero. */
    ALLOCATED("allocated"),
    /** Native allocation needs Java 22 or later, and mmap-core built with Java 22 or later. */
    JDK_UNSUPPORTED("fallocate needs Java 22 or later, and mmap-core built with it"),
    /** Native allocation needs 64-bit Linux. */
    PLATFORM_UNSUPPORTED("fallocate needs 64-bit Linux"),
    /** Native access is not enabled with {@code --enable-native-access}, and allocating without it was not allowed. */
    NATIVE_ACCESS_DISABLED("fallocate needs native access, enable it with --enable-native-access=ALL-UNNAMED"),
    /** Native access is denied by the JVM. */
    NATIVE_ACCESS_DENIED("fallocate needs native access, which the JVM denies"),
    /** The file system does not support native allocation, detected now; remembered for the file. */
    FILE_SYSTEM_UNSUPPORTED("the file system does not support fallocate"),
    /** Not attempted, as the file system was found not to support native allocation before. */
    SKIPPED("the file system does not support fallocate, found before");

    private final String description;

    FileAllocation(final String description) {
        this.description = description;
    }

    /**
     * Returns a description of the result, for instance for a warning when the space was not allocated.
     *
     * @return the description, a constant string
     */
    public String description() {
        return description;
    }
}
