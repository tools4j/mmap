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

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.core.idle.Idler;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.DynamicMapping;
import org.tools4j.mmap.mapping.api.DynamicMutableMapping;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;
import org.tools4j.mmap.mapping.impl.DynamicMappingImpl;
import org.tools4j.mmap.mapping.impl.RegionMetricsImpl;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for the region mapper decorator chains assembled by {@link RegionMappers#create(FileMapper, RegionMetrics,
 * MappingStrategyConfig)}: every combination of sync/async mapping, sync/async unmapping, ring and LRU cache settings
 * writes and reads back data, and is assembled from the expected mappers.
 */
class RegionMapperChainTest {
    /** Idler parking for 1 ms, so that test runtimes don't keep a CPU busy */
    private static Idler parkingIdler() {
        return Idler.backoff(0, 0, 1_000_000, 1_000_000);
    }

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int REGIONS = 8;
    private static final RegionMetrics REGION_METRICS = new RegionMetricsImpl(REGION_SIZE, 0);

    @TempDir
    Path tempDir;

    static List<Arguments> strategies() {
        final List<Arguments> strategies = new ArrayList<>();
        for (final boolean asyncMapping : new boolean[]{false, true}) {
            for (final boolean asyncUnmapping : new boolean[]{false, true}) {
                for (final int cacheSize : new int[]{1, 4}) {
                    for (final int lruCacheSize : new int[]{0, 2}) {
                        for (final boolean deferUnmapping : new boolean[]{false, true}) {
                            strategies.add(Arguments.of(asyncMapping, asyncUnmapping, cacheSize, lruCacheSize,
                                    deferUnmapping));
                        }
                    }
                }
            }
        }
        return strategies;
    }

    private static MappingStrategyConfig strategy(final boolean asyncMapping,
                                                  final boolean asyncUnmapping,
                                                  final int cacheSize,
                                                  final int lruCacheSize,
                                                  final boolean deferUnmapping) {
        final MappingStrategyConfigurator config = MappingStrategyConfig.configure()
                .cacheSize(cacheSize)
                .lruCacheSize(lruCacheSize)
                .deferUnmapping(deferUnmapping)
                .asyncMapping(false)
                .asyncUnmapping(false);
        if (asyncMapping) {
            config.asyncMapping(cfg -> cfg.regionsToMapAhead(2)
                    .mappingRuntimeUsing(parkingIdler()));
        }
        if (asyncUnmapping) {
            config.asyncUnmapping(cfg -> cfg.unmappingRuntimeUsing(parkingIdler()));
        }
        return config.toImmutableConfig();
    }

    private static String expectedChainStart(final boolean asyncMapping,
                                             final boolean asyncUnmapping,
                                             final int cacheSize,
                                             final int lruCacheSize,
                                             final boolean deferUnmapping) {
        if (asyncMapping) {
            return "AsyncRunAheadRegionMapper:";
        }
        if (lruCacheSize > 0) {
            return cacheSize > 1 ? "RingCacheRegionMapper:" : "LruCacheRegionMapper:";
        }
        final boolean cached = cacheSize > 1 || deferUnmapping;
        return cached ? "RingCacheRegionMapper:" : asyncUnmapping ? "SyncRegionMapperAsyncUnmapper:" :
                "SyncRegionMapper:";
    }

    private FileMapper fileMapper(final File file) {
        return fileMapper(file, 0);
    }

    private FileMapper fileMapper(final File file, final int overlapSize) {
        return new FixedSizeFileMapper(file, (long) REGIONS * REGION_SIZE + overlapSize, AccessMode.READ_WRITE,
                FileInitialiser.LAZY);
    }

    private static long valueFor(final int region) {
        return 0x7700_0000_0000_0000L | region;
    }

