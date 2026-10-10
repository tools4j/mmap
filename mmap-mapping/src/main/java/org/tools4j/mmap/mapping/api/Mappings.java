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
package org.tools4j.mmap.mapping.api;

import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.impl.AdaptiveMappingImpl;
import org.tools4j.mmap.mapping.impl.ElasticMappingImpl;
import org.tools4j.mmap.mapping.impl.FixedMappingImpl;
import org.tools4j.mmap.mapping.impl.MappingPoolImpl;
import org.tools4j.mmap.mapping.impl.NullMapping;
import org.tools4j.mmap.mapping.impl.RegionMappingImpl;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.FileMappers;
import org.tools4j.mmap.mapping.unsafe.FixedSizeFileMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMappers;

import java.io.File;

import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultInitialMappingPoolSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateFixedMappingLength;

/**
 * A facade class with static methods to create mappings and mapping pools.
 * <p>
 * Unless noted otherwise, all method arguments are required; passing {@code null} for any reference-type argument
 * results in a {@link NullPointerException}.
 */
public enum Mappings {
    ;

    /**
     * Returns an empty, unmapped null-mapping.
     * @return the null mapping singleton instance
     */
    public static Mapping nullMapping() {
        return NullMapping.INSTANCE;
    }

    /**
     * Creates a fixed mapping over the whole of the specified file, with a length determined by the actual file size.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @return a new fixed mapping covering the whole file
     */
    public static FixedMapping fixedMapping(final File file, final AccessMode accessMode) {
        return fixedMapping(file, accessMode, 0L);
    }

    /**
     * Creates a fixed mapping over the specified file from the given offset to the end of the file, with a length
     * determined by the actual file size.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param offset     the start offset in the file
     * @return a new fixed mapping from the offset to the end of the file
     */
    public static FixedMapping fixedMapping(final File file,
                                            final AccessMode accessMode,
                                            final long offset) {
        return fixedMapping(file, accessMode, offset, -1);
    }

    /**
     * Creates a fixed mapping over the specified slice of the given file.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param offset     the start offset in the file
     * @param length     the length of the mapped slice, or -1 to map from offset to the actual end of the file
     * @return a new fixed mapping for the specified file slice
     * @throws IllegalArgumentException if the resulting mapping length exceeds the integer range
     */
    public static FixedMapping fixedMapping(final File file,
                                            final AccessMode accessMode,
                                            final long offset,
                                            final int length) {
        final long fileSize = length >= 0 ? offset + length : file.length();
        validateFixedMappingLength(offset, fileSize);
        final FileInitialiser initialiser = FileInitialiser.zeroBytes(accessMode, offset, fileSize);
        final FileMapper fileMapper = new FixedSizeFileMapper(file, fileSize, accessMode, initialiser);
        return fixedMapping(fileMapper, offset, (int)(fileSize - offset), true);
    }

    /**
     * Creates a fixed mapping over the specified slice of the file already mapped by the given file mapper. Closing
     * the returned mapping also closes the given file mapper.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link FileMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code fixedMapping} factory methods instead.
     *
     * @param fileMapper the file mapper providing access to the underlying file
     * @param offset     the start offset in the file
     * @param length     the length of the mapped slice
     * @return a new fixed mapping for the specified file slice
     */
    @Unsafe
    public static FixedMapping fixedMapping(final FileMapper fileMapper,
                                            final long offset,
                                            final int length) {
        return fixedMapping(fileMapper, offset, length, true);
    }

    /**
     * Creates a fixed mapping over the specified slice of the file already mapped by the given file mapper.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link FileMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code fixedMapping} factory methods instead.
     *
     * @param fileMapper             the file mapper providing access to the underlying file
     * @param offset                 the start offset in the file
     * @param length                 the length of the mapped slice
     * @param closeFileMapperOnClose if true the given file mapper is also closed when the returned mapping is closed
     * @return a new fixed mapping for the specified file slice
     */
    @Unsafe
    public static FixedMapping fixedMapping(final FileMapper fileMapper,
                                            final long offset,
                                            final int length,
                                            final boolean closeFileMapperOnClose) {
        return new FixedMappingImpl(fileMapper, offset, length, closeFileMapperOnClose);
    }

    /**
     * Creates a region mapping for the specified file using default mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @return a new region mapping for the given file
     * @see MappingConfig#getDefault()
     */
    public static RegionMapping regionMapping(final File file, final AccessMode accessMode) {
        return regionMapping(file, accessMode, MappingConfig.getDefault());
    }

