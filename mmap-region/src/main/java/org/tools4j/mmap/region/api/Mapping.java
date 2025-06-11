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
package org.tools4j.mmap.region.api;


import org.agrona.DirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.impl.FixedMapping;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

/**
 * A mapping is a file block directly mapped into memory. The file data is accessible through the {@link #buffer()}.
 * <p>
 * The different mapping subtypes are:
 * <ul>
 *     <li>{@link FixedMapping}:  A mapping that has arbitrary start and end position, but both are fixed for the
 *                                lifetime of the mapping.</li>
 *     <li>{@link RegionMapping}: A mapping of a predefined region size (typically powers of two), or a slice of such a
 *                                region. Most region mappings are also a <code>DynamicMapping</code>:<ul>
 *       <li>{@link DynamicMapping}: A region mapping that can be moved to map different positions from the underlying
 *                                   file. A pure dynamic mapping can only move to region start positions; the following
 *                                   subtypes allow moving to arbitrary positions:<ul>
 *         <li>{@link ElasticMapping}:  A dynamic mapping that starts at an arbitrary offset from the region start
 *                                      position and spans all bytes until the end of that region.</li>
 *         <li>{@link AdaptiveMapping}: A dynamic mapping of an arbitrary slice of the file. Adaptive mappings can span
 *                                      across region boundaries. This is achieved by mapping blocks into memory that
 *                                      are slightly larger than the region size if necessary. Adaptive mappings can be
 *                                      moved around to any position in the file, with or without changing the mapping
 *                                      length.</li>
 *       </ul></li>
 *     </ul></li>
 * </ul>
 * <p>
 * Use one of the static factory methods in {@link Mappings} to create mapping instances.
 */
public interface Mapping extends AutoCloseable {
    /**
     * @return the file access mode used for this mapping
     */
    AccessMode accessMode();

    /**
     * Returns the start position of the mapping, or {@link NullValues#NULL_POSITION NULL_POSITION} if this mapping is
     * not {@link #isMapped() mapped}.
     *
     * @return the mapped position, or -1 if unavailable
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
     * @return the mapped address, or zero if unavailable
     */
    @Unsafe
    long address();

    /**
     * Returns the number of bytes available via {@linkplain #buffer() buffer} which is equal to the buffer's
     * {@linkplain DirectBuffer#capacity() capacity}.
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
     * {@linkplain DirectBuffer#capacity() capacity}.
     * <p>
     * <br>
     * <b>NOTE:</b> Re-wrapping the returned buffer through
     * {@link DirectBuffer#wrap(DirectBuffer) DirectBuffer.wrap(..)} is potentially unsafe and could result in a JVM
     * crash when this mapping is unmapped (e.g. because it is {@link #close() closed} or
     * {@link DynamicMapping#moveTo(long) moved} to a new position).
     *
     * @return the buffer to read and/or write mapping data.
     */
    AtomicBuffer buffer();

    /**
     * @return true if the mapping is mapped to a file block and data is available through the {@link #buffer()}
     */
    default boolean isMapped() {
        return position() > NULL_POSITION;
    }

    /**
     * @return true if this mapping is closed
     */
    boolean isClosed();

    /**
     * Closes this mapping.
     */
    void close();
}
