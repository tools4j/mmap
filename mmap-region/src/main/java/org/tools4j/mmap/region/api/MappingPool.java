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

import org.tools4j.mmap.region.impl.Closeable;

/**
 * A mapping pool provides mappings that share the same file, {@linkplain #accessMode() access mode} and mapping
 * parameters such as region size and mapping strategy. The pool optimizes and shares underlying mapped regions and uses
 * reference counting to release mapped regions only when the last mapping releases it.
 * <p>
 * A mapping is acquired from the pool via one of the acquire methods, and it is released back to the pool when it is
 * closed. Closing the pool also closes all mappings acquired from this pool.
 * <p>
 * <b>Note:</b> The mapping pool and its mappings are <b>not thread safe</b> and all mappings from a single pool can
 * only be used from a single thread. To access the same data from multiple threads, each thread has to create its own
 * pool and mappings from that pool.
 * <p>
 * Use one of the static factory methods in {@link Mappings} to create mapping pool instances.
 */
public interface MappingPool extends RegionAware, Closeable {
    /**
     * @return the file access mode used for all mapping from this repository
     */
    AccessMode accessMode();

    /**
     * Acquires and returns a new {@link RegionMapping}.
     * @return a new region mapping instance
     */
    RegionMapping acquireRegionMapping();

    /**
     * Acquires and returns a new {@link ElasticMapping}.
     * @return a new elastic mapping instance
     */
    ElasticMapping acquireElasticMapping();

    /**
     * Acquires and returns a new {@link AdaptiveMapping}.
     * @return a new adaptive mapping instance
     */
    AdaptiveMapping acquireAdaptiveMapping();

    /**
     * @return true if this mapping pool is closed
     */
    boolean isClosed();

    /**
     * Closes this repository with all its mappings.
     */
    void close();
}
