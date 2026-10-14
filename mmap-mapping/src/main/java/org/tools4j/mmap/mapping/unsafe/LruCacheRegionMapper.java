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

import org.agrona.collections.Hashing;
import org.agrona.collections.Long2LongHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.RegionMetrics;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;

/**
 * Region mapper with a bounded cache of mapped regions, evicted on a least-recently-used basis. Actual mapping and
 * unmapping operations are delegated to an underlying region mapper.
 * <p>
 * A cached region is <i>in use</i> from the time it is mapped until the holder unmaps it. Without deferred
 * unmapping, unmapping a cached region unmaps it immediately and frees its cache slot. With deferred unmapping, the
 * region is kept mapped and only marked as <i>unused</i>; if it is requested again while still cached, it is in use
 * again without a new map operation.
 * <p>
 * When a new region is mapped and the cache is full, a slot is reused as follows:
 * <ol>
 *     <li>the least recently freed unused slot, unmapping its region first, or otherwise</li>
 *     <li>the least recently used slot of a region still in use: the region is evicted from the cache but
 *         <b>not</b> unmapped, as its holder may still access it; the holder's later unmap is passed through to the
 *         underlying mapper.</li>
 * </ol>
 * Unmap requests for regions that are not cached, or that are cached with a different address (a region evicted
 * while in use and mapped again since), are always passed through to the underlying mapper.
 */
