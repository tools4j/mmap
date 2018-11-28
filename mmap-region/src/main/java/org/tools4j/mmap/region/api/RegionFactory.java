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
package org.tools4j.mmap.region.api;

import java.nio.channels.FileChannel;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.tools4j.mmap.region.api.RegionMapper.IoMapper;
import org.tools4j.mmap.region.api.RegionMapper.IoUnmapper;
import org.tools4j.mmap.region.impl.AsyncAtomicExchangeRegion;
import org.tools4j.mmap.region.impl.AsyncAtomicStateMachineRegion;
import org.tools4j.mmap.region.impl.AsyncVolatileStateMachineRegion;
import org.tools4j.mmap.region.impl.SyncRegion;

public interface RegionFactory<T extends Region> {
    RegionFactory<AsyncRegion> ASYNC_ATOMIC_STATE_MACHINE =
            (size, fileChannelSupplier, fileSizeEnsurer, mapMode) -> new AsyncAtomicStateMachineRegion(fileChannelSupplier,
                    IoMapper.DEFAULT, IoUnmapper.DEFAULT, fileSizeEnsurer, mapMode, size,
                    2, TimeUnit.SECONDS);
    RegionFactory<AsyncRegion> ASYNC_VOLATILE_STATE_MACHINE =
            (size, fileChannelSupplier, fileSizeEnsurer, mapMode) -> new AsyncVolatileStateMachineRegion(fileChannelSupplier,
                    IoMapper.DEFAULT, IoUnmapper.DEFAULT, fileSizeEnsurer, mapMode, size,
                    2, TimeUnit.SECONDS);
    RegionFactory<AsyncRegion> ASYNC_ATOMIC_EXCHANGE =
            (size, fileChannelSupplier, fileSizeEnsurer, mapMode) -> new AsyncAtomicExchangeRegion(fileChannelSupplier,
                    IoMapper.DEFAULT, IoUnmapper.DEFAULT, fileSizeEnsurer, mapMode, size,
                    2, TimeUnit.SECONDS);
    RegionFactory<Region> SYNC =
            (size, fileChannelSupplier, fileSizeEnsurer, mapMode) -> new SyncRegion(fileChannelSupplier,
                    IoMapper.DEFAULT, IoUnmapper.DEFAULT, fileSizeEnsurer, mapMode, size);

    T create(int size,
             Supplier<? extends FileChannel> fileChannelSupplier,
             FileSizeEnsurer fileSizeEnsurer,
             FileChannel.MapMode mapMode);

}