    /**
     * Creates a region mapping for the specified file using the given mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param config     the mapping configuration to use
     * @return a new region mapping for the given file
     */
    public static RegionMapping regionMapping(final File file, final AccessMode accessMode, final MappingConfig config) {
        return regionMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    /**
     * Creates a region mapping for the specified file using the given file initialiser and mapping configuration.
     *
     * @param file            the file to map
     * @param accessMode      the access mode to open the file with
     * @param fileInitialiser the initialiser invoked once when the file is newly created or expanded
     * @param config          the mapping configuration to use
     * @return a new region mapping for the given file
     */
    public static RegionMapping regionMapping(final File file,
                                              final AccessMode accessMode,
                                              final FileInitialiser fileInitialiser,
                                              final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return regionMapping(regionMapper, true);
    }

    /**
     * Creates a region mapping backed by the given region mapper. Closing the returned mapping also closes the given
     * region mapper if {@code closeRegionMapperOnClose} is true.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link RegionMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code regionMapping} factory methods instead.
     *
     * @param regionMapper             the region mapper providing the underlying mapping operations
     * @param closeRegionMapperOnClose if true the given region mapper is also closed when the returned mapping is closed
     * @return a new region mapping backed by the given region mapper
     */
    @Unsafe
    public static RegionMapping regionMapping(final RegionMapper regionMapper, final boolean closeRegionMapperOnClose) {
        return new RegionMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    /**
     * Creates an elastic mapping for the specified file using default mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @return a new elastic mapping for the given file
     * @see MappingConfig#getDefault()
     */
    public static ElasticMapping elasticMapping(final File file, final AccessMode accessMode) {
        return elasticMapping(file, accessMode, MappingConfig.getDefault());
    }

    /**
     * Creates an elastic mapping for the specified file using the given mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param config     the mapping configuration to use
     * @return a new elastic mapping for the given file
     */
    public static ElasticMapping elasticMapping(final File file,
                                                final AccessMode accessMode,
                                                final MappingConfig config) {
        return elasticMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    /**
     * Creates an elastic mapping for the specified file using the given file initialiser and mapping configuration.
     *
     * @param file            the file to map
     * @param accessMode      the access mode to open the file with
     * @param fileInitialiser the initialiser invoked once when the file is newly created or expanded
     * @param config          the mapping configuration to use
     * @return a new elastic mapping for the given file
     */
    public static ElasticMapping elasticMapping(final File file,
                                                final AccessMode accessMode,
                                                final FileInitialiser fileInitialiser,
                                                final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return elasticMapping(regionMapper, true);
    }

    /**
     * Creates an elastic mapping backed by the given region mapper. Closing the returned mapping also closes the
     * given region mapper if {@code closeRegionMapperOnClose} is true.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link RegionMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code elasticMapping} factory methods instead.
     *
     * @param regionMapper             the region mapper providing the underlying mapping operations
     * @param closeRegionMapperOnClose if true the given region mapper is also closed when the returned mapping is closed
     * @return a new elastic mapping backed by the given region mapper
     */
    @Unsafe
    public static ElasticMapping elasticMapping(final RegionMapper regionMapper,
                                                final boolean closeRegionMapperOnClose) {
        return new ElasticMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    /**
     * Creates an adaptive mapping for the specified file using default mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @return a new adaptive mapping for the given file
     * @see MappingConfig#getDefault()
     */
    public static AdaptiveMapping adaptiveMapping(final File file, final AccessMode accessMode) {
        return adaptiveMapping(file, accessMode, MappingConfig.getDefault());
    }

    /**
     * Creates an adaptive mapping for the specified file using the given mapping configuration.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param config     the mapping configuration to use
     * @return a new adaptive mapping for the given file
     */
    public static AdaptiveMapping adaptiveMapping(final File file,
                                                  final AccessMode accessMode,
                                                  final MappingConfig config) {
        return adaptiveMapping(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config);
    }

    /**
     * Creates an adaptive mapping for the specified file using the given file initialiser and mapping configuration.
     *
     * @param file            the file to map
     * @param accessMode      the access mode to open the file with
     * @param fileInitialiser the initialiser invoked once when the file is newly created or expanded
     * @param config          the mapping configuration to use
     * @return a new adaptive mapping for the given file
     */
    public static AdaptiveMapping adaptiveMapping(final File file,
                                                  final AccessMode accessMode,
                                                  final FileInitialiser fileInitialiser,
                                                  final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return adaptiveMapping(regionMapper, true);
    }

    /**
     * Creates an adaptive mapping backed by the given region mapper. Closing the returned mapping also closes the
     * given region mapper if {@code closeRegionMapperOnClose} is true.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link RegionMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code adaptiveMapping} factory methods instead.
     *
     * @param regionMapper             the region mapper providing the underlying mapping operations
     * @param closeRegionMapperOnClose if true the given region mapper is also closed when the returned mapping is closed
     * @return a new adaptive mapping backed by the given region mapper
     */
    @Unsafe
    public static AdaptiveMapping adaptiveMapping(final RegionMapper regionMapper,
                                                  final boolean closeRegionMapperOnClose) {
        return new AdaptiveMappingImpl(regionMapper, closeRegionMapperOnClose);
    }

    /**
     * Creates a mapping pool for the specified file using default mapping configuration and the default initial pool
     * size.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @return a new mapping pool for the given file
     * @see MappingConfig#getDefault()
     */
    public static MappingPool mappingPool(final File file, final AccessMode accessMode) {
        return mappingPool(file, accessMode, MappingConfig.getDefault());
    }

    /**
     * Creates a mapping pool for the specified file using the given mapping configuration and the default initial
     * pool size.
     *
     * @param file       the file to map
     * @param accessMode the access mode to open the file with
     * @param config     the mapping configuration to use
     * @return a new mapping pool for the given file
     */
    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final MappingConfig config) {
        return mappingPool(file, accessMode, config, defaultInitialMappingPoolSize());
    }

    /**
     * Creates a mapping pool for the specified file using the given mapping configuration and initial pool size.
     *
     * @param file            the file to map
     * @param accessMode      the access mode to open the file with
     * @param config          the mapping configuration to use
     * @param initialPoolSize the expected number of mappings to be acquired from the pool, used as a capacity hint
     *                        for its internal tracking structures (mappings themselves are still created lazily,
     *                        on each acquire call)
     * @return a new mapping pool for the given file
     */
    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final int initialPoolSize) {
        return mappingPool(file, accessMode, FileInitialiser.zeroBytes(accessMode, 0), config, initialPoolSize);
    }

    /**
     * Creates a mapping pool for the specified file using the given file initialiser, mapping configuration and
     * initial pool size.
     *
     * @param file            the file to map
     * @param accessMode      the access mode to open the file with
     * @param fileInitialiser the initialiser invoked once when the file is newly created or expanded
     * @param config          the mapping configuration to use
     * @param initialPoolSize the expected number of mappings to be acquired from the pool, used as a capacity hint
     *                        for its internal tracking structures (mappings themselves are still created lazily,
     *                        on each acquire call)
     * @return a new mapping pool for the given file
     */
    public static MappingPool mappingPool(final File file,
                                          final AccessMode accessMode,
                                          final FileInitialiser fileInitialiser,
                                          final MappingConfig config,
                                          final int initialPoolSize) {
        final FileMapper fileMapper = FileMappers.create(file, accessMode, fileInitialiser, config);
        final RegionMapper regionMapper = RegionMappers.create(fileMapper, config.mappingStrategy());
        return mappingPool(regionMapper, initialPoolSize);
    }

    /**
     * Creates a mapping pool backed by the given region mapper, sized for the specified expected number of mappings.
     * <p>
     * <b>NOTE:</b> Using this method directly is unsafe and could lead to a JVM crash in the worst case. It exposes
     * the underlying {@link RegionMapper} directly, bypassing the usual {@code File}/{@link AccessMode}-based
     * construction. Application code should not normally need to call it directly; prefer one of the other
     * {@code mappingPool} factory methods instead.
     *
     * @param regionMapper    the region mapper providing the underlying mapping operations
     * @param initialPoolSize the expected number of mappings to be acquired from the pool, used as a capacity hint
     *                        for its internal tracking structures (mappings themselves are still created lazily,
     *                        on each acquire call)
     * @return a new mapping pool backed by the given region mapper
     */
    @Unsafe
    public static MappingPool mappingPool(final RegionMapper regionMapper, final int initialPoolSize) {
        return new MappingPoolImpl(regionMapper, initialPoolSize);
    }
}
