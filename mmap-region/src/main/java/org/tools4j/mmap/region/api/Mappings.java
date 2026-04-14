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
package org.tools4j.mmap.region.api;

import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.AdaptiveMappingImpl;
import org.tools4j.mmap.region.impl.ElasticMappingImpl;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.FixedMappingImpl;
import org.tools4j.mmap.region.impl.NullMapping;
import org.tools4j.mmap.region.impl.RegionMappingImpl;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.FileMappers;
import org.tools4j.mmap.region.unsafe.FixedSizeFileMapper;
import org.tools4j.mmap.region.unsafe.MappingPoolImpl;
import org.tools4j.mmap.region.unsafe.RegionMapper;
import org.tools4j.mmap.region.unsafe.RegionMappers;

import java.io.File;

import static org.tools4j.mmap.region.config.MappingConfigurations.defaultInitialMappingPoolSize;

public enum Mappings {
    ;

    /**
     * Returns an empty, unmapped null-mapping.
     * @return the null mapping singleton instance
     */
    public static Mapping nullMapping() {
        return NullMapping.INSTANCE;
    }

    public static FixedMapping fixedSizeMapping(final File file, final int size, final AccessMode accessMode) {
        final FileInitialiser initialiser = FileInitialiser.zeroBytes(accessMode, size);
        return fixedSizeMapping(new FixedSizeFileMapper(file, size, accessMode, initialiser), true);
    }

    @Unsafe
    public static FixedMapping fixedSizeMapping(final FixedSizeFileMapper fileMapper,
                                                final boolean closeFileMapperOnClose) {
        return new FixedMappingImpl(fileMapper, closeFileMapperOnClose);
    }

    public static RegionMapping regionMapping(final File file, final AccessMode accessMode) {
        return regionMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static RegionMapping regionMapping(final File file, final AccessMode accessMode, final MappingConfig config) {
        return regionMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    public static RegionMapping regionMapping(final File file,
                                              final AccessMode accessMode,
                                              final FileInitialiser fileInitialiser,
                                              final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return regionMapping(regionMapper, true);
    }

    @Unsafe
    public static RegionMapping regionMapping(final RegionMapper regionMapper, final boolean closeRegionMapperOnClose) {
        return new RegionMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    public static ElasticMapping elasticMapping(final File file, final AccessMode accessMode) {
        return elasticMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static ElasticMapping elasticMapping(final File file,
                                                final AccessMode accessMode,
                                                final MappingConfig config) {
        return elasticMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    public static ElasticMapping elasticMapping(final File file,
                                                final AccessMode accessMode,
                                                final FileInitialiser fileInitialiser,
                                                final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return elasticMapping(regionMapper, true);
    }

    @Unsafe
    public static ElasticMapping elasticMapping(final RegionMapper regionMapper,
                                                final boolean closeRegionMapperOnClose) {
        return new ElasticMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    public static AdaptiveMapping adaptiveMapping(final File file, final AccessMode accessMode) {
        return adaptiveMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static AdaptiveMapping adaptiveMapping(final File file,
                                                  final AccessMode accessMode,
                                                  final MappingConfig config) {
        return adaptiveMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    public static AdaptiveMapping adaptiveMapping(final File file,
                                                  final AccessMode accessMode,
                                                  final FileInitialiser fileInitialiser,
                                                  final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return adaptiveMapping(regionMapper, true);
    }

    @Unsafe
    public static AdaptiveMapping adaptiveMapping(final RegionMapper regionMapper,
                                                  final boolean closeRegionMapperOnClose) {
        return new AdaptiveMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    public static MappingPool mappingPool(final File file, final AccessMode accessMode) {
        return mappingPool(file, accessMode, MappingConfig.getDefault());
    }

    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final MappingConfig config) {
        return mappingPool(file, accessMode, config, defaultInitialMappingPoolSize());
    }

    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final int initialPoolSize) {
        return mappingPool(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config, initialPoolSize);
    }

    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final FileInitialiser fileInitialiser,
                                          final MappingConfig config,
                                          final int initialPoolSize) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return mappingPool(regionMapper, initialPoolSize);
    }

    @Unsafe
    public static MappingPool mappingPool(final RegionMapper regionMapper, final int initialPoolSize) {
        return new MappingPoolImpl(regionMapper, initialPoolSize);
    }
}
