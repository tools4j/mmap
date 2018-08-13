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
package org.tools4j.eventsourcing.queue;

import org.tools4j.eventsourcing.api.IndexedPollerFactory;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.eventsourcing.api.RegionAccessorSupplier;
import org.tools4j.eventsourcing.poller.IndexedPoller;
import org.tools4j.eventsourcing.poller.PayloadBufferPoller;
import org.tools4j.mmap.region.api.RegionRingFactory;

import java.io.IOException;
import java.util.Objects;

public class DefaultIndexedPollerFactory implements IndexedPollerFactory {
    private final String directory;
    private final String filePrefix;
    private final RegionRingFactory regionRingFactory;
    private final int regionSize;
    private final int regionRingSize;
    private final int regionsToMapAhead;

    public DefaultIndexedPollerFactory(final String directory,
                                       final String filePrefix,
                                       final RegionRingFactory regionRingFactory,
                                       final int regionSize,
                                       final int regionRingSize,
                                       final int regionsToMapAhead) throws IOException {
        this.directory = Objects.requireNonNull(directory);
        this.filePrefix = Objects.requireNonNull(filePrefix);
        this.regionRingFactory = Objects.requireNonNull(regionRingFactory);
        this.regionSize = regionSize;
        this.regionRingSize = regionRingSize;
        this.regionsToMapAhead = regionsToMapAhead;
    }

    @Override
    public Poller createPoller(final Poller.IndexPredicate skipPredicate,
                               final Poller.IndexPredicate pausePredicate,
                               final Poller.IndexConsumer beforeIndexHandler,
                               final Poller.IndexConsumer afterIndexHandler) throws IOException {
        return new IndexedPoller(
                RegionAccessorSupplier.forReadOnly(
                        directory,
                        filePrefix,
                        regionRingFactory,
                        regionSize,
                        regionRingSize,
                        regionsToMapAhead),
                skipPredicate,
                pausePredicate,
                beforeIndexHandler,
                afterIndexHandler,
                new PayloadBufferPoller());
    }
}
