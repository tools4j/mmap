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