    @ParameterizedTest(name = "asyncMapping={0}, asyncUnmapping={1}, cacheSize={2}, lruCacheSize={3}, " +
            "deferUnmapping={4}")
    @MethodSource("strategies")
    void writeAndReadBack(final boolean asyncMapping,
                          final boolean asyncUnmapping,
                          final int cacheSize,
                          final int lruCacheSize,
                          final boolean deferUnmapping) {
        final File file = tempDir.resolve("chain.map").toFile();
        final MappingStrategyConfig config = strategy(asyncMapping, asyncUnmapping, cacheSize, lruCacheSize,
                deferUnmapping);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper(file), REGION_METRICS, config);
        final String chain = regionMapper.toString();
        assertTrue(chain.startsWith(expectedChainStart(asyncMapping, asyncUnmapping, cacheSize, lruCacheSize,
                deferUnmapping)), chain);
        assertEquals(lruCacheSize > 0, chain.contains("LruCacheRegionMapper:"), chain);
        assertTrue(chain.contains(asyncUnmapping ? "SyncRegionMapperAsyncUnmapper:" : "SyncRegionMapper:"), chain);

        try (final DynamicMutableMapping mapping = new DynamicMappingImpl(regionMapper, true)) {
            for (int region = 0; region < REGIONS; region++) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE), "region " + region);
                mapping.buffer().putLong(8, valueFor(region));
            }
            for (int region = REGIONS - 1; region >= 0; region--) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE), "region " + region);
                assertEquals(valueFor(region), mapping.buffer().getLong(8), "region " + region);
            }
        }
        assertTrue(regionMapper.isClosed());

        final RegionMapper syncMapper = RegionMappers.create(fileMapper(file), REGION_METRICS,
                MappingStrategyConfig.getDefaultSync());
        try (final DynamicMapping mapping = new DynamicMappingImpl(syncMapper, true)) {
            for (int region = 0; region < REGIONS; region++) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE), "region " + region);
                assertEquals(valueFor(region), mapping.buffer().getLong(8), "reopened, region " + region);
            }
        }
    }

    /**
     * Every chain with overlap: values written across region ends are read back through the next region, and the
     * last region of the file is mapped with its overlap too, into the tail of the file beyond the max file size.
     */
    @ParameterizedTest(name = "asyncMapping={0}, asyncUnmapping={1}, cacheSize={2}, lruCacheSize={3}, " +
            "deferUnmapping={4}")
    @MethodSource("strategies")
    void writeAcrossRegionEndsAndReadBackWithOverlap(final boolean asyncMapping,
                                                     final boolean asyncUnmapping,
                                                     final int cacheSize,
                                                     final int lruCacheSize,
                                                     final boolean deferUnmapping) {
        final File file = tempDir.resolve("chain-overlap.map").toFile();
        final int overlapSize = REGION_SIZE;
        final RegionMetrics metrics = new RegionMetricsImpl(REGION_SIZE, overlapSize);
        final MappingStrategyConfig config = strategy(asyncMapping, asyncUnmapping, cacheSize, lruCacheSize,
                deferUnmapping);
        try (final DynamicMutableMapping mapping = new DynamicMappingImpl(
                RegionMappers.create(fileMapper(file, overlapSize), metrics, config), true)) {
            for (int region = 1; region < REGIONS; region++) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE - Integer.BYTES, Long.BYTES), "region " + region);
                mapping.buffer().putLong(0, valueFor(region));
            }
            final long lastPosition = (long) REGIONS * REGION_SIZE - Integer.BYTES;
            assertTrue(mapping.moveTo(lastPosition, mapping.safeLength(Integer.BYTES)), "last region");
            mapping.buffer().putLong(0, valueFor(REGIONS));
            assertTrue(mapping.moveTo((REGIONS - 1L) * REGION_SIZE, DynamicMapping.STRETCH_LENGTH));
            assertEquals(REGION_SIZE + overlapSize, mapping.length(), "last region with overlap");
            assertEquals(valueFor(REGIONS), mapping.buffer().getLong(REGION_SIZE - Integer.BYTES), "tail");
            for (int region = REGIONS - 1; region >= 1; region--) {
                assertTrue(mapping.moveTo((long) region * REGION_SIZE, Integer.BYTES), "region " + region);
                assertEquals((int) (valueFor(region) >>> Integer.SIZE), mapping.buffer().getInt(0),
                        "next region, region " + region);
                assertTrue(mapping.moveTo((long) region * REGION_SIZE - Integer.BYTES, Long.BYTES), "region " + region);
                assertEquals(valueFor(region), mapping.buffer().getLong(0), "region " + region);
            }
        }
        assertEquals((long) REGIONS * REGION_SIZE + overlapSize, file.length());
    }
}
