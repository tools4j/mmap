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

import org.agrona.CloseHelper;
import org.agrona.collections.LongArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.AccessMode;
import org.tools4j.mmap.region.api.Unsafe;
import org.tools4j.mmap.region.config.MappingConfig;
import org.tools4j.mmap.region.impl.FileInitialiser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.region.impl.Constraints.validateMaxOpenFiles;
import static org.tools4j.mmap.region.impl.Constraints.validateNotClosed;
import static org.tools4j.mmap.region.impl.Constraints.validateRegionSize;

/**
 * A {@link FileMapper} that splits a logical stream across multiple physical files ("rolling"), creating a new file
 * once the current one reaches its maximum size and keeping at most a configured number of files open at a time.
 * <p>
 * <b>Note:</b> This class is <b>not thread safe</b> and must only be used from a single thread. See
 * {@link ConcurrentRollingFileMapper} for a thread-safe alternative.
 */
@Unsafe
public class RollingFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileMapper.class);
    private static final int DEFAULT_CAPACITY = 1024;

    private final File baseFile;
    private final Function<? super File, ? extends FileMapper> fileMapperFactory;
    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final int maxOpenFiles;
    private final AccessMode accessMode;
    private final long positionInFileMask;
    private final int positionToFileIndexShift;

    private final List<File> files = new ArrayList<>(DEFAULT_CAPACITY);
    private final List<FileMapper> fileMappers = new ArrayList<>(DEFAULT_CAPACITY);
    private final LongArrayList timeStamps = new LongArrayList(DEFAULT_CAPACITY, 0);
    private final IntFunction<File> fileFactory = this::fileForIndex;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private long clock;
    private int openFiles;


    private RollingFileMapper(final File baseFile,
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
        return new RollingFileMapper(baseFile, file -> new ReadOnlyFileMapper(file, fileInitialiser),
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
                file -> new ExpandableSizeFileMapper(file, minFileSize, maxFileSize, regionSize, fileInitialiser) :
                file -> new FixedSizeFileMapper(file, maxFileSize, accessMode, fileInitialiser);
        return new RollingFileMapper(baseFile, fileMapperFactory, maxFileSize, regionSize, filesToCreateAhead,
                maxOpenFiles, accessMode);
    }

    private File getOrCreateFile(final int fileIndex) {
        File file;
        if (fileIndex < files.size() && (file = files.get(fileIndex)) != null) {
            return file;
        }
        for (int i = files.size(); i <= fileIndex; i++) {
            files.add(null);
        }
        file = fileFactory.apply(fileIndex);
        files.set(fileIndex, file);
        return file;
    }

    private FileMapper getOrCreateFileMapper(final int fileIndex) {
        final FileMapper fileMapper;
        if (fileIndex < fileMappers.size() && (fileMapper = fileMappers.get(fileIndex)) != null) {
            return fileMapper;
        }
        return createFileMapper(fileIndex, getOrCreateFile(fileIndex));
    }

    private FileMapper createFileMapper(final int fileIndex, final File fileForIndex) {
        final FileMapper fileMapper = fileMapperFactory.apply(fileForIndex);
        for (int i = fileMappers.size(); i <= fileIndex; i++) {
            fileMappers.add(null);
        }
        fileMappers.set(fileIndex, fileMapper);
        for (int i = timeStamps.size(); i <= fileIndex; i++) {
            timeStamps.addLong(0);
        }
        timeStamps.set(fileIndex, ++clock);
        openFiles++;
        if (isClosed()) {
            close(fileIndex);
            return null;
        }
        return fileMapper;
    }

    private int lruFileIndex() {
        long lruTime = Long.MAX_VALUE;
        int lruIndex = -1;
        for (int fileIndex = 0; fileIndex < fileMappers.size(); fileIndex++) {
            if (fileMappers.get(fileIndex) != null) {
                final long timeStamp = timeStamps.get(fileIndex);
                if (timeStamp < lruTime) {
                    lruTime = timeStamp;
                    lruIndex = fileIndex;
                }
            }
        }
        return lruIndex;
    }

    private void touch(final int fileIndex) {
        timeStamps.set(fileIndex, ++clock);
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
        validateNotClosed(this);
        if (position < 0) {
            return NULL_ADDRESS;
        }
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }

        final int fileIndex = positionToFileIndex(position);
        FileMapper mapperForIndex = fileIndex < fileMappers.size() ? fileMappers.get(fileIndex) : null;
        if (mapperForIndex == null) {
            if (isClosed()) {
                return NULL_ADDRESS;
            }
            final File file = getOrCreateFile(fileIndex);
            if (accessMode == AccessMode.READ_ONLY && !file.exists()) {
                return NULL_ADDRESS;
            }
            mapperForIndex = createFileMapper(fileIndex, file);
            closeFilesIfNecessary();

            //NOTE: pre-create next files
            FileMapper mapper = mapperForIndex;
            for (int i = 1; i <= filesToCreateAhead && mapper != null; i++) {
                mapper = getOrCreateFileMapper(fileIndex + i);
                if (openFiles > maxOpenFiles) {
                    touch(fileIndex);//prevent closing the one we actually want
                    closeFilesIfNecessary();
                }
            }
            if (mapperForIndex == null) {
                return NULL_ADDRESS;
            }
        } else {
            touch(fileIndex);
        }
        final long positionWithinFile = position & positionInFileMask;
        return mapperForIndex.map(positionWithinFile, length);
    }

    @Override
    public void unmap(final long position, final long address, final int length) {
        assert address > NULL_ADDRESS;
        assert position > NULL_POSITION;
        validateNotClosed(this);
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
        final int fileIndex = positionToFileIndex(position);
        final long positionWithinFile = position & positionInFileMask;
        final FileMapper mapperForIndex = fileMappers.get(fileIndex);
        if (mapperForIndex == null) {
            return;
        }
        touch(fileIndex);
        mapperForIndex.unmap(positionWithinFile, address, length);
    }

    private void closeFilesIfNecessary() {
        while (openFiles > maxOpenFiles) {
            final int lruIndex = lruFileIndex();
            if (lruIndex >= 0) {
                close(lruIndex);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.getAcquire();
    }

    private void close(final int fileIndex) {
        final FileMapper fileMapper = fileMappers.set(fileIndex, null);
        if (fileMapper != null) {
            CloseHelper.quietClose(fileMapper);
            openFiles--;
        }
        files.set(fileIndex, null);
        timeStamps.setLong(fileIndex, 0);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            final int openBeforeClosing = openFiles;
            try {
                for (int fileIndex = 0; fileIndex < fileMappers.size(); fileIndex++) {
                    close(fileIndex);
                }
            } finally {
                LOGGER.info("Closed: {} ({} open files before closing)", this, openBeforeClosing);
            }
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
                "|openFiles=" + openFiles +
                "|closed=" + isClosed();
    }
}