class LruCacheRegionMapper implements RegionMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(LruCacheRegionMapper.class);
    private static final long UNUSED_BIT = 0x8000000000000000L;
    private static final long USED_MASK = 0x7fffffffffffffffL;

    private final RegionMapper baseMapper;
    private final boolean deferUnmapping;
    private final Long2LongHashMap positionToIndex;
    private final long[] positions;//need to store positions for deferred unmapping of LRU position
    private final long[] addresses;
    private final int[] prevIndex;
    private final int[] nextIndex;
    private int mruIndex;//most recently used
    private int lrfIndex;//least recently freed

    public LruCacheRegionMapper(final RegionMapper baseMapper, final int cacheSize, final boolean deferUnmapping) {
        this.baseMapper = requireNonNull(baseMapper);
        this.deferUnmapping = deferUnmapping;
        this.positionToIndex = new Long2LongHashMap(cacheSize, Hashing.DEFAULT_LOAD_FACTOR, -1);
        this.positions = new long[cacheSize];
        this.addresses = new long[cacheSize];
        this.prevIndex = new int[cacheSize];
        this.nextIndex = new int[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            positions[i] = NULL_POSITION;
            addresses[i] = NULL_ADDRESS;
            prevIndex[i] = (cacheSize + i - 1) % cacheSize;
            nextIndex[i] = (i + 1) % cacheSize;
        }
        this.mruIndex = -1;
        this.lrfIndex = 0;
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
        long addr, posn;
        int index = (int)positionToIndex.get(position);
        if (index >= 0) {
            posn = positions[index];
            addr = addresses[index];
            assert (posn & USED_MASK) == position;
            assert addr > NULL_ADDRESS;
            if ((posn & UNUSED_BIT) != 0) {
                positions[index] = position;//mark used again
                popFromFree(index);
                pushAsMru(index);
            } else {
                markMostRecentlyUsed(index);
            }
            return addr;
        }
        addr = baseMapper.mapInternal(position, regionSize);
        if (addr != NULL_ADDRESS) {
            index = acquireIndex();
            positions[index] = position;
            addresses[index] = addr;
            positionToIndex.put(position, index);
            return addr;
        }
        return NULL_ADDRESS;
    }

    private boolean isInLocalCache(final long position) {
        return positionToIndex.containsKey(position);
    }

    @Override
    public boolean isMappedInCache(final long position) {
        return isInLocalCache(position) || baseMapper.isMappedInCache(position);
    }

    @Override
    public void unmapInternal(final long position, final long address, final int regionSize) {
        final int index = (int)positionToIndex.get(position);
        if (index < 0 || addresses[index] != address) {
            baseMapper.unmapInternal(position, address, regionSize);
            return;
        }
        assert positions[index] == position;
        if (deferUnmapping) {
            positions[index] = (position | UNUSED_BIT);
        } else {
            positions[index] = NULL_POSITION;
            addresses[index] = NULL_ADDRESS;
            positionToIndex.remove(position);
            baseMapper.unmapInternal(position, address, regionSize);
        }
        markMostRecentlyFreed(index);
    }

    //precondition: index is in used list
    private void markMostRecentlyUsed(final int index) {
        if (index != mruIndex) {
            popFromUsed(index);
            pushAsMru(index);
        }
    }

    //precondition: index is in used list
    private void markMostRecentlyFreed(final int index) {
        popFromUsed(index);
        pushAsMrf(index);
    }

    //precondition: currently free
    private int acquireIndex() {
        final int free = acquireIndexFromFree();
        return free >= 0 ? free : acquireIndexFromUsed();
    }

    private int acquireIndexFromFree() {
        final int lrf = lrfIndex;
        if (lrf < 0) {
            return -1;
        }
        unmapPositionIfNeeded(lrf);
        popFromFree(lrf);
        pushAsMru(lrf);
        return lrf;
    }

    private int acquireIndexFromUsed() {
        //repurpose least recently used, but don't unmap
        final int lru = prevIndex[mruIndex];
        final long pos = positions[lru];
        positionToIndex.remove(pos);
        positions[lru] = NULL_POSITION;
        addresses[lru] = NULL_ADDRESS;
        popFromUsed(lru);
        pushAsMru(lru);
        return lru;
    }

    private void unmapPositionIfNeeded(final int index) {
        final long posn = positions[index];
        final long addr = addresses[index];
        if (addr != NULL_ADDRESS) {
            assert posn != NULL_POSITION;
            final long posUnmasked = posn & USED_MASK;
            baseMapper.unmapInternal(posUnmasked, addr, regionSize());
            positions[index] = NULL_POSITION;
            addresses[index] = NULL_ADDRESS;
            final long rem = positionToIndex.remove(posUnmasked);
            assert rem == index;
        }
    }

    //precondition: index is a single-element list
    private void pushAsMru(final int index) {
        if (mruIndex >= 0) {
            push(index, mruIndex);
        }
        mruIndex = index;
    }

    //precondition: index is a single-element list
    private void pushAsMrf(final int index) {
        if (lrfIndex >= 0) {
            push(index, lrfIndex);
        } else {
            lrfIndex = index;
        }
    }

    private void popFromUsed(final int index) {
        final int next = pop(index);
        if (mruIndex == index) {
            mruIndex = next;
        }
    }

    private void popFromFree(final int index) {
        final int next = pop(index);
        if (lrfIndex == index) {
            lrfIndex = next;
        }
    }

    private int pop(final int index) {
        final int prev = prevIndex[index];
        final int next = nextIndex[index];
        if (prev == index) {
            assert next == index;
            return -1;//single element queue
        }
        nextIndex[prev] = next;
        prevIndex[next] = prev;
        prevIndex[index] = index;
        nextIndex[index] = index;
        return next;
    }

    private void push(final int index, final int position) {
        final int prev = prevIndex[position];
        nextIndex[prev] = index;
        prevIndex[index] = prev;
        nextIndex[index] = position;
        prevIndex[position] = index;
    }

    @Override
    public boolean isClosed() {
        return baseMapper.isClosed();
    }

    @Override
    public void close() {
        if (!isClosed()) {
            try {
                if (!positionToIndex.isEmpty()) {
                    final int regionSize = regionSize();
                    //NOTE: some garbage here
                    positionToIndex.forEachLong((pos, index) -> {
                        final long addr = addresses[(int)index];
                        assert pos != NULL_POSITION && addr != NULL_ADDRESS;
                        baseMapper.unmapInternal(pos & USED_MASK, addr, regionSize);
                    });
                }
            } finally {
                positionToIndex.clear();
                Arrays.fill(addresses, NULL_ADDRESS);
                Arrays.fill(prevIndex, -1);
                Arrays.fill(nextIndex, -1);
                mruIndex = -1;
                lrfIndex = -1;
                baseMapper.close();
                LOGGER.info("Closed lru-cache region mapper: base={}", baseMapper);
            }
        }
    }

    @Override
    public String toString() {
        return "LruCacheRegionMapper" +
                ":cached=" + positionToIndex.size() +
                "|deferUnmapping=" + deferUnmapping +
                "|baseMapper=" + baseMapper;
    }
}
