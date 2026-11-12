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
package org.tools4j.mmap.core.buffer;

/**
 * The mechanism accessing the memory of all buffers, chosen once per JVM with the system property
 * {@value org.tools4j.mmap.core.config.CoreConfigurations#MEMORY_ACCESS_PROPERTY}, see
 * {@link org.tools4j.mmap.core.config.CoreConfigurations#defaultMemoryAccessMode()}.
 */
public enum MemoryAccessMode {
    /**
     * {@code jdk.internal.misc.Unsafe}, the default and the fastest, on all supported Java versions; needs
     * {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED} (or {@code --add-opens}), as Agrona does.
     */
    UNSAFE,
    /**
     * The Foreign Function and Memory API, public API, Java 22 or later; needs
     * {@code --enable-native-access=ALL-UNNAMED}, otherwise the JVM warns about the restricted method creating the
     * segment for direct memory. Byte buffers need {@code --add-opens java.base/java.nio=ALL-UNNAMED} to be accessed
     * without allocating; otherwise wrapping a direct {@code ByteBuffer} creates a segment to find out its address,
     * unless the flyweight already wraps it, and so does copying from or to one, and wrapping a read-only heap
     * {@code ByteBuffer} creates a segment to access its memory; the first buffer created warns about the missing flag
     * through the default error listener.
     */
    FFM
}
