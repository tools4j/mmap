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

import java.util.concurrent.TimeUnit;

/**
 * AsyncRegion version of region mapper delaying map and unmap requests until
 * later.  An Outstanding request is processed (usually in a different thread) via
 * {@link #processRequest()}.
 */
public interface AsyncMappableRegion extends MappableRegion {
    /**
     * Process outstanding {@link #map(long)} or {@link #unmap()} operations if any have been requested.
     * @return true if an operation has been processed, and false if no request was outstanding
     */
    boolean processRequest();

    default long map(final long position, final long timeout, final TimeUnit unit) {
        long address = map(position);
        if (address == NULL) {
            final long nanos = unit.toNanos(timeout);
            final long start = System.nanoTime();
            do {
                address = map(position);
            } while (address == NULL && System.nanoTime() - start < nanos);
        }
        return address;
    }
}
