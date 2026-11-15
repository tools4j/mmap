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
 *     <li>{@link DynamicMapping}: A mapping that can be moved to map different slices from the underlying file. The
 *                                 mapping operation always maps a whole region into memory of a predefined <i>region
 *                                 size</i> (typically powers of two). The dynamic mapping provides access to the mapped
 *                                 region or a slice of it, depending on the subtype:<ul>
 *         <li>{@link RegionMapping}:   A dynamic mapping that always maps the whole region. As a consequence, move
 *                                      operations are only permitted to positions that are multiples of the region
 *                                      size.</li>
 *         <li>{@link ElasticMapping}:  A dynamic mapping that starts at an offset from the region start
 *                                      position and spans all bytes until the end of that region.</li>
 *         <li>{@link AdaptiveMapping}: A dynamic mapping of an arbitrary slice of the region. Adaptive mappings start
 *                                      at an offset of the region and span a slice that is no longer than the remaining
 *                                      bytes from that region. In other words, the mapped slice can map any slice of
 *                                      the file (including zero length slices) as long as it does not cross region
 *                                      boundaries.</li>
 *     </ul></li>
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
