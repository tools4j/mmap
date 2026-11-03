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
package org.tools4j.mmap.mapping.unsafe;

import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ErrorListener;
import org.tools4j.mmap.mapping.api.FileInitialiser;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.mapping.api.Unsafe;
import org.tools4j.mmap.mapping.config.MappingConfig;
import org.tools4j.mmap.mapping.config.MappingConfigurations;
import org.tools4j.mmap.mapping.impl.AtomicArray;
import org.tools4j.mmap.mapping.impl.ConcurrentLruCache;
import org.tools4j.mmap.mapping.impl.RollingFiles;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.Constraints.alreadyClosedException;
import static org.tools4j.mmap.mapping.impl.Constraints.validateFilesToCreateAhead;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxFileSize;
import static org.tools4j.mmap.mapping.impl.Constraints.validateMaxOpenFiles;
import static org.tools4j.mmap.mapping.impl.Constraints.validateRegionSize;

/**
 * Splits a logical stream across multiple physical files ("rolling"), creating a new file once the current one reaches
 * its maximum size and keeping at most a configured number of files open at a time.
 * <p>
 * The files are shared by {@link PerThreadFileMapper}s. The factory methods return the owner; per-thread file mappers
 * for other threads are obtained via {@link FileMappers#forThread(FileMapper)}.
 * <p>
 * <b>Note:</b> {@code map} of a per-thread file mapper must be called by one thread at a time only (it maps via its own
 * {@link ConcurrentLruCache.Borrower}); {@code unmap} is thread-safe, as it needs no file access. Closing the owner
 * closes the files, after all other per-thread file mappers have stopped mapping; closing any other one only releases
 * its own resources. See {@link RollingFileMapper} for a single-threaded alternative.
 */
@Unsafe
public final class ConcurrentRollingFileMapper implements Describable {
    private static final int DEFAULT_CAPACITY = 1024;

    private final File baseFile;
    private final Function<? super File, ? extends FileMapper> fileMapperFactory;
    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final int maxOpenFiles;
    private final AccessMode accessMode;
    private final LifecycleListener lifecycleListener;
    private final ErrorListener errorListener;
    private final long positionInFileMask;
    private final int positionToFileIndexShift;

    private final AtomicArray<File> files;
    private final ConcurrentLruCache<FileMapper> fileMappers;
    private final IntFunction<File> fileFactory = this::fileForIndex;
    private final IntFunction<FileMapper> fileMapperByIndexFactory = this::fileMapperForIndex;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ConcurrentRollingFileMapper(final File baseFile,
                                        final Function<? super File, ? extends FileMapper> fileMapperFactory,
                                        final long maxFileSize,
                                        final int regionSize,
                                        final int filesToCreateAhead,
                                        final int maxOpenFiles,
                                        final AccessMode accessMode,
                                        final LifecycleListener lifecycleListener,
                                        final ErrorListener errorListener) {
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
        this.lifecycleListener = requireNonNull(lifecycleListener);
        this.errorListener = requireNonNull(errorListener);
        this.positionInFileMask = maxFileSize - 1;
        this.positionToFileIndexShift = Long.SIZE - Long.numberOfLeadingZeros(maxFileSize - 1);
        this.files = new AtomicArray<>(DEFAULT_CAPACITY);
        this.fileMappers = new ConcurrentLruCache<>(maxOpenFiles);
        lifecycleListener.onOpened(this);
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final MappingConfig config,
                                         final FileInitialiser fileInitialiser) {
        return forReadOnly(baseFile, config.maxFileSize(), config.regionSize(), config.maxOpenFiles(),
                fileInitialiser, config.lifecycleListener(), config.errorListener());
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final int maxOpenFiles,
                                         final FileInitialiser fileInitialiser) {
        return forReadOnly(baseFile, maxFileSize, regionSize, maxOpenFiles, fileInitialiser,
                MappingConfigurations.defaultLifecycleListener(), MappingConfigurations.defaultErrorListener());
    }

    public static FileMapper forReadOnly(final File baseFile,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final int maxOpenFiles,
                                         final FileInitialiser fileInitialiser,
                                         final LifecycleListener lifecycleListener,
                                         final ErrorListener errorListener) {
        requireNonNull(fileInitialiser);
        requireNonNull(lifecycleListener);
        requireNonNull(errorListener);
        return new ConcurrentRollingFileMapper(baseFile,
                file -> new ReadOnlyFileMapper(file, fileInitialiser, lifecycleListener, errorListener),
                maxFileSize, regionSize, 0, maxOpenFiles, AccessMode.READ_ONLY, lifecycleListener, errorListener).newOwnerMapper();
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final MappingConfig config,
                                          final FileInitialiser fileInitialiser) {
        return forReadWrite(baseFile, accessMode, config.expandFile(), config.minFileSize(), config.maxFileSize(),
                config.regionSize(), config.filesToCreateAhead(), config.maxOpenFiles(),
                fileInitialiser, config.lifecycleListener(), config.errorListener());
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
        return forReadWrite(baseFile, accessMode, expandFile, minFileSize, maxFileSize, regionSize,
                filesToCreateAhead, maxOpenFiles, fileInitialiser, MappingConfigurations.defaultLifecycleListener(),
                MappingConfigurations.defaultErrorListener());
    }

