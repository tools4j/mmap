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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.unsafe.RecordingRegionMapper.address;
import static org.tools4j.mmap.mapping.unsafe.RecordingRegionMapper.position;

/**
 * Unit test for {@link LruCacheRegionMapper}: cache hits, least-recently-used eviction and deferred unmapping,
 * verified through the map/unmap calls reaching the base mapper.
 */
class LruCacheRegionMapperTest {

    private final RecordingRegionMapper base = new RecordingRegionMapper(64);

    private long map(final RegionMapper mapper, final int region) {
        final long address = mapper.map(position(region));
        assertEquals(address(position(region)), address, "region " + region);
        return address;
    }

    private static void unmap(final RegionMapper mapper, final int region) {
        mapper.unmap(position(region), address(position(region)));
    }

    @ParameterizedTest(name = "deferUnmapping={0}")
    @ValueSource(booleans = {false, true})
    void cacheHitDoesNotMapAgain(final boolean deferUnmapping) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, 4, deferUnmapping);
        map(mapper, 1);
        map(mapper, 1);
        map(mapper, 1);
        assertEquals(List.of("map:1"), base.takeCalls());
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 3, 4, 7})
    void evictsLeastRecentlyUsedWhenFullWithoutUnmappingIt(final int cacheSize) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, false);
        for (int region = 0; region < cacheSize; region++) {
            map(mapper, region);
        }
        //touch region 0 so that region 1 (if any) becomes least recently used
        map(mapper, 0);
        base.takeCalls();
        map(mapper, cacheSize);
        final int evicted = cacheSize == 1 ? 0 : 1;
        assertEquals(List.of("map:" + cacheSize), base.takeCalls(), "evicted region stays mapped for its holder");
        assertFalse(mapper.isMappedInCache(position(evicted)));
        assertEquals(cacheSize + 1, base.mapped());

        unmap(mapper, evicted);
        assertEquals(List.of("unmap:" + evicted), base.takeCalls(), "unmap of evicted region passes through to base");
        assertEquals(cacheSize, base.mapped());
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 3, 4, 7})
    void unmapWithoutDeferringUnmapsImmediately(final int cacheSize) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, false);
        map(mapper, 5);
        unmap(mapper, 5);
        assertEquals(List.of("map:5", "unmap:5"), base.takeCalls());
        map(mapper, 5);
        assertEquals(List.of("map:5"), base.takeCalls(), "no longer cached after unmap");
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {1, 2, 3, 4, 7})
    void deferredUnmappingKeepsRegionUntilSlotIsNeeded(final int cacheSize) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, true);
        map(mapper, 5);
        unmap(mapper, 5);
        assertEquals(List.of("map:5"), base.takeCalls(), "unmapping deferred");
        map(mapper, 5);
        assertEquals(List.of(), base.takeCalls(), "re-requested while still cached");
        unmap(mapper, 5);
        for (int region = 10; region < 10 + cacheSize; region++) {
            map(mapper, region);
        }
        final List<String> calls = base.takeCalls();
        assertTrue(calls.contains("unmap:5"), "deferred unmap once slot is needed: " + calls);
        assertEquals(cacheSize, base.mapped());
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {2, 3, 4, 7})
    void freedSlotsAreReusedBeforeEvictingUsedOnes(final int cacheSize) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, true);
        for (int region = 0; region < cacheSize; region++) {
            map(mapper, region);
        }
        unmap(mapper, cacheSize - 1);
        base.takeCalls();
        map(mapper, 20);
        assertEquals(List.of("map:20", "unmap:" + (cacheSize - 1)), base.takeCalls());
        for (int region = 0; region < cacheSize - 1; region++) {
            map(mapper, region);
        }
        assertEquals(List.of(), base.takeCalls(), "regions still in use were not evicted");
    }

    @ParameterizedTest(name = "cacheSize={0}")
    @ValueSource(ints = {3, 4, 7})
    void freedSlotsAreReusedLeastRecentlyFreedFirst(final int cacheSize) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, true);
        for (int region = 0; region < cacheSize; region++) {
            map(mapper, region);
        }
        //free in an order different from both mapping order and index order
        unmap(mapper, 1);
        unmap(mapper, cacheSize - 1);
        unmap(mapper, 0);
        base.takeCalls();
        map(mapper, 20);
        map(mapper, 21);
        map(mapper, 22);
        assertEquals(List.of("map:20", "unmap:1", "map:21", "unmap:" + (cacheSize - 1), "map:22", "unmap:0"),
                base.takeCalls());
    }

    @ParameterizedTest(name = "cacheSize={0}, deferUnmapping={1}")
    @CsvSource({"1, false", "2, false", "3, false", "4, false", "7, false",
            "1, true", "2, true", "3, true", "4, true", "7, true"})
    void movingMappingKeepsCacheBounded(final int cacheSize, final boolean deferUnmapping) {
        final LruCacheRegionMapper mapper = new LruCacheRegionMapper(base, cacheSize, deferUnmapping);
        //simulates a single mapping moving around: map the new region, then unmap the previous one
        int previous = -1;
        for (int i = 0; i < 200; i++) {
            final int region = (i * 7) % 40;
            map(mapper, region);
            if (previous >= 0 && previous != region) {
                unmap(mapper, previous);
            }
            previous = region;
            assertTrue(cached(mapper) <= cacheSize, mapper + " at i=" + i);
            assertTrue(base.mapped() <= Math.max(cacheSize, 2), base + " at i=" + i);
        }
        mapper.close();
        assertEquals(0, base.mapped());
    }

    private static int cached(final LruCacheRegionMapper mapper) {
        final String description = mapper.toString();
        final String prefix = "LruCacheRegionMapper:cached=";
        assertTrue(description.startsWith(prefix), description);
        return Integer.parseInt(description.substring(prefix.length(), description.indexOf('|')));
    }

    @ParameterizedTest(name = "deferUnmapping={0}")
    @ValueSource(booleans = {false, true})
    void closeUnmapsAllCachedRegionsAndClosesBase(final boolean deferUnmapping) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, 4, deferUnmapping);
        map(mapper, 1);
        map(mapper, 2);
        map(mapper, 3);
        unmap(mapper, 2);
        base.takeCalls();
        mapper.close();
        assertEquals(0, base.mapped());
        assertTrue(mapper.isClosed());
        final List<String> calls = base.takeCalls();
        assertEquals("close", calls.get(calls.size() - 1), calls.toString());
        assertThrows(IllegalStateException.class, () -> mapper.map(position(1)));
    }

    @ParameterizedTest(name = "deferUnmapping={0}")
    @ValueSource(booleans = {false, true})
    void unmapOfUncachedRegionPassesThroughToBase(final boolean deferUnmapping) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, 4, deferUnmapping);
        final long address = base.mapInternal(position(2), RecordingRegionMapper.REGION_SIZE);
        base.takeCalls();
        mapper.unmap(position(2), address);
        assertEquals(List.of("unmap:2"), base.takeCalls());
        assertEquals(0, base.mapped());
    }

    @ParameterizedTest(name = "deferUnmapping={0}")
    @ValueSource(booleans = {false, true})
    void unmapOfEvictedRegionAfterRemappingPassesThroughToBase(final boolean deferUnmapping) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, 1, deferUnmapping);
        final long oldAddress = mapper.map(position(0));
        mapper.map(position(1));
        final long newAddress = mapper.map(position(0));
        assertNotEquals(oldAddress, newAddress, "region 0 mapped again while evicted mapping is still live");
        base.takeCalls();

        mapper.unmap(position(0), oldAddress);
        assertEquals(List.of("unmap:0"), base.takeCalls(), "old address unmapped in base");
        assertEquals(newAddress, mapper.map(position(0)));
        assertEquals(List.of(), base.takeCalls(), "new address still cached");
        unmap(mapper, 1);
        assertEquals(List.of("unmap:1"), base.takeCalls(), "evicted region 1 passes through to base");
    }

    @ParameterizedTest(name = "deferUnmapping={0}")
    @ValueSource(booleans = {false, true})
    void mappingFailureIsNotCached(final boolean deferUnmapping) {
        final RegionMapper mapper = new LruCacheRegionMapper(base, 4, deferUnmapping);
        final long beyondEnd = base.maxPosition() + position(1);
        assertEquals(NULL_ADDRESS, mapper.map(beyondEnd));
        assertFalse(mapper.isMappedInCache(beyondEnd));
        map(mapper, 1);
        assertTrue(mapper.isMappedInCache(position(1)));
    }
}
