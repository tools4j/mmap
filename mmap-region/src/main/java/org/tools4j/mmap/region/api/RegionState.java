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

/**
 * State of a {@link Region} for instance indicating whether the region is ready for data access.  Some states are
 * associated with {@linkplain #isAsync() async} mapping and unmapping operations that are performed in the background.
 * <p>
 * <br>
 * The state machines for synchronous and asynchronous mapping/unmapping are as follows (ERROR state not shown):
 * <br>
 * <br>
 * <b>User initiated transitions:</b>
 * <pre><i>
 *     (m) mapping request
 *     (c) close
 *     (a) async processing
 * </i></pre>
 * <b>Synchronous:</b>
 * <pre><i>{@code
 *                     ,(m).
 *                    \/    \
 *   UNMAPPED --(m)--> MAPPED
 *      |                |
 *     (c)              (c)
 *      |                |
 *     \/                |
 *   CLOSED <-----------`
 *
 * }</i></pre>
 * <b>Asynchronous</b>
 * <pre><i>{@code
 *                       ,(m).---------(m)-.
 *                      \/    \             \
 *   UNMAPPED --(m)--> REQUESTED --(a)--> MAPPED
 *      |                 |                 |
 *     (c)               (c)---------------(c)
 *      |                                   |
 *      |               ,(c).               |
 *     \/              \/    \              |
 *   CLOSED <---(a)--- CLOSING <-----------`
 *
 * }</i></pre>
 */
public enum RegionState {
    /** The region has not been mapped yet. */
    UNMAPPED,
    /** The region is mapped and ready for data access. */
    MAPPED,
    /** Mapping attempt failed, for instance because the region requested for read-access does not exist. */
    FAILED,
    /** The region has been closed. */
    CLOSED,
    /** Mapping of the region has been requested and will be performed asynchronously. */
    REQUESTED,
    /** Closing of the region has been initiated and will be performed asynchronously. */
    CLOSING;

    private static final RegionState[] VALUES = values();

    /**
     * Returns the number of region state constants.
     * @return the number of constants
     */
    public static int valueCount() {
        return VALUES.length;
    }

    /**
     * Returns the region state constant given the ordinal.
     * @param ordinal the ordinal of the constant, from 0 to (n-1) where n is the number of constants
     * @return the region state
     * @see #valueCount()
     */
    public static RegionState valueByOrdinal(final int ordinal) {
        return VALUES[ordinal];
    }

    /**
     * Returns true if this state is associated with asynchronous mapping or unmapping operation.
     * @return true if the state is {@link #REQUESTED} or {@link #CLOSING}
     */
    public boolean isAsync() {
        return this == REQUESTED || this == CLOSING;
    }

    /**
     * Returns true if the region is ready for data access.
     * @return true if the state is {@link #MAPPED}
     */
    public boolean isReady() {
        return this == MAPPED;
    }

    /**
     * Returns true if the region can be mapped, which is only not true if the region is closed or close has been
     * requested.
     * @return true if the state is {@link #CLOSED} or {@link #CLOSING}
     */
    public boolean isMappable() {
        return this != CLOSED && this != CLOSING;
    }

    /**
     * Returns true if the region is closed.
     * @return true if the state is {@link #CLOSED}
     */
    public boolean isClosed() {
        return this == CLOSED;
    }

    /**
     * Returns true if the region is not yet ready for data access due to an async mapping operation.
     * @return true if the state is {@link #REQUESTED}
     */
    public boolean isPending() {
        return this == REQUESTED;
    }
}
