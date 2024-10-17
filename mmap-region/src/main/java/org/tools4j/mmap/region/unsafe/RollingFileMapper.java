/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.region.unsafe;

import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.MappingConfig;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public class RollingFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileMapper.class);

    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final Int2ObjectHashMap<FileMapper> fileMappers = new Int2ObjectHashMap<>();
    private final IntFunction<? extends FileMapper> fileMapperFactory;
    private final File file;
    private final AccessMode mapMode;

    private boolean closed;

    RollingFileMapper(final File file,
                      final IntFunction<? extends FileMapper> fileMapperFactory,
                      final long maxFileSize,
                      final int regionSize,
                      final int filesToCreateAhead,
                      final AccessMode accessMode) {
        requireNonNull(file);
        requireNonNull(fileMapperFactory);
        validateMaxFileSize(maxFileSize);
        validateRegionSize(regionSize);
        validateFilesToCreateAhead(filesToCreateAhead);
        requireNonNull(accessMode);
        if (maxFileSize % regionSize != 0) {
            throw new IllegalArgumentException("Invalid maxFileSize=" + maxFileSize +
                    ", must be a multiple of regionSize=" + regionSize);
        }
        this.file = file;
        this.maxFileSize = maxFileSize;
        this.regionSize = regionSize;
        this.filesToCreateAhead = filesToCreateAhead;
        this.fileMapperFactory = fileMapperFactory;
        this.mapMode = accessMode;
    }

    public static FileMapper forReadOnly(final File file,
                                         final MappingConfig config,
                                         final FileInitialiser fileInitialiser) {
        return forReadOnly(file, config.maxFileSze(), config.mappingStrategy().regionSize(), fileInitialiser);
    }

    public static FileMapper forReadOnly(final File file,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final FileInitialiser fileInitialiser) {
        requireNonNull(file);
        requireNonNull(fileInitialiser);
        return new RollingFileMapper(file, index -> new ReadOnlyFileMapper(indexFile(file, index), fileInitialiser),
                maxFileSize, regionSize, 0, AccessMode.READ_ONLY);
    }

    public static FileMapper forReadWrite(final File file,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final FileInitialiser fileInitialiser) {
        return forReadWrite(file, accessMode, config.expandFile(), config.maxFileSze(),
                config.mappingStrategy().regionSize(), config.filesToCreateAhead(), fileInitialiser);
    }

    public static FileMapper forReadWrite(final File file,
                                          final AccessMode accessMode,
                                          final boolean expandFile,
                                          final long maxFileSize,
                                          final int regionSize,
                                          final int filesToCreateAhead,
                                          final FileInitialiser fileInitialiser) {
        requireNonNull(file);
        requireNonNull(accessMode);
        validateMaxFileSize(maxFileSize);
        validateRegionSize(regionSize);
        validateFilesToCreateAhead(filesToCreateAhead);
        requireNonNull(fileInitialiser);
        final IntFunction<FileMapper> fileMapperFactory = expandFile ?
                index -> new ExpandableSizeFileMapper(indexFile(file, index), maxFileSize, fileInitialiser) :
                index -> new ExpandableSizeFileMapper(indexFile(file, index), maxFileSize, fileInitialiser);
        return new RollingFileMapper(file, fileMapperFactory, maxFileSize, regionSize, filesToCreateAhead, accessMode);
    }

    private static File indexFile(final File file, final int index) {
        final String name = file.getName();
        final int dotIndex = name.lastIndexOf('.');
        final int nameEnd = dotIndex < 0 ? name.length() : dotIndex;
        final String ending = dotIndex < 0 ? "" : name.substring(dotIndex);
        final String prefix = name.substring(0, nameEnd);
        return new File(file.getParentFile(), prefix + "_" + index + ending);
    }

    @Override
    public AccessMode mapMode() {
        return mapMode;
    }

    @Override
    public long map(final long position, final int length) {
        if (length != regionSize) {
            LOGGER.error("Length {} to map should match region size {}", length, regionSize);
            return NULL_ADDRESS;
        }

        final int fileIndex = (int)(position / maxFileSize);
        final long positionWithinFile = position % maxFileSize;
        final FileMapper mapperForIndex = fileMappers.computeIfAbsent(fileIndex, fileMapperFactory);

        //NOTE: pre-create next file if we are in append mode
        if (mapMode == AccessMode.READ_WRITE) {
            for (int i = 1; i <= filesToCreateAhead; i++) {
                if (fileMappers.get(fileIndex + i) == null) {
                    computeIfAbsent(fileIndex + i);
                }
            }
        }
        return mapperForIndex.map(positionWithinFile, length);
    }

    private FileMapper computeIfAbsent(final int fileIndex) {
        final ExpandableSizeFileMapper mapper = (ExpandableSizeFileMapper)fileMappers.computeIfAbsent(fileIndex, fileMapperFactory);
        if (mapMode == AccessMode.READ_WRITE) {
            mapper.init();
            mapper.ensureFileLength(maxFileSize);
        }
        return mapper;
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        if (length != regionSize) {
            LOGGER.warn("Length {} to map should match region size {}", length, regionSize);
        }

        final int fileIndex = (int)(position / maxFileSize);
        final long positionWithinFile = position % maxFileSize;

        final FileMapper mapperForIndex = fileMappers.get(fileIndex);
        if (mapperForIndex != null) {
            mapperForIndex.unmap(address, positionWithinFile, length);

            //As writing is done as append-only, we should close the file when the last region
            //is unmapped.
            //As for Read-only, however, it provides random access, so we should not close
            //any files until this mapper is closed.
            if (mapMode == AccessMode.READ_WRITE && positionWithinFile + length == maxFileSize) {
                fileMappers.remove(fileIndex);
                mapperForIndex.close();
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                fileMappers.forEachInt((index, fileMapper) -> fileMapper.close());
                fileMappers.clear();
            } finally {
                closed = true;
                LOGGER.info("Closed rolling file mapper: mapMode={}, file={}", mapMode, file.getPath());
            }
        }
    }

}
