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

import org.tools4j.mmap.core.base.Closeable;
import org.tools4j.mmap.core.buffer.MemoryBufferFlyweight;
import org.tools4j.mmap.core.buffer.MutableMemoryBuffer;
import org.tools4j.mmap.core.unsafe.Unsafe;

import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;

/**
 * A mapping is a file block directly mapped into memory. The file data is accessible through the {@link #buffer()}.
 * <p>
 * The different mapping subtypes are:
 * <ul>
 *     <li>{@link FixedMapping}:   A mapping that has arbitrary start and end position, but both are fixed for the
 *                                 lifetime of the mapping.</li>
 *     <li>{@link DynamicMapping}: A mapping of a slice of the file that can be moved to other slices. The file is
 *                                 mapped in regions of a predefined <i>region size</i> (typically powers of two), and
 *                                 a slice can be of any length from zero up to the end of the region its position
 *                                 lies in.</li>
 * </ul>
 * <p>
 * Use one of the static factory methods in {@link Mappings}, or the acquire methods of a {@link MappingPool}, to
 * create mapping instances.
 */
public interface Mapping extends Closeable {
    /**
     * Returns the file access mode used for this mapping.
     *
     * @return the file access mode used for this mapping
     */
    AccessMode accessMode();

    /**
     * Returns the start position of the mapping, or {@link NullValues#NULL_POSITION NULL_POSITION} if this mapping is
     * not {@link #isMapped() mapped}.
     *
     * @return the mapped position, or {@link NullValues#NULL_POSITION NULL_POSITION} if unavailable
     */
    long position();

    /**
     * Returns the mapped memory address, or {@link NullValues#NULL_ADDRESS NULL_ADDRESS} if this mapping is not
     * {@link #isMapped() mapped}.
     * <p>
     * <br>
     * <b>NOTE:</b> Using the returned address directly for memory access is unsafe and could result in a JVM crash when
     * this mapping is unmapped (e.g. because it is {@link #close() closed} or {@link DynamicMapping#moveTo(long) moved}
     * to a new position).
     *
     * @return the mapped address, or {@link NullValues#NULL_ADDRESS NULL_ADDRESS} if unavailable
     */
    @Unsafe
    long address();

    /**
     * Returns the number of bytes available via {@linkplain #buffer() buffer} which is equal to the buffer's
     * {@linkplain MutableMemoryBuffer#capacity() capacity}.
     *
     * @return the number of bytes available via buffer, zero if not {@linkplain #isMapped() mapped}
     */
    default int bytesAvailable() {
        return buffer().capacity();
    }

    /**
     * Returns the limit of this mapping, the position <i>after</i> the last byte that is accessible through this
     * mapping, or {@link NullValues#NULL_POSITION NULL_POSITION} if this mapping is not {@link #isMapped() mapped}.
     *
     * @return the absolute position limit of this mapping (exclusive)
     */
    default long limit() {
        return position() + bytesAvailable();//NOTE: also works for NULL_POSITION with zero bytes available
    }

    /**
     * Returns the buffer to access the mapped data. The value {@code buffer[i]} corresponds to the byte
     * {@code file[position() + i]} in the mapped file.
     * <p>
     * If the mapping is not ready for data access, the returned buffer will have zero
     * {@linkplain MutableMemoryBuffer#capacity() capacity}. The buffer of a {@link AccessMode#READ_ONLY read-only}
     * mapping is {@linkplain MutableMemoryBuffer#isReadOnly() read-only}: writes throw
     * {@link java.nio.ReadOnlyBufferException}.
     * <p>
     * <br>
     * <b>NOTE:</b> The returned buffer follows this mapping: it is re-wrapped when the mapping moves, so it is always
     * safe to use, but then accesses the data at the new position, or none (zero capacity) once the mapping is
     * closed or not mapped. A flyweight wrapping it, or a range of it, through
     * {@link MemoryBufferFlyweight#wrap(org.tools4j.mmap.core.buffer.MemoryBuffer) MemoryBufferFlyweight.wrap(..)},
     * does not: it keeps accessing the memory mapped when it was wrapped, which is unmapped when this mapping is
     * {@link #close() closed} or {@link DynamicMapping#moveTo(long) moved} to a new position, and accessing it then
     * could crash the JVM. Don't keep such a flyweight beyond the next move of this mapping.
     *
     * @return the buffer to read and/or write mapping data.
     */
    MutableMemoryBuffer buffer();

    /**
     * Returns whether the mapping is mapped to a file block with data available through the buffer.
     *
     * @return true if the mapping is mapped to a file block and data is available through the {@link #buffer()}
     */
    default boolean isMapped() {
        return position() > NULL_POSITION;
    }
}
