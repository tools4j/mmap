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
import org.agrona.collections.IntObjConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.AtomicArray;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.agrona.collections.Hashing.DEFAULT_LOAD_FACTOR;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public class RollingFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileMapper.class);
    private static final int DEFAULT_CAPACITY = 1024;

    private final File baseFile;
    private final Function<? super File, ? extends FileMapper> fileMapperFactory;
    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final boolean closeFiles;
    private final AccessMode accessMode;
    private final long positionInFileMask;
    private final int positionToFileIndexShift;

    private final ThreadLocal<Int2ObjectHashMap<File>> files = ThreadLocal.withInitial(
            () -> new Int2ObjectHashMap<>(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR)
    );
    private final AtomicArray<FileMapper> fileMappers = new AtomicArray<>(DEFAULT_CAPACITY);
    private final IntObjConsumer<FileMapper> fileMappersCloser = (fileIndex, fileMapper) ->
            closeFileMapper(fileMappers, fileIndex, fileMapper);
    private final AtomicLong lastUnmappedPosition = new AtomicLong(NULL_POSITION);

    private boolean closed;

    private RollingFileMapper(final File baseFile,
                              final Function<? super File, ? extends FileMapper> fileMapperFactory,
                              final long maxFileSize,
                              final int regionSize,
                              final int filesToCreateAhead,
                              final boolean closeFiles,
                              final AccessMode accessMode) {
        requireNonNull(baseFile);
        requireNonNull(fileMapperFactory);
        validateMaxFileSize(maxFileSize);
        validateRegionSize(regionSize);
        validateFilesToCreateAhead(filesToCreateAhead);
        requireNonNull(accessMode);
        if (maxFileSize % regionSize != 0) {
            throw new IllegalArgumentException("Invalid maxFileSize=" + maxFileSize +
                    ", must be a multiple of regionSize=" + regionSize);
        }
        this.baseFile = baseFile;
        this.fileMapperFactory = fileMapperFactory;
        this.maxFileSize = maxFileSize;
        this.regionSize = regionSize;
        this.filesToCreateAhead = filesToCreateAhead;
        this.closeFiles = closeFiles;
        this.accessMode = accessMode;
        this.positionInFileMask = maxFileSize - 1;
        this.positionToFileIndexShift = Long.SIZE - Long.numberOfLeadingZeros(maxFileSize - 1);
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final MappingConfig config,
                                         final FileInitialiser fileInitialiser) {
        return forReadOnly(baseFile, config.maxFileSize(), config.mappingStrategy().regionSize(), config.closeFiles(),
                fileInitialiser);
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final boolean closeFiles,
                                         final FileInitialiser fileInitialiser) {
        requireNonNull(fileInitialiser);
        return new RollingFileMapper(baseFile, file -> new ReadOnlyFileMapper(file, fileInitialiser),
                maxFileSize, regionSize, 0, closeFiles, AccessMode.READ_ONLY);
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final FileInitialiser fileInitialiser) {
        return forReadWrite(baseFile, accessMode, config.expandFile(), config.maxFileSize(),
                config.mappingStrategy().regionSize(), config.filesToCreateAhead(), config.closeFiles(),
                fileInitialiser);
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final boolean expandFile,
                                          final long maxFileSize,
                                          final int regionSize,
                                          final int filesToCreateAhead,
                                          final boolean closeFiles,
                                          final FileInitialiser fileInitialiser) {
        requireNonNull(accessMode);
        requireNonNull(fileInitialiser);
        final Function<File, FileMapper> fileMapperFactory = expandFile ?
                file -> new ExpandableSizeFileMapper(file, maxFileSize, fileInitialiser) :
                file -> new FixedSizeFileMapper(file, maxFileSize, accessMode, fileInitialiser);
        return new RollingFileMapper(baseFile, fileMapperFactory, maxFileSize, regionSize, filesToCreateAhead,
                closeFiles, accessMode);
    }

    private static File indexFile(final File baseFile, final int index) {
        final String name = baseFile.getName();
        final int dotIndex = name.lastIndexOf('.');
        final int nameEnd = dotIndex < 0 ? name.length() : dotIndex;
        final String ending = dotIndex < 0 ? "" : name.substring(dotIndex);
        final String prefix = name.substring(0, nameEnd);
        return new File(baseFile.getParentFile(), prefix + "_" + index + ending);
    }

    private int positionToFileIndex(final long position) {
        final long index = position >>> positionToFileIndexShift;
        if (index <= Integer.MAX_VALUE) {
            return (int)index;
        }
        throw new IllegalArgumentException("File index exceeded for " + position + " and max file size " + maxFileSize);
    }

    @Override
    public AccessMode accessMode() {
        return accessMode;
    }

    @Override
    public long map(final long position, final int length) {
        checkNotClosed();
        if (position < 0) {
            return NULL_ADDRESS;
        }
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }

        final int fileIndex = positionToFileIndex(position);
        final long positionWithinFile = position & positionInFileMask;
        final AtomicArray<FileMapper> mappers = fileMappers;
        FileMapper mapperForIndex = mappers.get(fileIndex);
        if (mapperForIndex == null) {
            final File fileForIndex = getOrCreateFile(fileIndex);
            if (accessMode == AccessMode.READ_ONLY && !fileForIndex.exists()) {
                return NULL_ADDRESS;
            }
            mapperForIndex = getOrCreateFileMapper(mappers, fileIndex, fileForIndex);

            //NOTE: pre-create next files
            for (int i = 1; i <= filesToCreateAhead; i++) {
                if (mappers.get(fileIndex + i) == null) {
                    getOrCreateFileMapper(mappers, fileIndex, getOrCreateFile(fileIndex));
                }
            }
        }
        return mapperForIndex.map(positionWithinFile, length);
    }

    private File getOrCreateFile(final int fileIndex) {
        final Int2ObjectHashMap<File> fileMap = files.get();
        File file = fileMap.get(fileIndex);
        if (file == null) {
            file = indexFile(baseFile, fileIndex);
            fileMap.put(fileIndex, file);
        }
        return file;
    }

    private FileMapper getOrCreateFileMapper(final AtomicArray<FileMapper> fileMappers,
                                             final int fileIndex,
                                             final File fileForIndex) {
        assert fileForIndex != null : "fileForIndex cannot be null";
        FileMapper fileMapper = fileMappers.get(fileIndex);
        if (fileMapper == null) {
            fileMapper = fileMapperFactory.apply(fileForIndex);
            if (!fileMappers.compareAndSet(fileIndex, null, fileMapper)) {
                fileMapper = fileMappers.get(fileIndex);
            }
        }
        return fileMapper;
    }

    @Override
    public void unmap(final long address, final long position, final int length) {
        checkNotClosed();
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }

        final int fileIndex = positionToFileIndex(position);
        final long positionWithinFile = position & positionInFileMask;
        final FileMapper mapperForIndex = fileMappers.get(fileIndex);
        if (mapperForIndex != null) {
            mapperForIndex.unmap(address, positionWithinFile, length);

            //If closeFile == true, we should close the file when
            // a) we are forward un-mapping, and the last region of the file was just unmapped
            // b) we are backward un-mapping, and the first region of the file was just unmapped
            // a) we are forward un-mapping, and the last (first(first) region of the file is unmapped.
            if (closeFiles) {
                final long lastUnmappedPos = lastUnmappedPosition.getAndSet(position);
                if (lastUnmappedPos != NULL_POSITION && (
                        (positionWithinFile + length == maxFileSize && position == lastUnmappedPos + length) ||
                        (positionWithinFile == 0 && position == lastUnmappedPos - length)
                )) {
                    closeFile(fileIndex, mapperForIndex);
                }
            }
        }
    }

    private void closeFile(final int fileIndex, final FileMapper mapperForIndex) {
        closeFileMapper(fileMappers, fileIndex, mapperForIndex);
        files.get().remove(fileIndex);
    }

    private static void closeFileMapper(final AtomicArray<FileMapper> mappers,
                                        final int fileIndex,
                                        final FileMapper mapperForIndex) {
        if (mapperForIndex != null) {
            mappers.compareAndSet(fileIndex, mapperForIndex, null);
            mapperForIndex.close();
        }
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Expandable-size file mapper is closed");
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
                fileMappers.forEach(fileMappersCloser);
                files.remove();
            } finally {
                closed = true;
                LOGGER.info("Closed rolling file mapper: mapMode={}, file={}", accessMode, baseFile.getPath());
            }
        }
    }

}
