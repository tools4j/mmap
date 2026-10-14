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

import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.config.MappingStrategyConfig;
import org.tools4j.mmap.mapping.config.MappingStrategyConfigurator;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tools4j.mmap.mapping.impl.Constants.REGION_SIZE_GRANULARITY;

/**
 * Unit test for the region mapper decorator chains assembled by {@link RegionMappers#create(FileMapper,
 * MappingStrategyConfig)}: every combination of sync/async mapping, sync/async unmapping, ring and LRU cache settings
 * writes
 * and reads back data, and is assembled from the expected mappers.
 */
class RegionMapperChainTest {

    private static final int REGION_SIZE = (int) REGION_SIZE_GRANULARITY;
    private static final int REGIONS = 8;

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
                .regionSize(REGION_SIZE)
                .cacheSize(cacheSize)
                .lruCacheSize(lruCacheSize)
                .deferUnmapping(deferUnmapping)
                .asyncMapping(false)
                .asyncUnmapping(false);
        if (asyncMapping) {
            config.asyncMapping(cfg -> cfg.regionsToMapAhead(2)
                    .mappingRuntimeUsing(() -> new SleepingMillisIdleStrategy(1)));
        }
        if (asyncUnmapping) {
            config.asyncUnmapping(cfg -> cfg.unmappingRuntimeUsing(() -> new SleepingMillisIdleStrategy(1)));
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
        return new FixedSizeFileMapper(file, (long) REGIONS * REGION_SIZE, AccessMode.READ_WRITE,
                FileInitialiser.zeroBytes(AccessMode.READ_WRITE, 0));
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
        final RegionMapper regionMapper = RegionMappers.create(fileMapper(file), config);
        final String chain = regionMapper.toString();
        assertTrue(chain.startsWith(expectedChainStart(asyncMapping, asyncUnmapping, cacheSize, lruCacheSize,
                deferUnmapping)), chain);
        assertEquals(lruCacheSize > 0, chain.contains("LruCacheRegionMapper:"), chain);
        assertTrue(chain.contains(asyncUnmapping ? "SyncRegionMapperAsyncUnmapper:" : "SyncRegionMapper:"), chain);

        try (final ElasticMapping mapping = Mappings.elasticMapping(regionMapper, true)) {
            for (int region = 0; region < REGIONS; region++) {
                assertTrue(mapping.moveToRegion(region), "region " + region);
                mapping.buffer().putLong(8, valueFor(region));
            }
            for (int region = REGIONS - 1; region >= 0; region--) {
                assertTrue(mapping.moveToRegion(region), "region " + region);
                assertEquals(valueFor(region), mapping.buffer().getLong(8), "region " + region);
            }
        }
        assertTrue(regionMapper.isClosed());

        final RegionMapper syncMapper = RegionMappers.create(fileMapper(file), MappingStrategyConfig.configure(
                MappingStrategyConfig.getDefaultSync()).regionSize(REGION_SIZE).toImmutableConfig());
        try (final ElasticMapping mapping = Mappings.elasticMapping(syncMapper, true)) {
            for (int region = 0; region < REGIONS; region++) {
                assertTrue(mapping.moveToRegion(region), "region " + region);
                assertEquals(valueFor(region), mapping.buffer().getLong(8), "reopened, region " + region);
            }
        }
    }
}
