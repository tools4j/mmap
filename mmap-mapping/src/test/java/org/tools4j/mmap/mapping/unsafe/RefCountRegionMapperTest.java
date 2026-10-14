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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.unsafe.RecordingRegionMapper.address;
import static org.tools4j.mmap.mapping.unsafe.RecordingRegionMapper.position;

/**
 * Unit test for {@link RefCountRegionMapper}: a shared region is mapped once and only unmapped when its last
 * reference is released.
 */
class RefCountRegionMapperTest {

    private final RecordingRegionMapper base = new RecordingRegionMapper(64);
    private final RegionMapper mapper = RegionMappers.createRefCountRegionMapper(base, 4);

    @Test
    void delegatesMetricsAndAccessMode() {
        assertSame(base.regionMetrics(), mapper.regionMetrics());
        assertSame(base.accessMode(), mapper.accessMode());
    }

    @Test
    void sharedRegionIsMappedOnceAndUnmappedWithLastReference() {
        assertEquals(address(position(3)), mapper.map(position(3)));
        assertEquals(address(position(3)), mapper.map(position(3)));
        assertEquals(address(position(3)), mapper.map(position(3)));
        assertEquals(List.of("map:3"), base.takeCalls());
        assertTrue(mapper.isMappedInCache(position(3)));

        mapper.unmap(position(3), address(position(3)));
        mapper.unmap(position(3), address(position(3)));
        assertEquals(List.of(), base.takeCalls(), "references remaining");
        mapper.unmap(position(3), address(position(3)));
        assertEquals(List.of("unmap:3"), base.takeCalls());
        assertFalse(mapper.isMappedInCache(position(3)));

        assertEquals(address(position(3)), mapper.map(position(3)));
        assertEquals(List.of("map:3"), base.takeCalls(), "mapped again after last reference was released");
    }

    @Test
    void regionsAreCountedIndependently() {
        mapper.map(position(1));
        mapper.map(position(2));
        mapper.map(position(2));
        mapper.unmap(position(2), address(position(2)));
        mapper.unmap(position(1), address(position(1)));
        assertEquals(List.of("map:1", "map:2", "unmap:1"), base.takeCalls());
        assertEquals(1, base.mapped());
    }

    @Test
    void unmapOfUnknownOrMismatchedAddressIsRejected() {
        mapper.map(position(1));
        assertThrows(IllegalArgumentException.class, () -> mapper.unmap(position(1), address(position(2))));
        assertThrows(IllegalArgumentException.class, () -> mapper.unmap(position(2), address(position(2))));
        assertEquals(List.of("map:1"), base.takeCalls());
    }

    @Test
    void mappingFailureIsNotCounted() {
        final long beyondEnd = base.maxPosition() + position(1);
        assertEquals(NULL_ADDRESS, mapper.map(beyondEnd));
        assertFalse(mapper.isMappedInCache(beyondEnd));
        assertEquals(0, base.mapped());
    }

    @Test
    void closeUnmapsRegionsStillReferencedAndClosesBase() {
        mapper.map(position(1));
        mapper.map(position(1));
        mapper.map(position(2));
        base.takeCalls();
        mapper.close();
        assertEquals(0, base.mapped());
        assertTrue(mapper.isClosed());
        final List<String> calls = base.takeCalls();
        assertEquals(List.of("close"), calls.subList(calls.size() - 1, calls.size()), calls.toString());
        assertEquals(3, calls.size(), "one unmap per region, then close: " + calls);
        mapper.close();
        assertEquals(List.of(), base.takeCalls(), "second close has no effect");
    }

    @Test
    void toStringShowsCachedRegions() {
        mapper.map(position(1));
        assertTrue(mapper.toString().startsWith("RefCountRegionMapper:cached=1|baseMapper="), mapper.toString());
    }
}
