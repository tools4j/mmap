/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
 * Mapping is implemented by {@link FixedMapping}, {@link DynamicMapping} and {@link OffsetMapping}.
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
