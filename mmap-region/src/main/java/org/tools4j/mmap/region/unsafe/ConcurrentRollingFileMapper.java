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
package org.tools4j.mmap.region.unsafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.AtomicArray;
import org.tools4j.mmap.region.impl.AtomicLruCache;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.alreadyClosedException;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxOpenFiles;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

@Unsafe
public class ConcurrentRollingFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentRollingFileMapper.class);
    private static final int DEFAULT_CAPACITY = 1024;
    private static final int CLOSED = Integer.MIN_VALUE;

    private final File baseFile;
    private final Function<? super File, ? extends FileMapper> fileMapperFactory;
    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final int maxOpenFiles;
    private final AccessMode accessMode;
    private final long positionInFileMask;
    private final int positionToFileIndexShift;

    private final AtomicArray<File> files;
    private final AtomicLruCache<FileMapper> fileMappers;
    private final IntFunction<File> fileFactory = this::fileForIndex;
    private final IntFunction<FileMapper> fileMapperByIndexFactory = this::fileMapperForIndex;

    private final AtomicInteger actors = new AtomicInteger(0);

    private ConcurrentRollingFileMapper(final File baseFile,
                                        final Function<? super File, ? extends FileMapper> fileMapperFactory,
                                        final long maxFileSize,
                                        final int regionSize,
                                        final int filesToCreateAhead,
                                        final int maxOpenFiles,
                                        final AccessMode accessMode) {
        requireNonNull(baseFile);
        requireNonNull(fileMapperFactory);
        validateMaxFileSize(maxFileSize);
        validateRegionSize(regionSize);
        validateFilesToCreateAhead(filesToCreateAhead);
        validateMaxOpenFiles(maxOpenFiles);
        requireNonNull(accessMode);
        if (maxFileSize % regionSize != 0) {
            throw new IllegalArgumentException("Invalid maxFileSize=" + maxFileSize +
                    ", must be a multiple of regionSize=" + regionSize);
        }
        if (maxOpenFiles < 2) {
            throw new IllegalArgumentException("Invalid maxOpenFiles=" + maxOpenFiles +
                    ", must be at least 2 for async mapping strategy");
        }
        if (filesToCreateAhead >= maxOpenFiles) {
            throw new IllegalArgumentException("Invalid filesToCreateAhead=" + filesToCreateAhead +
                    ", must be smaller than maxOpenFiles=" + maxOpenFiles);
        }
        this.baseFile = baseFile;
        this.fileMapperFactory = fileMapperFactory;
        this.maxFileSize = maxFileSize;
        this.regionSize = regionSize;
        this.filesToCreateAhead = filesToCreateAhead;
        this.maxOpenFiles = maxOpenFiles;
        this.accessMode = accessMode;
        this.positionInFileMask = maxFileSize - 1;
        this.positionToFileIndexShift = Long.SIZE - Long.numberOfLeadingZeros(maxFileSize - 1);
        this.files = new AtomicArray<>(DEFAULT_CAPACITY);
        this.fileMappers = new AtomicLruCache<>(maxOpenFiles);
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final MappingConfig config,
                                         final FileInitialiser fileInitialiser) {
        return forReadOnly(baseFile, config.maxFileSize(), config.mappingStrategy().regionSize(), config.maxOpenFiles(),
                fileInitialiser);
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final int maxOpenFiles,
                                         final FileInitialiser fileInitialiser) {
        requireNonNull(fileInitialiser);
        return new ConcurrentRollingFileMapper(baseFile, file -> new ReadOnlyFileMapper(file, fileInitialiser),
                maxFileSize, regionSize, 0, maxOpenFiles, AccessMode.READ_ONLY);
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final FileInitialiser fileInitialiser) {
        return forReadWrite(baseFile, accessMode, config.expandFile(), config.minFileSize(), config.maxFileSize(),
                config.mappingStrategy().regionSize(), config.filesToCreateAhead(), config.maxOpenFiles(),
                fileInitialiser);
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final boolean expandFile,
                                          final long minFileSize,
                                          final long maxFileSize,
                                          final int regionSize,
                                          final int filesToCreateAhead,
                                          final int maxOpenFiles,
                                          final FileInitialiser fileInitialiser) {
        requireNonNull(accessMode);
        requireNonNull(fileInitialiser);
        final Function<File, FileMapper> fileMapperFactory = expandFile ?
                file -> new ExpandableSizeFileMapper(file, minFileSize, maxFileSize, fileInitialiser) :
                file -> new FixedSizeFileMapper(file, maxFileSize, accessMode, fileInitialiser);
        return new ConcurrentRollingFileMapper(baseFile, fileMapperFactory, maxFileSize, regionSize, filesToCreateAhead,
                maxOpenFiles, accessMode);
    }

    private File getOrCreateFile(final int fileIndex) {
        return files.computeIfAbsent(fileIndex, fileFactory);
    }

    private File fileForIndex(final int index) {
        return fileForIndex(baseFile, index);
    }

    private static File fileForIndex(final File baseFile, final int index) {
        final String name = baseFile.getName();
        final int dotIndex = name.lastIndexOf('.');
        final int nameEnd = dotIndex < 0 ? name.length() : dotIndex;
        final String ending = dotIndex < 0 ? "" : name.substring(dotIndex);
        final String prefix = name.substring(0, nameEnd);
        return new File(baseFile.getParentFile(), prefix + "_" + index + ending);
    }

    private FileMapper fileMapperForIndex(final int index) {
        final File file = getOrCreateFile(index);
        return fileMapperFactory.apply(file);
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
        if (position < 0) {
            return NULL_ADDRESS;
        }
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        final int fileIndex = positionToFileIndex(position);
        enter();
        FileMapper mapperForIndex = null;
        try {
            mapperForIndex = accessMode == AccessMode.READ_ONLY
                    ? fileMappers.tryAcquire(fileIndex)
                    : fileMappers.acquire(fileIndex, fileMapperByIndexFactory);
            if (mapperForIndex == null) {
                if (accessMode != AccessMode.READ_ONLY) {
                    return NULL_ADDRESS;
                }
                final File file = getOrCreateFile(fileIndex);
                if (!file.exists()) {
                    return NULL_ADDRESS;
                }
                mapperForIndex = fileMappers.acquire(fileIndex, fileMapperByIndexFactory);

                //NOTE: pre-create next files
                for (int i = 1; i <= filesToCreateAhead; i++) {
                    fileMappers.acquire(fileIndex + i, fileMapperByIndexFactory);
                    fileMappers.release(fileIndex + i);
                }
            }
            final long positionWithinFile = position & positionInFileMask;
            return mapperForIndex.map(positionWithinFile, length);
        } finally {
            if (mapperForIndex != null) {
                fileMappers.release(fileIndex);
            }
            exit();
        }
    }

    @Override
    public void unmap(final long position, final long address, final int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        enter();
        try {
            final int fileIndex = positionToFileIndex(position);
            final long positionWithinFile = position & positionInFileMask;
            final FileMapper mapperForIndex = fileMappers.tryAcquire(fileIndex);
            if (mapperForIndex != null) {
                mapperForIndex.unmap(positionWithinFile, address, length);
                fileMappers.release(fileIndex);
            }
        } finally {
            exit();
        }
    }

    private void enter() {
        if (actors.incrementAndGet() <= 0) {
            actors.decrementAndGet();
            throw alreadyClosedException(this);
        }
    }

    private void exit() {
        if (actors.decrementAndGet() == CLOSED) {
            doClose();
        }
    }

    @Override
    public boolean isClosed() {
        return actors.getAcquire() < 0;
    }

    public int openFiles() {
        return fileMappers.size();
    }

    @Override
    public void close() {
        final int actorsOnClose = actors.getAndUpdate(cur -> cur < 0 ? cur : cur + CLOSED);
        if (actorsOnClose == 0) {
            doClose();
        }
    }

    //PRECONDITION: closed and no actors
    private void doClose() {
        final int openBeforeClosing = openFiles();
        try {
            fileMappers.removeAll();
            for (int i = 0, len = files.length(); i < len; i++) {
                files.setIfPresent(i, null);
            }
        } finally {
            LOGGER.info("Closed: {} ({} open files before closing)", this, openBeforeClosing);
        }
    }

    @Override
    public String toString() {
        return "RollingFileMapper" +
                ":accessMode=" + accessMode +
                "|maxFileSize=" + maxFileSize +
                "|regionSize=" + regionSize +
                "|filesToCreateAhead=" + filesToCreateAhead +
                "|maxOpenFiles=" + maxOpenFiles +
                "|baseFile=" + baseFile +
                "|openFiles=" + openFiles() +
                "|closed=" + isClosed();
    }
}
