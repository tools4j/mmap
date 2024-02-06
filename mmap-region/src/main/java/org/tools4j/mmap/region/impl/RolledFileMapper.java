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
package org.tools4j.mmap.region.impl;

import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.MapMode;

import java.util.function.IntFunction;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.impl.Constraints.greaterThanZero;
import static org.tools4j.mmap.region.impl.Constraints.nonNegative;

public class RolledFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolledFileMapper.class);

    interface IndexedFactory {
        FileMapper create(int index);
    }

    private final long maxFileSize;
    private final int regionSize;
    private final int filesToCreateAhead;
    private final Int2ObjectHashMap<FileMapper> fileMappers = new Int2ObjectHashMap<>();
    private final IntFunction<FileMapper> factory;
    private final String filePrefix;
    private final MapMode mapMode;

    RolledFileMapper(final String filePrefix,
                     final IndexedFactory indexedFactory,
                     final long maxFileSize,
                     final int regionSize,
                     final int filesToCreateAhead,
                     final MapMode mapMode) {
        greaterThanZero(maxFileSize, "maxFileSize");
        greaterThanZero(regionSize, "regionSize");
        nonNegative(filesToCreateAhead, "filesToCreateAhead");
        if (maxFileSize % regionSize != 0) {
            throw new IllegalArgumentException(
                    "maxFileSize [" + maxFileSize + "]  must be multiple of regionSize [" + regionSize + "]");
        }
        this.filePrefix = requireNonNull(filePrefix);
        this.maxFileSize = maxFileSize;
        this.regionSize = regionSize;
        this.filesToCreateAhead = filesToCreateAhead;
        this.factory = indexedFactory::create;
        this.mapMode = mapMode;
    }

    public static FileMapper forReadOnly(final String filePrefix,
                                         final long maxFileSize,
                                         final int regionSize,
                                         final FileInitialiser fileInitialiser) {
        requireNonNull(filePrefix);
        requireNonNull(fileInitialiser);
        return new RolledFileMapper(filePrefix, index -> new SingleFileReadOnlyMapper(filePrefix + "_" + index, fileInitialiser),
                maxFileSize, regionSize, 0, MapMode.READ_ONLY);
    }

    public static FileMapper forReadWrite(final String filePrefix,
                                          final long maxFileSize,
                                          final int regionSize,
                                          final int filesToCreateAhead,
                                          final FileInitialiser fileInitialiser) {
        requireNonNull(filePrefix);
        requireNonNull(fileInitialiser);
        return new RolledFileMapper(filePrefix,
                index -> new SingleFileReadWriteMapper(filePrefix  + "_" + index, maxFileSize, fileInitialiser), maxFileSize,
                regionSize, filesToCreateAhead, MapMode.READ_WRITE);
    }

    @Override
    public long map(final long position, final int length) {
        if (length != regionSize) {
            LOGGER.error("Length {} to map should match region size {}", length, regionSize);
            return NULL_ADDRESS;
        }

        final int fileIndex = (int)(position / maxFileSize);
        final long positionWithinFile = position % maxFileSize;
        final FileMapper mapperForIndex = fileMappers.computeIfAbsent(fileIndex, factory);

        //NOTE: pre-create next file if we are in append mode
        if (mapMode == MapMode.READ_WRITE) {
            for (int i = 1; i <= filesToCreateAhead; i++) {
                if (fileMappers.get(fileIndex + i) == null) {
                    computeIfAbsent(fileIndex + i);
                }
            }
        }
        return mapperForIndex.map(positionWithinFile, length);
    }

    private FileMapper computeIfAbsent(final int fileIndex) {
        final SingleFileReadWriteMapper mapper = (SingleFileReadWriteMapper)fileMappers.computeIfAbsent(fileIndex, factory);
        if (mapMode == MapMode.READ_WRITE) {
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
            if (mapMode == MapMode.READ_WRITE && positionWithinFile + length == maxFileSize) {
                fileMappers.remove(fileIndex);
                mapperForIndex.close();
            }
        }
    }

    @Override
    public void close() {
        fileMappers.forEachInt((index, fileMapper) -> fileMapper.close());
        fileMappers.clear();
        LOGGER.info("Closed file mapper. mapMode={}  filePrefix={}", mapMode, filePrefix);
    }

}
