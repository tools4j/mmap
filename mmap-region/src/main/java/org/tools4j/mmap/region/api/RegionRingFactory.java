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
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Region ring factory.
 */
@FunctionalInterface
public interface RegionRingFactory {
    /**
     * Creates array of regions based on the length of the array and size of a region.
     *
     * @param ringSize              how many regions to buffer in the ring
     * @param regionSize            bytes per region
     * @param fileChannelSupplier   supplier of file channel
     * @param fileSizeEnsurer       ansures sufficient file size is guaranteed
     * @param mapMode               file map mode
     * @return array of regions
     */
    Region[] create(int ringSize,
                    int regionSize,
                    Supplier<? extends FileChannel> fileChannelSupplier,
                    FileSizeEnsurer fileSizeEnsurer,
                    FileChannel.MapMode mapMode);

    static RegionRingFactory forAsync(final RegionFactory<? extends AsyncRegion> regionFactory) {
        Objects.requireNonNull(regionFactory);
        final List<Runnable> regionRingProcessors = new CopyOnWriteArrayList<>();
        final Thread regionMapper = new Thread(() -> {
            System.out.println("started: region-mapper");
            while (true) {
                for(int index = 0; index < regionRingProcessors.size(); index++) {
                    final Runnable regionRingProcessor = regionRingProcessors.get(index);
                    regionRingProcessor.run();
                }
            }
        });
        regionMapper.setName("region-mapper");
        regionMapper.setDaemon(true);
        regionMapper.setUncaughtExceptionHandler((t, e) -> System.err.println(e.getMessage()));
        regionMapper.start();


        return (ringSize, regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode) -> {
            final AsyncRegion[] regions = new AsyncRegion[ringSize];

            for (int i = 0; i < ringSize; i++) {
                regions[i] = regionFactory.create(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
            }

            regionRingProcessors.add(() -> {
                for (final AsyncRegionMapper asyncRegionMapper : regions) {
                    asyncRegionMapper.processRequest();
                }
            });
            return regions;
        };
    }

    static RegionRingFactory forSync(final RegionFactory<? extends Region> regionFactory) {
        Objects.requireNonNull(regionFactory);

        return (ringSize, regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode) -> {
            final Region[] regions = new Region[ringSize];

            for (int i = 0; i < ringSize; i++) {
                regions[i] = regionFactory.create(regionSize, fileChannelSupplier, fileSizeEnsurer, mapMode);
            }
            return regions;
        };
    }

    static RegionRingFactory async() {
        return RegionRingFactory.forAsync(RegionFactory.ASYNC_VOLATILE_STATE_MACHINE);
    }

    static RegionRingFactory sync() {
        return RegionRingFactory.forSync(RegionFactory.SYNC);
    }

}
