/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.mmap.region.impl;

import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import org.tools4j.mmap.region.api.AsyncMappingProcessor;
import org.tools4j.mmap.region.api.AsyncRegionMapper;
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionFactory;
import org.tools4j.mmap.region.api.RegionMapper;

import static org.tools4j.mmap.region.impl.AbstractRegion.ensurePowerOfTwo;

public class RegionRing implements Region {
    private final RegionMapper[] regionMappers;
    private final Region[] regions;
    private final int regionSize;
    private final int regionsToMapAhead;
    private final int regionsLengthMask;

    private long currentAbsoluteIndex = -1;

    public static RegionRing sync(final int ringSize,
                                  final int regionSize,
                                  final Supplier<? extends FileChannel> fileChannelSupplier,
                                  final FileSizeEnsurer fileSizeEnsurer,
                                  final FileChannel.MapMode mapMode) {
        return sync(ringSize, ringSize - 1, regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
    }

    public static RegionRing sync(final int ringSize,
                                  final int regionsToMapAhead,
                                  final int regionSize,
                                  final Supplier<? extends FileChannel> fileChannelSupplier,
                                  final FileSizeEnsurer fileSizeEnsurer,
                                  final FileChannel.MapMode mapMode) {
        final RegionMapper[] mappers = new RegionMapper[ringSize];
        for (int i = 0; i < ringSize; i++) {
            mappers[i] = new SyncRegionMapper(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
        }
        return new RegionRing(SyncRegion::new, regionsToMapAhead, mappers);
    }

    public static AsyncRegionRing async(final RegionFactory<? extends AsyncRegionMapper> mapperFactory,
                                        final long mappingTimeout,
                                        final TimeUnit unit,
                                        final int ringSize,
                                        final int regionSize,
                                        final Supplier<? extends FileChannel> fileChannelSupplier,
                                        final FileSizeEnsurer fileSizeEnsurer,
                                        final FileChannel.MapMode mapMode) {
        return async(mapperFactory, mappingTimeout, unit, ringSize, ringSize - 1, regionSize,
                fileChannelSupplier, fileSizeEnsurer, mapMode);
    }

    public static AsyncRegionRing async(final RegionFactory<? extends AsyncRegionMapper> mapperFactory,
                                        final long mappingTimeout,
                                        final TimeUnit unit,
                                        final int ringSize,
                                        final int regionsToMapAhead,
                                        final int regionSize,
                                        final Supplier<? extends FileChannel> fileChannelSupplier,
                                        final FileSizeEnsurer fileSizeEnsurer,
                                        final FileChannel.MapMode mapMode) {
        final AsyncRegionMapper[] mappers = new AsyncRegionMapper[ringSize];
        for (int i = 0; i < ringSize; i++) {
            mappers[i] = mapperFactory.create(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
        }
        return new AsyncRegionRing(mapper -> new AsyncRegion(mapper, mappingTimeout, unit), regionsToMapAhead, mappers);
    }

    @SafeVarargs
    public <T extends RegionMapper> RegionRing(final Function<? super T, ? extends Region> regionFactory,
                                               final T... regionMappers) {
        this(regionFactory, regionMappers.length - 1, regionMappers);
    }

    @SafeVarargs
    public <T extends RegionMapper> RegionRing(final Function<? super T, ? extends Region> regionFactory,
                                               final int regionsToMapAhead,
                                               final T... regionMappers) {
        Objects.requireNonNull(regionFactory);
        if (regionMappers.length == 0) {
            throw new IllegalArgumentException("Ring size must be at least 1");
        }
        ensurePowerOfTwo("Region ring size", regionMappers.length);
        if (regionsToMapAhead< 0 || regionsToMapAhead >= regionMappers.length) {
            throw new IllegalArgumentException("Regions to map ahead must be in [0, " + (regionMappers.length-1) + "] but was " +
                regionsToMapAhead);
        }
        for (int i = 0; i < regionMappers.length; i++) {
            final RegionMapper mapper = Objects.requireNonNull(regionMappers[i]);
            if (i == 0) {
                ensurePowerOfTwo("Region size", mapper.size());
            } else {
                if (mapper.size() != regionMappers[0].size()) {
                    throw new IllegalArgumentException("Incompatible region sizes, all sizes must be " +
                            regionMappers[0].size() + " but also found " + mapper.size());
                }
            }
        }
        this.regionMappers = regionMappers;
        this.regionSize = regionMappers[0].size();
        this.regionsToMapAhead = regionsToMapAhead;
        this.regionsLengthMask = regionMappers.length - 1;
        this.regions = new Region[regionMappers.length];
        for (int i = 0; i < regions.length; i++) {
            regions[i] = regionFactory.apply(regionMappers[i]);
        }
    }

    @Override
    public int wrap(final long position, final DirectBuffer buffer) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative" + position);
        }
        Objects.requireNonNull(buffer);

        final long absoluteIndex = position / regionSize;
        final int length = regions[(int) (absoluteIndex & regionsLengthMask)].wrap(position, buffer);
        if (length > 0) {
            if (currentAbsoluteIndex < absoluteIndex) { // moving forward
                for (long mapIndex = absoluteIndex + 1; mapIndex <= absoluteIndex + regionsToMapAhead; mapIndex++) {
                    regionMappers[(int) (mapIndex & regionsLengthMask)].map(mapIndex * regionSize);
                }
                if (currentAbsoluteIndex >= 0) {
                    regionMappers[(int) (currentAbsoluteIndex & regionsLengthMask)].unmap();
                }
            } else if (currentAbsoluteIndex > absoluteIndex) { // moving backward
                for (long mapIndex = absoluteIndex - 1; mapIndex >= 0 && mapIndex >= absoluteIndex - regionsToMapAhead; mapIndex--) {
                    regionMappers[(int) (mapIndex & regionsLengthMask)].map(mapIndex * regionSize);
                }
                if (currentAbsoluteIndex >= 0) {
                    regionMappers[(int) (currentAbsoluteIndex & regionsLengthMask)].unmap();
                }
            }
        }
        currentAbsoluteIndex = absoluteIndex;
        return length;
    }

    @Override
    public int unwrap(final DirectBuffer buffer) {
        int result = -1;
        for (final Region region : regions) {
            result = Math.max(result, region.unwrap(buffer));
        }
        return result;
    }

    @Override
    public int size() {
        return regionSize;
    }

    public static class AsyncRegionRing extends RegionRing implements AsyncMappingProcessor {

        public AsyncRegionRing(final Function<? super AsyncRegionMapper, ? extends AsyncRegion> regionFactory,
                               final AsyncRegionMapper... regionMappers) {
            super(regionFactory, regionMappers);
        }

        public AsyncRegionRing(final Function<? super AsyncRegionMapper, ? extends AsyncRegion> regionFactory,
                               final int regionsToMapAhead,
                               final AsyncRegionMapper... regionMappers) {
            super(regionFactory, regionsToMapAhead, regionMappers);
        }

        @Override
        public boolean processMappingRequests() {
            boolean processed = false;
            for (final Region region : super.regions) {
                processed |= ((AsyncRegion)region).processMappingRequests();
            }
            return processed;
        }
    }

}
