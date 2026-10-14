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
package org.tools4j.mmap.mapping.unsafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.api.Unsafe;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionCacheSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionSize;

/**
 * Region mapper with a bounded ring cache of mapped regions, where each region position maps to a fixed slot.
 * Actual mapping and unmapping operations are delegated to an underlying region mapper.
 * <p>
 * A cached region is <i>in use</i> from the time it is mapped until the holder unmaps it. Without deferred
 * unmapping, unmapping a cached region unmaps it immediately and frees its slot. With deferred unmapping, the region
 * is kept mapped and only marked as <i>unused</i>; if it is requested again while still cached, it is in use again
 * without a new map operation.
 * <p>
 * When a new region is mapped to an occupied slot, an unused region in the slot is unmapped, while a region still in
 * use is evicted from the cache but <b>not</b> unmapped, as its holder may still access it; the holder's later unmap
 * is passed through to the underlying mapper.
 */
@Unsafe
final class RingCacheRegionMapper implements RegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RingCacheRegionMapper.class);
    private static final long POSITION_MASK = 0x7fffffffffffffffL;
    private static final long UNUSED_BIT = 0x8000000000000000L;
    private final RegionMapper baseMapper;
    private final boolean deferUnmapping;
    private final int regionSizeBits;
    private final int cacheSizeMask;
    private final long[] positions;
    private final long[] addresses;

    public RingCacheRegionMapper(final RegionMapper baseMapper, final int cacheSize, final boolean deferUnmapping) {
        validateRegionSize(baseMapper.regionSize());
        validateRegionCacheSize(cacheSize);
        this.baseMapper = requireNonNull(baseMapper);
        this.deferUnmapping = deferUnmapping;
        this.regionSizeBits = Integer.SIZE - Integer.numberOfLeadingZeros(baseMapper.regionSize() - 1);
        this.cacheSizeMask = cacheSize - 1;
        this.positions = new long[cacheSize];
        this.addresses = new long[cacheSize];
        Arrays.fill(positions, NULL_POSITION);
        Arrays.fill(addresses, NULL_ADDRESS);
    }

    @Override
    public AccessMode accessMode() {
        return baseMapper.accessMode();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return baseMapper.regionMetrics();
    }

    private int cacheIndex(final long position) {
        return (int) (cacheSizeMask & (position >>> regionSizeBits));
    }

    @Override
    public long mapInternal(final long position, final int regionSize) {
        final int cacheIndex = cacheIndex(position);
        final long curPosition = positions[cacheIndex];
        if (curPosition == position) {
            return addresses[cacheIndex];
        }
        final long unmaskedPosition = curPosition & POSITION_MASK;
        if (unmaskedPosition == position) {//won't match for NULL_POSITION
            positions[cacheIndex] = position;
            return addresses[cacheIndex];
        }
        final long addr = baseMapper.mapInternal(position, regionSize);
        if (addr == NULL_ADDRESS) {
            return NULL_ADDRESS;
        }
        if (curPosition != NULL_POSITION && (curPosition & UNUSED_BIT) != 0) {
            baseMapper.unmapInternal(unmaskedPosition, addresses[cacheIndex], regionSize);
        }
        positions[cacheIndex] = position;
        addresses[cacheIndex] = addr;
        return addr;
    }

    private boolean isInLocalCache(final long position) {
        final int cacheIndex = cacheIndex(position);
        final long curPosition = positions[cacheIndex];
        return (curPosition & POSITION_MASK) == position;
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return isInLocalCache(position) || baseMapper.isMappedInCache(position);
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        final int cacheIndex = cacheIndex(position);
        if (positions[cacheIndex] == position) {
            if (deferUnmapping) {
                positions[cacheIndex] = (UNUSED_BIT | position);
                return;
            }
            positions[cacheIndex] = NULL_POSITION;
            addresses[cacheIndex] = NULL_ADDRESS;
        }
        baseMapper.unmapInternal(position, address, regionSize);
    }

    @Override
    public boolean isClosed() {
        return baseMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            for (int cacheIndex = 0; cacheIndex <= cacheSizeMask; cacheIndex++) {
                final long posn = positions[cacheIndex];
                final long addr = addresses[cacheIndex];
                if (posn != NULL_POSITION && addr != NULL_ADDRESS) {
                    baseMapper.unmap(posn & POSITION_MASK, addr);
                }
                positions[cacheIndex] = NULL_POSITION;
                addresses[cacheIndex] = NULL_ADDRESS;
            }
            baseMapper.close();
            LOGGER.info("Closed {}.", this);
        }
    }

    @Override
    public String toString() {
        return "RingCacheRegionMapper" +
                ":cacheSize=" + (cacheSizeMask + 1) +
                "|deferUnmapping=" + deferUnmapping +
                "|baseMapper=" + baseMapper;
    }
}
