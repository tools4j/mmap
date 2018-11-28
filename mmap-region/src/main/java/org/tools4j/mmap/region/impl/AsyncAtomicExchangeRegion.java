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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.tools4j.mmap.region.api.FileSizeEnsurer;

public class AsyncAtomicExchangeRegion extends AbstractAsyncRegion {
    private final AtomicLong requestPosition = new AtomicLong(-1);
    private final AtomicLong responseAddress = new AtomicLong(NULL);

    private long writerPosition = -1;
    private long writerAddress = NULL;

    public AsyncAtomicExchangeRegion(final Supplier<? extends FileChannel> fileChannelSupplier,
                                     final IoMapper ioMapper,
                                     final IoUnmapper ioUnmapper,
                                     final FileSizeEnsurer fileSizeEnsurer,
                                     final FileChannel.MapMode mapMode,
                                     final int regionSize,
                                     final long timeout,
                                     final TimeUnit unit) {
        super(fileChannelSupplier, ioMapper, ioUnmapper, fileSizeEnsurer, mapMode, regionSize, timeout, unit);
    }

    @Override
    public long map(final long regionStartPosition) {
        if (position == regionStartPosition) {
            return address;
        }

        position = -1;
        requestPosition.set(regionStartPosition); //can be lazy

        return NULL;
    }

    @Override
    public long map(long regionStartPosition, long timeout, TimeUnit unit) {
        long addr = map(regionStartPosition);
        if (addr == NULL) {
            final long nanos = unit.toNanos(timeout);
            final long start = System.nanoTime();
            long respAddress;
            do {
                respAddress = responseAddress.get();
            } while (addr == respAddress && System.nanoTime() - start < nanos);

            this.address = respAddress;
            this.position = regionStartPosition;
        }
        return addr;
    }

    @Override
    public boolean unmap() {
        final boolean hadBeenUnmapped = position == -1;
        position = -1;
        requestPosition.set(-1); //can be lazy

        return hadBeenUnmapped;
    }

    @Override
    public boolean processRequest() {
        final long reqPosition = requestPosition.get();
        if (writerPosition != reqPosition) {
            if (writerAddress != NULL) {
                ioUnmapper.unmap(fileChannelSupplier.get(), writerAddress, regionSize);
                writerAddress = NULL;
            }
            if (reqPosition != -1) {
                if (fileSizeEnsurer.ensureSize(reqPosition + regionSize)) {
                    writerAddress = ioMapper.map(fileChannelSupplier.get(), mapMode, reqPosition, regionSize);
                }
            }
            writerPosition = reqPosition;
            responseAddress.set(writerAddress); //can be lazy
            return true;
        }
        return false;
    }
}
