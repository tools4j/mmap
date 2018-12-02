/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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

import java.nio.channels.FileChannel;
import java.util.function.Supplier;

import org.agrona.IoUtil;

import org.tools4j.mmap.region.api.AsyncRegionMapper;
import org.tools4j.mmap.region.api.FileSizeEnsurer;

public class AsyncVolatileRequestRegionMapper extends AbstractRegionMapper implements AsyncRegionMapper {

    private volatile long requestedPosition = -1;
    private volatile long mappedPosition = -1;
    private volatile long mappedAddress = NULL;

    public AsyncVolatileRequestRegionMapper(final int regionSize,
                                            final Supplier<? extends FileChannel> fileChannelSupplier,
                                            final FileSizeEnsurer fileSizeEnsurer,
                                            final FileChannel.MapMode mapMode) {
        super(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
    }

    @Override
    public long map(final long position) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative: " + position);
        }
        if (currentPosition == position) {
            return currentAddress;
        }
        if (requestedPosition == position & mappedPosition == position) {
            currentPosition = mappedPosition;
            currentAddress = mappedAddress;
            return currentAddress;
        }

        requestedPosition = position;
        currentPosition = -2;
        currentAddress = NULL;
        return NULL;
    }

    @Override
    public boolean unmap() {
        if (currentPosition == -1) {
            return true;
        }
        if (requestedPosition == -1 & mappedPosition == -1) {
            currentPosition = mappedPosition;
            currentAddress = mappedAddress;
            return true;
        }

        requestedPosition = -1;
        currentPosition = -2;
        currentAddress = NULL;
        return false;
    }

    @Override
    public boolean processMappingRequests() {
        final long reqPosition = requestedPosition;
        if (reqPosition != mappedPosition) {
            boolean workDone = false;
            if (mappedAddress != NULL) {
                IoUtil.unmap(fileChannelSupplier.get(), mappedAddress, regionSize);
                mappedAddress = NULL;
                mappedPosition = -1;
                workDone = true;
            }
            if (reqPosition >= 0) {
                if (fileSizeEnsurer.ensureSize(reqPosition + regionSize)) {
                    mappedAddress = IoUtil.map(fileChannelSupplier.get(), mapMode, reqPosition, regionSize);
                    mappedPosition = reqPosition;
                    workDone = true;
                }
            }
            return workDone;
        }
        return false;
    }
}
