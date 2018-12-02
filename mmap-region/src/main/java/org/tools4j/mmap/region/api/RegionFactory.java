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
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.tools4j.mmap.region.impl.AsyncAtomicStateMachineRegionMapper;
import org.tools4j.mmap.region.impl.AsyncRegion;
import org.tools4j.mmap.region.impl.AsyncVolatileRequestRegionMapper;
import org.tools4j.mmap.region.impl.AsyncVolatileStateMachineRegionMapper;
import org.tools4j.mmap.region.impl.RegionRing;
import org.tools4j.mmap.region.impl.SyncRegion;

@FunctionalInterface
public interface RegionFactory<T> {
    long ASYNC_MAPPING_TIMEOUT_MILLIS = 2000;
    int RING_SIZE = 2;

    T create(int size,
             Supplier<? extends FileChannel> fileChannelSupplier,
             FileSizeEnsurer fileSizeEnsurer,
             FileChannel.MapMode mapMode);

    enum Sync implements RegionFactory<Region> {
        SYNC(SyncRegion::new),
        SYNC_RING((size, fileChannelSupplier, fileSizeEnsurer, mapMode) -> RegionRing.sync(
                RING_SIZE, size, fileChannelSupplier, fileSizeEnsurer, mapMode
        ));

        private final RegionFactory<? extends Region> factory;
        Sync(final RegionFactory<? extends Region> factory) {
            this.factory = Objects.requireNonNull(factory);
        }

        @Override
        public Region create(final int size,
                             final Supplier<? extends FileChannel> fileChannelSupplier,
                             final FileSizeEnsurer fileSizeEnsurer,
                             final FileChannel.MapMode mapMode) {
            return factory.create(size, fileChannelSupplier, fileSizeEnsurer, mapMode);
        }
    }

    enum Async implements RegionFactory<AsyncRegion> {
        ASYNC_VOLATILE_REQUEST(AsyncVolatileRequestRegionMapper::new),
        ASYNC_ATOMIC_STATE_MACHINE(AsyncAtomicStateMachineRegionMapper::new),
        ASYNC_VOLATILE_STATE_MACHINE(AsyncVolatileStateMachineRegionMapper::new);

        private interface AsyncRegionMapperFactory {
            AsyncRegionMapper create(int size,
                                     Supplier<? extends FileChannel> fileChannelSupplier,
                                     FileSizeEnsurer fileSizeEnsurer,
                                     FileChannel.MapMode mapMode);
        }

        private final AsyncRegionMapperFactory mapperFactory;

        Async(final AsyncRegionMapperFactory mapperFactory) {
            this.mapperFactory = Objects.requireNonNull(mapperFactory);
        }
        @Override
        public AsyncRegion create(final int size,
                                  final Supplier<? extends FileChannel> fileChannelSupplier,
                                  final FileSizeEnsurer fileSizeEnsurer,
                                  final FileChannel.MapMode mapMode){
            return new AsyncRegion(
                    mapperFactory.create(size, fileChannelSupplier, fileSizeEnsurer, mapMode),
                    ASYNC_MAPPING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS
            );
        }
    }

    enum AsyncRing implements RegionFactory<RegionRing.AsyncRegionRing> {
        ASYNC_VOLATILE_REQUEST(AsyncVolatileRequestRegionMapper::new),
        ASYNC_ATOMIC_STATE_MACHINE(AsyncAtomicStateMachineRegionMapper::new),
        ASYNC_VOLATILE_STATE_MACHINE(AsyncVolatileStateMachineRegionMapper::new);

        private interface AsyncRegionMapperFactory {
            AsyncRegionMapper create(int size,
                                     Supplier<? extends FileChannel> fileChannelSupplier,
                                     FileSizeEnsurer fileSizeEnsurer,
                                     FileChannel.MapMode mapMode);
        }

         private final RegionFactory<? extends AsyncRegionMapper> mapperFactory;

        AsyncRing(final RegionFactory<? extends AsyncRegionMapper> mapperFactory) {
            this.mapperFactory = Objects.requireNonNull(mapperFactory);
        }
        @Override
        public RegionRing.AsyncRegionRing create(final int size,
                                                 final Supplier<? extends FileChannel> fileChannelSupplier,
                                                 final FileSizeEnsurer fileSizeEnsurer,
                                                 final FileChannel.MapMode mapMode){
            return RegionRing.async(mapperFactory, ASYNC_MAPPING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS, RING_SIZE,
                    size, fileChannelSupplier, fileSizeEnsurer, mapMode);
        }
    }
}
