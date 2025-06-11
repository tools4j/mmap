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
import org.tools4j.mmap.region.impl.DynamicMappingImpl;
import org.tools4j.mmap.region.impl.ElasticMappingImpl;
import org.tools4j.mmap.region.impl.FileInitialiser;
import org.tools4j.mmap.region.impl.FixedMapping;
import org.tools4j.mmap.region.impl.MappingPoolImpl;
import org.tools4j.mmap.region.impl.NullMapping;
import org.tools4j.mmap.region.unsafe.FileMapper;
import org.tools4j.mmap.region.unsafe.FileMappers;
import org.tools4j.mmap.region.unsafe.FixedSizeFileMapper;
import org.tools4j.mmap.region.unsafe.RegionMapper;
import org.tools4j.mmap.region.unsafe.RegionMappers;

import java.io.File;

public enum Mappings {
    ;

    /**
     * Returns an empty, unmapped, non-closeable null-mapping.
     * @return the null mapping singleton instance
     */
    public static Mapping nullMapping() {
        return NullMapping.INSTANCE;
    }

    public static Mapping fixedSizeMapping(final File file, final int size, final AccessMode accessMode) {
        final FileInitialiser initialiser = FileInitialiser.zeroBytes(accessMode, size);
        return fixedSizeMapping(new FixedSizeFileMapper(file, size, accessMode, initialiser), true);
    }

    @Unsafe
    public static Mapping fixedSizeMapping(final FixedSizeFileMapper fileMapper, final boolean closeFileMapperOnClose) {
        return new FixedMapping(fileMapper, closeFileMapperOnClose);
    }

    public static DynamicMapping dynamicMapping(final File file, final AccessMode accessMode) {
        return dynamicMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static DynamicMapping dynamicMapping(final File file, final AccessMode accessMode, final MappingConfig config) {
        return dynamicMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    public static DynamicMapping dynamicMapping(final File file,
                                                final AccessMode accessMode,
                                                final FileInitialiser fileInitialiser,
                                                final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return dynamicMapping(regionMapper, true);
    }

    @Unsafe
    public static DynamicMapping dynamicMapping(final RegionMapper regionMapper, final boolean closeFileMapperOnClose) {
        return new DynamicMappingImpl(regionMapper, closeFileMapperOnClose);
    }

    public static AdaptiveMapping adaptiveMapping(final File file, final AccessMode accessMode) {
        return adaptiveMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static AdaptiveMapping adaptiveMapping(final File file, final AccessMode accessMode, final MappingConfig config) {
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
    public static AdaptiveMapping adaptiveMapping(final RegionMapper regionMapper, final boolean closeFileMapperOnClose) {
        return new AdaptiveMappingImpl(regionMapper, closeFileMapperOnClose);
    }

    public static ElasticMapping elasticMapping(final File file, final AccessMode accessMode) {
        return elasticMapping(file, accessMode, MappingConfig.getDefault());
    }

    public static ElasticMapping elasticMapping(final File file, final AccessMode accessMode, final MappingConfig config) {
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
    public static ElasticMapping elasticMapping(final RegionMapper regionMapper, final boolean closeFileMapperOnClose) {
        return new ElasticMappingImpl(regionMapper, closeFileMapperOnClose);
    }

    public static MappingPool mappingPool(final File file, final AccessMode accessMode) {
        return mappingPool(file, accessMode, MappingConfig.getDefault());
    }

    public static MappingPool mappingPool(final File file, final AccessMode accessMode, final MappingConfig config) {
        return mappingPool(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final FileInitialiser fileInitialiser,
                                          final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return mappingPool(regionMapper, true);
    }

    @Unsafe
    public static MappingPool mappingPool(final RegionMapper regionMapper, final boolean closeFileMapperOnClose) {
        return new MappingPoolImpl(regionMapper, closeFileMapperOnClose, 64);//FIXME parameterize pool size
    }

}
