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

/**
 * A pool of mappings of the same region size, where the pool may optimize and reuse the same underlying memory mapped
 * region if mappings are overlapping or reused.
 * <p>
 * A mapping is acquired from the pool and is released back to the pool if it is closed. The poll may choose to recycle
 * and reuse closed mappings, hence they should no longer be used after closing. Closing the pool closes all mappings
 * acquired from this pool.
 * <p>
 * <b>Note:</b> The mapping pool and its mappings are <b>not thread safe</b> and all mappings from a single pool can
 * only be used from a single thread. To access the same data from multiple threads, each thread has to create its own
 * pool and mappings from that pool.
 * <p>
 * Use one of the static factory methods in {@link Mappings} to create mapping pool instances.
 */
public interface MappingPool extends AutoCloseable {
    /**
     * @return the file access mode used for all mapping from this repository
     */
    AccessMode accessMode();

    /**
     * Returns the size of a region, which is also the maximum size of a mapping from this repository.
     * @return the size of a mappable memory region in bytes
     * @see #regionMetrics()
     */
    default int regionSize() {
        return regionMetrics().regionSize();
    }

    /**
     * @return the region metrics determined by the {@linkplain #regionSize() region size}
     */
    RegionMetrics regionMetrics();

    DynamicMapping acquireDynamicMapping();
    AdaptiveMapping acquireAdaptiveMapping();
    ElasticMapping acquireElasticMapping();

    /**
     * @return true if this mapping repository is closed
     */
    boolean isClosed();

    /**
     * Closes this repository with all its mappings.
     */
    void close();
}
