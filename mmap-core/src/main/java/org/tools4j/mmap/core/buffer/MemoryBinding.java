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
 * How a buffer is bound to its memory, fixed when the buffer is created. Whether the memory is
 * {@linkplain MemoryBuffer#isDirect() direct} or {@linkplain MemoryBuffer#isReadOnly() read-only} is a property of the
 * memory instead, and how it is accessed is chosen once per JVM, see {@link MemoryAccessMode}.
 */
public enum MemoryBinding {
    /** Bound to the memory it was created with, allocated or an existing {@code long[]}, for its whole lifetime. */
    FIXED,
    /**
     * Rebinding itself to larger memory on writes beyond its capacity, on the heap or direct like the memory it was
     * created with; see {@link MutableMemoryBuffer}.
     */
    EXPANDABLE,
    /** Rebound to other memory by {@code wrap} and {@code unwrap}, see {@link MemoryBufferFlyweight}. */
    WRAPPABLE
}
