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
package org.tools4j.mmap.core.api;

/**
 * Creates buffers and flyweights.
 * <p>
 * All buffers are instances of one internal class, so that call sites using them stay monomorphic, whatever memory
 * they access. Which mechanism accesses the memory is chosen once per JVM with the system property
 * {@value #MEMORY_ACCESS_PROPERTY}:
 * <ul>
 *     <li>{@code UNSAFE} (default): {@code jdk.internal.misc.Unsafe}, the fastest, on all supported Java versions;
 *     needs {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED}</li>
 *     <li>{@code FFM}: the Foreign Function and Memory API, public API, Java 22 or later; needs
 *     {@code --enable-native-access=ALL-UNNAMED}</li>
 * </ul>
 */
public enum MemoryBuffers {
    ;

    /** System property choosing the memory access mechanism, {@code UNSAFE} (default) or {@code FFM}. */
    public static final String MEMORY_ACCESS_PROPERTY = "mmap.core.memoryAccess";

    /**
     * Returns a new read-only flyweight, initially wrapping no memory (capacity 0).
     *
     * @return the flyweight
     */
    public static MemoryBufferFlyweight flyweight() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Returns a new mutable flyweight, initially wrapping no memory (capacity 0).
     *
     * @return the flyweight
     */
    public static MutableMemoryBufferFlyweight mutableFlyweight() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Allocates a heap buffer backed by a {@code long[]}, which supports ordered access and atomic operations.
     *
     * @param capacity the capacity in bytes, rounded up to a multiple of 8 for the array, but not for the buffer
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer allocate(final int capacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Allocates a direct buffer whose first byte is aligned to a cache line, freed by the garbage collector once the
     * buffer is no longer referenced.
     *
     * @param capacity the capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer allocateDirect(final int capacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Allocates an expandable heap buffer backed by a {@code long[]}, growing on writes beyond its capacity.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer expandable(final int initialCapacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Allocates an expandable direct buffer, growing on writes beyond its capacity; growing moves its memory, so
     * flyweights wrapping it see the old memory.
     *
     * @param initialCapacity the initial capacity in bytes
     * @return the buffer, zeroed
     */
    public static MutableMemoryBuffer expandableDirect(final int initialCapacity) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
