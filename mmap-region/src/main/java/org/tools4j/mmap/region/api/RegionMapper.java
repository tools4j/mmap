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
package org.tools4j.mmap.region.api;

import org.agrona.DirectBuffer;

public interface RegionMapper extends AutoCloseable {
    /**
     * Default maximum time to wait in milliseconds when closing a mapper (or all underlying mappers) if closing
     * involves some asynchronous operations.
     */
    long DEFAULT_MAX_CLOSE_WAIT_MILLIS = 500;

    /**
     * Value returned by {@link #map(long, DirectBuffer) map(..)} if the mapping request was accepted and the operation
     * is being processed asynchronously; mapping will eventually succeed if it is re-attempted.
     */
    int PROCESSING = 0;
    /**
     * Value returned by {@link #map(long, DirectBuffer) map(..)} if another mapping operation was underway and will
     * have to complete before a new request can be accepted; mapping will eventually succeed if it is re-attempted.
     */
    int BUSY = -1;
    /**
     * Value returned by {@link #map(long, DirectBuffer) map(..)} if the mapping operation has failed, usually because
     * the requested position does not exist; retried mapping requests might succeed eventually if the position is under
     * creation by another process while re-attempting.
     */
    int FAILED = -2;
    /**
     * Value returned by {@link #map(long, DirectBuffer) map(..)} if this region mapper is closed or closing;
     * re-attempted mapping requests will also fail.
     */
    int CLOSED = -3;

    /**
     * Returns the metrics of regions mapped by this region mapper.
     * @return metrics of regions mapped by this class
     */
    RegionMetrics regionMetrics();

    /**
     * Attempts to map the buffer at the specified position which is performed synchronously or asynchronously
     * (see {@link #isAsync()}).<br>
     * <p>
     * The method returns a value {@code n}<ul>
     *     <li>{@code n > 0} : the number of bytes available if the buffer was mapped and is ready for data access</li>
     *     <li>{@code n = 0} : if the mapping is being processed asynchronously and the existing buffer was unwrapped (if
     *     it was used before) -- retry to eventually map the buffer</li>
     *     <li>{@code n < 0} : if the mapping request failed and the buffer was left unchanged -- retry may or may not
     *     succeed depending on the value (see constants for details)</li>
     * </ul>
     *
     * @param position      the requested position, a non-negative value
     * @param buffer        the buffer to be wrapped for data access if mapping is successful
     *                      (can be null for pre-mapping of the region to speed-up subsequent map requests)
     * @return a positive value indicating the number of available bytes if mapping is successful, or a result code as
     *         per the constants defined by this class
     * @throws IllegalArgumentException if position is negative
     */
    int map(long position, DirectBuffer buffer);

    /** @return true if this mapper performs asynchronous mapping, and false if synchronous mapping is used*/
    boolean isAsync();

    /** @return true if this mapper is closed*/
    boolean isClosed();

    /**
     * Closes this region mapper and issued mapping, or all mappers and mappings if this is a
     * {@link org.tools4j.mmap.region.impl.RingCacheRegionMapper RingCacheRegionMapper}
     */
    @Override
    default void close() {
        close(DEFAULT_MAX_CLOSE_WAIT_MILLIS);
    }
    /**
     * Closes this region mapper and issued mapping, or all mappers and mappings if this is a
     * {@link org.tools4j.mmap.region.impl.RingCacheRegionMapper RingCacheRegionMapper}, waiting at most the specified
     * time in milliseconds.
     *
     * @param maxWaitMillis maximum time to wait for asynchronous operations to complete, if any
     */
    void close(long maxWaitMillis);
}