    public static FileMapper forReadWrite(final File baseFile,
                                          final AccessMode accessMode,
                                          final boolean expandFile,
                                          final long minFileSize,
                                          final long maxFileSize,
                                          final int regionSize,
                                          final int filesToCreateAhead,
                                          final int maxOpenFiles,
                                          final FileInitialiser fileInitialiser,
                                          final LifecycleListener lifecycleListener,
                                          final ErrorListener errorListener) {
        requireNonNull(accessMode);
        requireNonNull(fileInitialiser);
        requireNonNull(lifecycleListener);
        requireNonNull(errorListener);
        final Function<File, FileMapper> fileMapperFactory = expandFile ?
                file -> new ExpandableSizeFileMapper(file, minFileSize, maxFileSize, regionSize, fileInitialiser,
                        lifecycleListener, errorListener) :
                file -> new FixedSizeFileMapper(file, maxFileSize, accessMode, fileInitialiser, lifecycleListener,
                        errorListener);
        return new ConcurrentRollingFileMapper(baseFile, fileMapperFactory, maxFileSize, regionSize, filesToCreateAhead,
                maxOpenFiles, accessMode, lifecycleListener, errorListener).newOwnerMapper();
    }

    private PerThreadFileMapper newOwnerMapper() {
        return new PerThreadFileMapper(true);
    }

    private File getOrCreateFile(final int fileIndex) {
        return files.computeIfAbsent(fileIndex, fileFactory);
    }

    private File fileForIndex(final int index) {
        return RollingFiles.fileForIndex(baseFile, index);
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

    private void validateRegionLength(final int length) {
        if (length != regionSize) {
            throw new IllegalArgumentException("Length " + length + " must match region size " + regionSize);
        }
    }

    private int openFiles() {
        return fileMappers.size();
    }

    //PRECONDITION: all per-thread file mappers have stopped mapping
    private void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (!fileMappers.removeAll()) {
                errorListener.onWarning(this, "Some files were still in use when closing");
            }
            for (int i = 0, len = files.length(); i < len; i++) {
                files.setIfPresent(i, null);
            }
        } finally {
            lifecycleListener.onClosed(this);
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("ConcurrentRollingFileMapper")
                .append(":baseFile=").append(baseFile.getPath())
                .append("|accessMode=").append(accessMode)
                .append("|maxFileSize=").append(maxFileSize)
                .append("|regionSize=").append(regionSize)
                .append("|filesToCreateAhead=").append(filesToCreateAhead)
                .append("|maxOpenFiles=").append(maxOpenFiles)
                .append("|openFiles=").append(openFiles())
                .append("|closed=").append(closed.getAcquire());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }

    /**
     * Per-thread file mapper over the shared files: {@code map} must be called by one thread at a time only,
     * {@code unmap} is thread-safe.
     */
    final class PerThreadFileMapper implements FileMapper {
        private final ConcurrentLruCache.Borrower<FileMapper> borrower = fileMappers.newBorrower();
        private final boolean owner;

        private PerThreadFileMapper(final boolean owner) {
            this.owner = owner;
        }

        /**
         * Returns a new per-thread file mapper for use by another thread, released when the owner is closed.
         * @see FileMappers#forThread(FileMapper)
         */
        PerThreadFileMapper newSibling() {
            if (isClosed()) {
                throw alreadyClosedException(this);
            }
            return new PerThreadFileMapper(false);
        }

        int openFiles() {
            return ConcurrentRollingFileMapper.this.openFiles();
        }

        @Override
        public AccessMode accessMode() {
            return accessMode;
        }

        @Override
        public long mappingLimit() {
            return Long.MAX_VALUE;
        }

        @Override
        public long map(final long position, final int length, final boolean preTouch) {
            if (position < 0) {
                return NULL_ADDRESS;
            }
            validateRegionLength(length);
            if (isClosed()) {
                throw alreadyClosedException(this);
            }
            final int fileIndex = positionToFileIndex(position);
            FileMapper mapperForIndex = borrower.tryAcquire(fileIndex);
            if (mapperForIndex == null) {
                if (accessMode == AccessMode.READ_ONLY && !getOrCreateFile(fileIndex).exists()) {
                    return NULL_ADDRESS;
                }
                //NOTE: pre-create next files first, furthest first, before acquiring fileIndex itself: this way
                //      fileIndex is never exposed to eviction while its own pre-create batch runs, and the nearest
                //      (soonest-needed) pre-created file ends up most-recently-used, i.e. least likely to be
                //      evicted under pressure
                for (int i = filesToCreateAhead; i >= 1; i--) {
                    borrower.createIfAbsent(fileIndex + i, fileMapperByIndexFactory);
                }
                mapperForIndex = borrower.acquire(fileIndex, fileMapperByIndexFactory);
            }
            try {
                return mapperForIndex.map(position & positionInFileMask, length, preTouch);
            } finally {
                borrower.release();
            }
        }

        @Override
        public void unmap(final long position, final long address, final int length) {
            assert address > NULL_ADDRESS;
            assert position > NULL_POSITION;
            validateRegionLength(length);
            if (closed.getAcquire()) {
                throw alreadyClosedException(this);
            }
            //NOTE: unmapping needs no file mapper, the mapping outlives the file if it was evicted meanwhile; hence
            //      also thread-safe, e.g. for async unmapping via the owner
            FileChannels.unmap(address, length);
        }

        @Override
        public boolean isClosed() {
            return closed.getAcquire() || borrower.isClosed();
        }

        /** Closes the files if this is the owner, otherwise releases only this per-thread file mapper */
        @Override
        public void close() {
            if (!borrower.isClosed()) {
                borrower.close();
                if (owner) {
                    ConcurrentRollingFileMapper.this.close();
                }
            }
        }

        @Override
        public String toString() {
            return ConcurrentRollingFileMapper.this + "|owner=" + owner;
        }
    }
}
