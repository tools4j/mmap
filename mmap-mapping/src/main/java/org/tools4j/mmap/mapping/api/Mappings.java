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

import org.tools4j.mmap.core.base.ErrorListener;
import org.tools4j.mmap.core.base.LifecycleListener;
import org.tools4j.mmap.core.config.CoreConfigurations;
import org.tools4j.mmap.core.internal.FileChannels;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.impl.DynamicMappingImpl;
import org.tools4j.mmap.mapping.impl.FixedMappingImpl;
import org.tools4j.mmap.mapping.impl.MappingPoolImpl;
import org.tools4j.mmap.mapping.impl.NullMapping;
import org.tools4j.mmap.mapping.impl.OpenModes;
import org.tools4j.mmap.mapping.unsafe.FileMapper;
import org.tools4j.mmap.mapping.unsafe.FileMappers;
import org.tools4j.mmap.mapping.unsafe.FixedSizeFileMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMapper;
import org.tools4j.mmap.mapping.unsafe.RegionMappers;

import java.io.File;

import static org.tools4j.mmap.mapping.config.MappingConfigurations.defaultInitialMappingPoolSize;
import static org.tools4j.mmap.mapping.impl.MappingValidations.validateFixedMappingLength;

/**
 * A facade class with static methods to create mappings and mapping pools.
 * <p>
 * Unless noted otherwise, all method arguments are required; passing {@code null} for any reference-type argument
 * results in a {@link NullPointerException}.
 * <p>
 * Methods mapping a file throw an {@link IllegalArgumentException} if the file cannot be opened as defined by the
 * given {@link OpenMode}, for instance if it must exist but does not.
 * <p>
 * Mappings for reading only are created for files opened with a {@link ReadOnlyOpenMode}, such as
 * {@link OpenMode#READ_ONLY}, and {@link MutableMapping mutable mappings} for files opened with a
 * {@link ReadWriteOpenMode}, such as {@link OpenMode#OPEN_OR_CREATE}; passing the other kind of open mode does not
 * compile.
 */
public enum Mappings {
    ;

    /**
     * Returns an empty, unmapped null-mapping.
     * @return the null mapping singleton instance
     */
    public static MutableMapping nullMapping() {
        return NullMapping.INSTANCE;
    }

    /**
     * Creates a fixed mapping for reading only over the whole of the specified file, with a length determined by the
     * actual file size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @return a new fixed mapping covering the whole file
     */
    public static FixedMapping fixedMapping(final File file, final ReadOnlyOpenMode openMode) {
        return fixedMapping(file, openMode, 0L);
    }

    /**
     * Creates a fixed mapping for reading only over the specified file from the given offset to the end of the file,
     * with a length determined by the actual file size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @param offset     the start offset in the file
     * @return a new fixed mapping from the offset to the end of the file
     */
    public static FixedMapping fixedMapping(final File file,
                                            final ReadOnlyOpenMode openMode,
                                            final long offset) {
        return fixedMapping(file, openMode, offset, -1);
    }

    /**
     * Creates a fixed mapping for reading only over the specified slice of the given file.
     * <p>
     * The file is mapped immediately, hence it must exist, also with {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @param offset     the start offset in the file
     * @param length     the length of the mapped slice, or -1 to map from offset to the actual end of the file
     * @return a new fixed mapping for the specified file slice
     * @throws IllegalArgumentException if the resulting mapping length exceeds the integer range
     * @throws IllegalStateException if the file cannot be mapped, for instance if it does not exist with
     *                               {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY}
     */
    public static FixedMapping fixedMapping(final File file,
                                            final ReadOnlyOpenMode openMode,
                                            final long offset,
                                            final int length) {
        return fixedMapping(file, openMode, offset, length, MappingConfigurations.defaultLifecycleListener(),
                CoreConfigurations.defaultErrorListener());
    }

    /**
     * Creates a fixed mapping for reading only over the specified slice of the given file, notifying the given
     * listeners.
     * <p>
     * The file is mapped immediately, hence it must exist, also with {@link OpenMode#READ_ONLY_LAZY READ_ONLY_LAZY},
     * and opening a file that is too short fails with an {@link IllegalArgumentException}.
     *
     * @param file              the file to map
     * @param openMode          the mode to open the file with for reading only
     * @param offset            the start offset in the file
     * @param length            the length of the mapped slice, or -1 to map from offset to the actual end of the file
     * @param lifecycleListener the listener notified when the file is opened and closed
     * @param errorListener     the listener notified about errors that cannot be thrown, such as close errors
     * @return a new fixed mapping for the specified file slice
     */
    public static FixedMapping fixedMapping(final File file,
                                            final ReadOnlyOpenMode openMode,
                                            final long offset,
                                            final int length,
                                            final LifecycleListener lifecycleListener,
                                            final ErrorListener errorListener) {
        OpenModes.apply(file, openMode, false);
        return fixedMappingImpl(file, openMode, offset, length, lifecycleListener, errorListener);
    }

