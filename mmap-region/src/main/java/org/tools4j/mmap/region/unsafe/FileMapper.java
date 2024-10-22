/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.unsafe;

import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;

/**
 * Maps a file to specified position and length.
 * Various implementations may work with single of multiple files
 * for reading and writing mapping mode.
 */
@Unsafe
public interface FileMapper extends AutoCloseable {
    /**
     * @return the file access mode used by this file mapper
     */
    AccessMode accessMode();

    /**
     * Map memory region at absolute position with given length to memory address.
     *
     * @param position - absolute position
     * @param length - region length
     * @return positive value if address has been mapped, or {@code NULL_ADDRESS} otherwise
     */
    long map(long position, int length);

    /**
     * Unmaps previously mapped address of the region starting at absolute position with length.
     *
     * @param address previously mapped address
     * @param position ab position
     * @param length region length
     */
    void unmap(long address, long position, int length);

    /**
     * @return true if this file mapper is closed
     */
    boolean isClosed();

    /**
     * Closes this file mapper and all underlying resources.
     */
    @Override
    void close();
}
