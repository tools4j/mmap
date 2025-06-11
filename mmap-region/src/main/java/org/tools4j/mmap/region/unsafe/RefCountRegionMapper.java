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
package org.tools4j.mmap.region.unsafe;

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2LongCounterMap;
import org.agrona.collections.Long2LongHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.RegionMetrics;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;

/**
 * Region mapper with an unbounded cache and reference counting for mapped regions.  A region is unmapped when the count
 * reaches zero.  Actual mapping and unmapping operations are delegated to an underlying region mapper.
 */
class RefCountRegionMapper implements RegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefCountRegionMapper.class);

    private final RegionMapper baseMapper;
    private final Long2LongHashMap positionToAddress;
    private final Long2LongCounterMap positionToRefCount;

    public RefCountRegionMapper(final RegionMapper baseMapper, final int initialCacheSize) {
        this.baseMapper = requireNonNull(baseMapper);
        this.positionToAddress = new Long2LongHashMap(initialCacheSize, Hashing.DEFAULT_LOAD_FACTOR, NULL_ADDRESS);
        this.positionToRefCount = new Long2LongCounterMap(initialCacheSize, Hashing.DEFAULT_LOAD_FACTOR, 0);
    }

    @Override
    public AccessMode accessMode() {
        return baseMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return baseMapper.regionMetrics();
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        long addr = positionToAddress.get(position);
        if (addr != NULL_ADDRESS) {
            positionToRefCount.incrementAndGet(position);
            return addr;
        }
        addr = baseMapper.mapInternal(position, regionSize);
        if (addr != NULL_ADDRESS) {
            positionToAddress.put(position, addr);
            positionToRefCount.put(position, 1);
            return addr;
        }
        return NULL_ADDRESS;
    }

    private boolean isInLocalCache(final long position) {
        return positionToAddress.containsKey(position);
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return isInLocalCache(position) || baseMapper.isMappedInCache(position);
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        final long addr = positionToAddress.get(position);
        if (addr != address) {
            throw new IllegalArgumentException("Position " + position + " is mapped to " + addr +
                    " but provided address is " + address);
        }
        final long remaining = positionToRefCount.decrementAndGet(position);
        if (remaining <= 0) {
            assert remaining == 0 : "remaining is negative";
            positionToAddress.remove(position);
            baseMapper.unmapInternal(position, address, regionSize);
        }
    }

    @Override
    public boolean isClosed() {
        return baseMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            try {
                if (!positionToAddress.isEmpty()) {
                    final int regionSize = regionSize();
                    //NOTE: some garbage here
                    positionToAddress.forEachLong((pos, addr) -> {
                        if (pos != NULL_POSITION) {
                            baseMapper.unmapInternal(pos, addr, regionSize);
                        }
                    });
                }
            } finally {
                positionToAddress.clear();
                positionToRefCount.clear();
                baseMapper.close();
                LOGGER.info("Closed ref-count region mapper: base={}", baseMapper);
            }
        }
    }

    @Override
    public String toString() {
        return "RefCountRegionMapper" +
                ":cached=" + positionToAddress.size() +
                "|baseMapper=" + baseMapper;
    }
}