    /**
     * Creates a fixed mutable mapping over the whole of the specified file, with a length determined by the actual
     * file size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @return a new fixed mutable mapping covering the whole file
     */
    public static FixedMutableMapping fixedMutableMapping(final File file, final ReadWriteOpenMode openMode) {
        return fixedMutableMapping(file, openMode, 0L);
    }

    /**
     * Creates a fixed mutable mapping over the specified file from the given offset to the end of the file, with a
     * length determined by the actual file size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @param offset     the start offset in the file
     * @return a new fixed mutable mapping from the offset to the end of the file
     */
    public static FixedMutableMapping fixedMutableMapping(final File file,
                                                          final ReadWriteOpenMode openMode,
                                                          final long offset) {
        return fixedMutableMapping(file, openMode, offset, -1);
    }

    /**
     * Creates a fixed mutable mapping over the specified slice of the given file.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @param offset     the start offset in the file
     * @param length     the length of the mapped slice, or -1 to map from offset to the actual end of the file
     * @return a new fixed mutable mapping for the specified file slice
     * @throws IllegalArgumentException if the resulting mapping length exceeds the integer range
     */
    public static FixedMutableMapping fixedMutableMapping(final File file,
                                                          final ReadWriteOpenMode openMode,
                                                          final long offset,
                                                          final int length) {
        return fixedMutableMapping(file, openMode, offset, length, MappingConfigurations.defaultLifecycleListener(),
                CoreConfigurations.defaultErrorListener());
    }

    /**
     * Creates a fixed mutable mapping over the specified slice of the given file, notifying the given listeners.
     * <p>
     * A file shorter than the mapped slice is extended with {@link FileInitialiser#ZERO zero bytes}.
     *
     * @param file              the file to map
     * @param openMode          the mode to open the file with for reading and writing
     * @param offset            the start offset in the file
     * @param length            the length of the mapped slice, or -1 to map from offset to the actual end of the file
     * @param lifecycleListener the listener notified when the file is created, opened and closed
     * @param errorListener     the listener notified about errors that cannot be thrown, such as close errors
     * @return a new fixed mutable mapping for the specified file slice
     */
    public static FixedMutableMapping fixedMutableMapping(final File file,
                                                          final ReadWriteOpenMode openMode,
                                                          final long offset,
                                                          final int length,
                                                          final LifecycleListener lifecycleListener,
                                                          final ErrorListener errorListener) {
        OpenModes.apply(file, openMode, false);
        return fixedMappingImpl(file, openMode, offset, length, lifecycleListener, errorListener);
    }

    private static FixedMappingImpl fixedMappingImpl(final File file,
                                                     final OpenMode openMode,
                                                     final long offset,
                                                     final int length,
                                                     final LifecycleListener lifecycleListener,
                                                     final ErrorListener errorListener) {
        final long fileSize = length >= 0 ? offset + length : file.length();
        validateFixedMappingLength(offset, fileSize);
        final FileMapper fileMapper = new FixedSizeFileMapper(file, fileSize, openMode.accessMode(),
                FileInitialiser.ZERO, lifecycleListener, errorListener);
        return new FixedMappingImpl(fileMapper, offset, (int)(fileSize - offset), true);
    }

    /**
     * Creates a dynamic mapping for reading only for the specified file using default mapping configuration.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @return a new dynamic mapping for the given file
     * @see MappingConfig#getDefault()
     */
    public static DynamicMapping dynamicMapping(final File file, final ReadOnlyOpenMode openMode) {
        return dynamicMapping(file, openMode, MappingConfig.getDefault());
    }

    /**
     * Creates a dynamic mapping for reading only for the specified file using the given mapping configuration.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @param config     the mapping configuration to use
     * @return a new dynamic mapping for the given file
     */
    public static DynamicMapping dynamicMapping(final File file,
                                                final ReadOnlyOpenMode openMode,
                                                final MappingConfig config) {
        OpenModes.apply(file, openMode, config.rollFiles());
        return new DynamicMappingImpl(regionMapper(file, openMode, config), true);
    }

    /**
     * Creates a dynamic mutable mapping for the specified file using default mapping configuration.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @return a new dynamic mutable mapping for the given file
     * @see MappingConfig#getDefault()
     */
    public static DynamicMutableMapping dynamicMutableMapping(final File file, final ReadWriteOpenMode openMode) {
        return dynamicMutableMapping(file, openMode, MappingConfig.getDefault());
    }

    /**
     * Creates a dynamic mutable mapping for the specified file using the given mapping configuration.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @param config     the mapping configuration to use
     * @return a new dynamic mutable mapping for the given file
     */
    public static DynamicMutableMapping dynamicMutableMapping(final File file,
                                                              final ReadWriteOpenMode openMode,
                                                              final MappingConfig config) {
        OpenModes.apply(file, openMode, config.rollFiles());
        return new DynamicMappingImpl(regionMapper(file, openMode, config), true);
    }

