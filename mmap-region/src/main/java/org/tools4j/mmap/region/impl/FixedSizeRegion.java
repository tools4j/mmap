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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.FileMapper;
import org.tools4j.mmap.region.api.RegionAccessor;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class FixedSizeRegion implements RegionAccessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FixedSizeRegion.class);

    private final String file;
    private final FileMapper fileMapper;
    private final long address;
    private final int size;

    private final UnsafeBuffer buffer = new UnsafeBuffer();

    public FixedSizeRegion(String directory, String fileName, int size, FileInitialiser fileInitialiser) {
        file = requireNonNull(directory) + "/" + requireNonNull(fileName);
        fileMapper = new SingleFileReadWriteFileMapper(file, size, fileInitialiser);
        address = fileMapper.map(0, size);
        this.size = size;
        if (address == FileMapper.NULL_ADDRESS) {
            throw new IllegalStateException(format("Failed to map %d bytes file %s to memory", size, file));
        }
        buffer.wrap(address, size);
    }

    public static RegionAccessor open(String directory, String fileName, int size, FileInitialiser fileInitialiser) {
        return new FixedSizeRegion(directory, fileName, size, fileInitialiser);
    }

    @Override
    public boolean wrap(long position, DirectBuffer buffer) {
        if (position >= size) {
            LOGGER.error("Cannot map position {} longer than the size {} of the file {}", position, size, file);
            return false;
        }
        buffer.wrap(this.buffer, (int) position, size - (int) position);
        return true;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void close() {
        fileMapper.unmap(address, 0, size);
        fileMapper.close();
        LOGGER.info("Closed region. file={}", file);
    }

    @Override
    public String toString() {
        return "FixedSizeRegion{" + "file='" + file + '\'' + '}';
    }

}
