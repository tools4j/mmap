/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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

/**
 * A file region that is mapped dynamically into memory as needed.  The mapping is triggered when
 * {@link #wrap(long, DirectBuffer) wrapping} a {@link DirectBuffer} at a certain position of the file.
 */
public interface Region {
    /**
     * Wraps the buffer starting from given currentPosition to the end of the mapped region.
     * Once mapped, buffer.capacity will indicate the length of the mapped memory.
     *
     * @param position  currentPosition in the file.
     * @param buffer    the direct buffer
     * @return the number of bytes that can now be read from buffer (equal to buffer capacity)
     */
    int wrap(long position, DirectBuffer buffer);

    /**
     * Unwraps the buffer if currently wrapped using this region.
     *
     * @param buffer    the direct buffer
     * @return  {#size} if unwrapped, 0 if unwrap did not succeed (yet) and -1 if this buffer is not curently wrapped
     *          with this region instance
     */
    int unwrap(DirectBuffer buffer);

    /**
     * Returns the total size of the region.
     *
     * @return tha maximum accessible size
     */
    int size();
}