    private static RegionMapper regionMapper(final File file, final OpenMode openMode, final MappingConfig config) {
        final FileMapper fileMapper = FileMappers.create(file, openMode.accessMode(), config);
        return RegionMappers.create(fileMapper, config);
    }

    /**
     * Creates a mapping pool providing mappings for reading only for the specified file using default mapping
     * configuration and the default initial pool size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @return a new mapping pool for the given file
     * @see MappingConfig#getDefault()
     * @see MappingConfigurations#defaultInitialMappingPoolSize()
     */
    public static MappingPool mappingPool(final File file, final ReadOnlyOpenMode openMode) {
        return mappingPool(file, openMode, MappingConfig.getDefault());
    }

    /**
     * Creates a mapping pool providing mappings for reading only for the specified file using the given mapping
     * configuration and the default initial pool size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading only
     * @param config     the mapping configuration to use
     * @return a new mapping pool for the given file
     * @see MappingConfigurations#defaultInitialMappingPoolSize()
     */
    public static MappingPool mappingPool(final File file,
                                          final ReadOnlyOpenMode openMode,
                                          final MappingConfig config) {
        return mappingPool(file, openMode, config, defaultInitialMappingPoolSize());
    }

    /**
     * Creates a mapping pool providing mappings for reading only for the specified file using the given mapping
     * configuration and initial pool size.
     *
     * @param file            the file to map
     * @param openMode        the mode to open the file with for reading only
     * @param config          the mapping configuration to use
     * @param initialPoolSize the expected number of mappings to be acquired from the pool, used as a capacity hint
     *                        for its internal tracking structures (mappings themselves are still created lazily,
     *                        on each acquire call)
     * @return a new mapping pool for the given file
     */
    public static MappingPool mappingPool(final File file,
                                          final ReadOnlyOpenMode openMode,
                                          final MappingConfig config,
                                          final int initialPoolSize) {
        OpenModes.apply(file, openMode, config.rollFiles());
        return new MappingPoolImpl(regionMapper(file, openMode, config), initialPoolSize, config.lifecycleListener());
    }

    /**
     * Creates a mutable mapping pool for the specified file using default mapping configuration and the default
     * initial pool size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @return a new mutable mapping pool for the given file
     * @see MappingConfig#getDefault()
     * @see MappingConfigurations#defaultInitialMappingPoolSize()
     */
    public static MutableMappingPool mutableMappingPool(final File file, final ReadWriteOpenMode openMode) {
        return mutableMappingPool(file, openMode, MappingConfig.getDefault());
    }

    /**
     * Creates a mutable mapping pool for the specified file using the given mapping configuration and the default
     * initial pool size.
     *
     * @param file       the file to map
     * @param openMode   the mode to open the file with for reading and writing
     * @param config     the mapping configuration to use
     * @return a new mutable mapping pool for the given file
     * @see MappingConfigurations#defaultInitialMappingPoolSize()
     */
    public static MutableMappingPool mutableMappingPool(final File file,
                                                        final ReadWriteOpenMode openMode,
                                                        final MappingConfig config) {
        return mutableMappingPool(file, openMode, config, defaultInitialMappingPoolSize());
    }

    /**
     * Creates a mutable mapping pool for the specified file using the given mapping configuration and initial pool
     * size.
     *
     * @param file            the file to map
     * @param openMode        the mode to open the file with for reading and writing
     * @param config          the mapping configuration to use
     * @param initialPoolSize the expected number of mappings to be acquired from the pool, used as a capacity hint
     *                        for its internal tracking structures (mappings themselves are still created lazily,
     *                        on each acquire call)
     * @return a new mutable mapping pool for the given file
     */
    public static MutableMappingPool mutableMappingPool(final File file,
                                                        final ReadWriteOpenMode openMode,
                                                        final MappingConfig config,
                                                        final int initialPoolSize) {
        OpenModes.apply(file, openMode, config.rollFiles());
        return new MappingPoolImpl(regionMapper(file, openMode, config), initialPoolSize, config.lifecycleListener());
    }

    /**
     * Returns the number of bytes currently mapped by all mappings and mapping pools of this library in the JVM,
     * including regions that are cached, or mapped ahead or not unmapped yet by background threads. It is the virtual
     * address space the mappings take, not the memory they use: pages that were never accessed take no memory, and a
     * file region mapped several times counts for each mapping. Once all mappings and pools are closed, it returns to
     * what it was before they were created, for instance to zero.
     *
     * @return the bytes currently mapped by this library
     * @see #mappingCount()
     */
    public static long mappedBytes() {
        return FileChannels.mappedBytes();
    }

    /**
     * Returns the number of memory mappings currently mapped by all mappings and mapping pools of this library in the
     * JVM, counted like {@link #mappedBytes()}. The operating system may limit the number of mappings of a process,
     * for instance through {@code vm.max_map_count} on Linux, which also counts mappings of the JVM itself.
     *
     * @return the number of memory mappings currently mapped by this library
     */
    public static long mappingCount() {
        return FileChannels.mappingCount();
    }
}
