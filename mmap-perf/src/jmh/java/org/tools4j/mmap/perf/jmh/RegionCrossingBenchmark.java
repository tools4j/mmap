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
package org.tools4j.mmap.perf.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.Mappings;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.FileMappers;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMappers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The cost of an elastic mapping moving to another region, all regions cached so that no region is actually mapped,
 * with the call from the mapping into its region mapper seeing one region mapper class, or three as soon as an
 * application uses several mapping strategies.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED")
public class RegionCrossingBenchmark {
    private static final int REGION_SIZE = 64 * 1024;
    private static final int REGIONS = 4;//crossed in turn, fewer than cached, a power of two
    private static final int CACHE_SIZE = 8;
    private static final int POLLUTION_ROUNDS = 200_000;

    /** Which region mapper classes the elastic mappings' calls into their region mappers see. */
    public enum Profile {
        /** Only the ring cache region mapper measured */
        MONOMORPHIC,
        /** The ring cache, LRU cache and reference counting region mappers, round-robin before measuring */
        MEGAMORPHIC
    }

    @Param({"MONOMORPHIC", "MEGAMORPHIC"})
    public Profile profile;

    private final List<ElasticMapping> mappings = new ArrayList<>();
    private Path directory;
    private ElasticMapping mapping;
    private int region;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        directory = Files.createTempDirectory("region-crossing");
        mapping = elasticMapping("ring", fileMapper -> RegionMappers.createSyncRegionMapper(fileMapper, REGION_SIZE,
                CACHE_SIZE, 0, true));
        if (profile == Profile.MEGAMORPHIC) {
            final ElasticMapping lru = elasticMapping("lru", fileMapper -> RegionMappers.createSyncRegionMapper(
                    fileMapper, REGION_SIZE, 1, CACHE_SIZE, true));
            final ElasticMapping refCount = elasticMapping("ref-count", fileMapper ->
                    RegionMappers.createRefCountRegionMapper(RegionMappers.createSyncRegionMapper(fileMapper,
                            REGION_SIZE, CACHE_SIZE, 0, true), CACHE_SIZE));
            for (int i = 0; i < POLLUTION_ROUNDS; i++) {
                final long position = position(i);
                move(mapping, position);
                move(lru, position);
                move(refCount, position);
            }
        }
    }

    private interface RegionMapperFactory {
        RegionMapper create(FileMapper fileMapper);
    }

    private ElasticMapping elasticMapping(final String name, final RegionMapperFactory factory) {
        final File file = directory.resolve(name + ".map").toFile();
        final MappingConfig config = MappingConfig.configure()
                .regionSize(REGION_SIZE)
                .maxFileSize((long) REGION_SIZE * REGIONS)
                .expandFile(false)
                .rollFiles(false)
                .toImmutableConfig();
        final ElasticMapping elasticMapping = Mappings.elasticMapping(
                factory.create(FileMappers.create(file, AccessMode.READ_WRITE, config)), true);
        mappings.add(elasticMapping);
        for (int i = 0; i < REGIONS; i++) {
            move(elasticMapping, position(i));//maps each region once, cached from then on
        }
        return elasticMapping;
    }

    private static long position(final int region) {
        return (long) (region & (REGIONS - 1)) * REGION_SIZE + 64;
    }

    private static void move(final ElasticMapping mapping, final long position) {
        if (!mapping.moveTo(position)) {
            throw new IllegalStateException("Mapping " + mapping + " not moved to position " + position);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws IOException {
        mappings.forEach(ElasticMapping::close);
        try (final Stream<Path> paths = Files.walk(directory)) {
            for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Benchmark
    public boolean crossRegion() {
        region = (region + 1) & (REGIONS - 1);
        return mapping.moveTo(position(region));
    }
}
