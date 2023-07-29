/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
import static org.tools4j.mmap.region.impl.Requirements.greaterThanZero;

public class RolledFileMapper implements FileMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RolledFileMapper.class);

    interface IndexedFactory {
        FileMapper create(int index);
    }

    private final long maxFileSize;
    private final int regionSize;
    private final Int2ObjectHashMap<FileMapper> fileMappers = new Int2ObjectHashMap<>();
    private final IntFunction<FileMapper> factory;
    private final String filePrefix;
    private final MapMode mapMode;

    RolledFileMapper(String filePrefix, IndexedFactory indexedFactory, long maxFileSize, int regionSize, MapMode mapMode) {
        greaterThanZero(maxFileSize, "maxFileSize");
        greaterThanZero(regionSize, "regionSize");
        if (maxFileSize % regionSize != 0) {
            throw new IllegalArgumentException(
                    "maxFileSize [" + maxFileSize + "]  must be multiple of regionSize [" + regionSize + "]");
        }
        this.filePrefix = requireNonNull(filePrefix);
        this.maxFileSize = maxFileSize;
        this.regionSize = regionSize;
        this.factory = indexedFactory::create;
        this.mapMode = mapMode;
    }

    public static FileMapper forReadOnly(String filePrefix,
                                          long maxFileSize,
                                          int regionSize,
                                          FileInitialiser fileInitialiser)
    {
        return new RolledFileMapper(filePrefix, index -> new SingleFileReadOnlyFileMapper(filePrefix + "_" + index, fileInitialiser),
                maxFileSize, regionSize, MapMode.READ_ONLY);
    }

    public static FileMapper forReadWrite(String filePrefix,
                                           long maxFileSize,
                                           int regionSize,
                                           FileInitialiser fileInitialiser)
    {
        return new RolledFileMapper(filePrefix,
                index -> new SingleFileReadWriteFileMapper(filePrefix  + "_" + index, maxFileSize, fileInitialiser), maxFileSize,
                regionSize, MapMode.READ_WRITE);
    }

    @Override
    public long map(long position, int length) {
        if (length != regionSize) {
            LOGGER.error("Length {} to map should match region size {}", length, regionSize);
            return NULL_ADDRESS;
        }

        int fileIndex = (int)(position / maxFileSize);
        long positionWithinFile = position % maxFileSize;
        FileMapper mapperForIndex = fileMappers.computeIfAbsent(fileIndex, factory);
        return mapperForIndex.map(positionWithinFile, length);
    }

    @Override
    public void unmap(long address, long position, int length) {
        if (length != regionSize) {
            LOGGER.warn("Length {} to map should match region size {}", length, regionSize);
        }

        int fileIndex = (int)(position / maxFileSize);
        long positionWithinFile = position % maxFileSize;

        FileMapper mapperForIndex = fileMappers.get(fileIndex);
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
