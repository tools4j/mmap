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

import org.agrona.DirectBuffer;
import org.tools4j.mmap.region.api.AsyncRegion;
import org.tools4j.mmap.region.api.FileSizeEnsurer;
import org.tools4j.mmap.region.api.Region;

import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class AsyncAtomicExchangeRegion implements AsyncRegion {
    private static final long NULL = -1;

    private final Supplier<FileChannel> fileChannelSupplier;
    private final Region.IoMapper ioMapper;
    private final IoUnmapper ioUnmapper;
    private final FileSizeEnsurer fileSizeEnsurer;
    private final FileChannel.MapMode mapMode;
    private final int length;
    private final long timeoutNanos;


    private final AtomicLong requestPosition = new AtomicLong(NULL);
    private final AtomicLong responseAddress = new AtomicLong(NULL);

    private long readerPosition = NULL;
    private long readerAddress = NULL;

    private long writerPosition = NULL;
    private long writerAddress = NULL;

    public AsyncAtomicExchangeRegion(final Supplier<FileChannel> fileChannelSupplier,
                                     final IoMapper ioMapper,
                                     final IoUnmapper ioUnmapper,
                                     final FileSizeEnsurer fileSizeEnsurer,
                                     final FileChannel.MapMode mapMode,
                                     final int length,
                                     final long timeout,
                                     final TimeUnit timeUnits) {
        this.fileChannelSupplier = Objects.requireNonNull(fileChannelSupplier);
        this.ioMapper = Objects.requireNonNull(ioMapper);
        this.ioUnmapper = Objects.requireNonNull(ioUnmapper);
        this.fileSizeEnsurer = Objects.requireNonNull(fileSizeEnsurer);
        this.mapMode = Objects.requireNonNull(mapMode);
        this.length = length;
        this.timeoutNanos = timeUnits.toNanos(timeout);
    }

    @Override
    public boolean wrap(final long position, final DirectBuffer source) {
        final int regionOffset = (int) (position & (this.length - 1));
        final long regionStartPosition = position - regionOffset;
        if (awaitMapped(regionStartPosition)) {
            source.wrap(readerAddress + regionOffset, this.length - regionOffset);
            return true;
        }
        return false;
    }

    @Override
    public boolean map(final long regionStartPosition) {
        if (readerPosition == regionStartPosition) return true;

        readerPosition = NULL;
        requestPosition.set(regionStartPosition); //can be lazy

        return false;
    }

    @Override
    public boolean unmap() {
        final boolean hadBeenUnmapped = readerPosition == NULL;
        readerPosition = NULL;
        requestPosition.set(NULL); //can be lazy

        return hadBeenUnmapped;
    }

    private boolean awaitMapped(final long regionStartPosition) {
        if (!map(regionStartPosition)) {
            final long timeOutTimeNanos = System.nanoTime() + timeoutNanos;
            long respAddress;
            do {
                if (timeOutTimeNanos <= System.nanoTime()) return false; // timeout
                respAddress = responseAddress.get();
            } while (readerAddress == respAddress);

            readerAddress = respAddress;
            readerPosition = regionStartPosition;
        }
        return true;
    }

    @Override
    public boolean processRequest() {
        final long reqPosition = requestPosition.get();
        if (writerPosition != reqPosition) {
            if (writerAddress != NULL) {
                ioUnmapper.unmap(fileChannelSupplier.get(), writerAddress, length);
                writerAddress = NULL;
            }
            if (reqPosition != NULL) {
                if (fileSizeEnsurer.ensureSize(reqPosition + length)) {
                    writerAddress = ioMapper.map(fileChannelSupplier.get(), mapMode, reqPosition, length);
                }
            }
            writerPosition = reqPosition;
            responseAddress.set(writerAddress); //can be lazy
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        unmap();
    }

    @Override
    public int size() {
        return length;
    }

}
