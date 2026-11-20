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
package org.tools4j.mmap.mapping.api;

import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;

/**
 * A {@link Mapping} giving read and write access to the file data through a {@link MutableMemoryBuffer}.
 * <p>
 * The {@link Mapping} documentation provides an overview of the different mapping types.
 *
 * @see FixedMutableMapping
 * @see DynamicMutableMapping
 */
public interface MutableMapping extends Mapping {
    /**
     * Returns the buffer to access the mapped data, as described in {@link Mapping#buffer()}, also for writing.
     * <p>
     * Writes throw {@link java.nio.ReadOnlyBufferException} if the {@linkplain #accessMode() access mode} is
     * {@link AccessMode#READ_ONLY READ_ONLY}, which is never the case for mutable mappings created through
     * {@link Mappings} or acquired from a {@link MutableMappingPool}.
     *
     * @return the buffer to read and write mapping data
     */
    @Override
    MutableMemoryBuffer buffer();
}
