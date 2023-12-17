/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.impl;

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.region.api.RegionState;

import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

interface MappingState extends AutoCloseable {

    long position();
    AtomicBuffer buffer();

    RegionState state();

    /**
     * Local mapping if possible without unmapping of current page, meaning that only the viewport {@link #buffer()}
     * will be adjusted.
     * @param position the new position requested
     * @return true if local mapping was possible, and false otherwise
     */
    boolean requestLocal(long position);
    /**
     * Maps the requested position after first unmapping the region currently mapped for this region.
     * @param position the new position requested
     * @return true if successful, and false if mapping is not currently possible, either because the region is already
     *         closed or because it is in an async state that does not support mapping to a new position
     */
    boolean request(long position);

    /**
     * Unmaps currently mapped region and closes the region.
     */
    void close();

    /**
     * Mapping state with default implementations for terminal CLOSED state.
     * @see RegionState#CLOSED
     */
    interface ClosedState extends MappingState {
        @Override
        default long position() {
            return NULL_POSITION;
        }

        @Override
        default AtomicBuffer buffer() {
            return EmptyBuffer.INSTANCE;
        }

        @Override
        default RegionState state() {
            return RegionState.CLOSED;
        }

        @Override
        default boolean requestLocal(final long position) {
            return false;
        }

        @Override
        default boolean request(final long position) {
            return false;
        }
        @Override
        default void close() {
            //no-op
        }
    }
}
