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
package org.tools4j.eventsourcing;

import org.agrona.collections.MutableReference;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.eventsourcing.api.EventProcessingQueue;
import org.tools4j.eventsourcing.api.EventProcessingState;
import org.tools4j.eventsourcing.api.MessageConsumer;
import org.tools4j.eventsourcing.api.Poller;
import org.tools4j.eventsourcing.config.RegionRingFactoryConfig;
import org.tools4j.eventsourcing.queue.*;
import org.tools4j.eventsourcing.step.DownstreamWhileDoneThenUpstreamUntilDoneStep;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.mmap.region.impl.MappedFile;
import org.tools4j.process.Process;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public class EventSourcingReplayTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventSourcingReplayTest.class);

    public static void main(String... args) throws Exception {

        final int regionSize = (int) Math.max(MappedFile.REGION_SIZE_GRANULARITY, 1L << 16) * 1024 * 4;
        LOGGER.info("regionSize: {}", regionSize);

        final int branchRegionSize = (int) Math.max(MappedFile.REGION_SIZE_GRANULARITY, 1L << 16) * 1024;
        LOGGER.info("regionSize: {}", regionSize);

        final RegionRingFactory regionRingFactory = getRegionRingFactory(args);

        final int ringSize = 4;
        final int regionsToMapAhead = 1;
        final long maxFileSize = 64L * 16 * 1024 * 1024 * 4;
        final int encodingBufferSize = 8 * 1024;
        final String directory = "/Users/anton/IdeaProjects/eventSourcing";
        final LongSupplier systemNanoClock = System::nanoTime;
        final BooleanSupplier leadership = () -> true;

        final MessageConsumer stateMessageConsumer = (buffer, offset, length) -> {};

        final long replayFromSourceId = 1534066796056L;
        final long replayToSourceId = 1534066796256L;

        final MutableReference<EventProcessingState> commitStateRef = new MutableReference<>();

        final EventProcessingQueue queue = new DefaultEventProcessingQueue(
                new ReadOnlyIndexedQueue(
                        directory,
                        "upstream",
                        regionRingFactory,
                        regionSize,
                        ringSize,
                        regionsToMapAhead),
                new BranchedIndexedTransactionalQueue(
                        new DefaultIndexedPollerFactory(
                                directory,
                                "downstream",
                                regionRingFactory,
                                regionSize,
                                ringSize,
                                regionsToMapAhead
                        ),
                        new DefaultIndexedTransactionalQueue(
                            directory,
                            "downstream_branch",
                            true,
                            regionRingFactory,
                            branchRegionSize,
                            ringSize,
                            regionsToMapAhead,
                            maxFileSize,
                            encodingBufferSize),
                        (index, source, sourceId, eventTimeNanos) -> sourceId == replayFromSourceId
                ),
                systemNanoClock,
                leadership,
                Poller.IndexConsumer.noop(),
                Poller.IndexConsumer.noop(),
                Poller.IndexConsumer.noop(),
                Poller.IndexConsumer.noop(),
                (downstreamAppender, upstreamBeforeState, downstreamAfterState) ->
                        (buffer, offset, length) -> {
                            LOGGER.info("Replaying sourceId {}, already applied sourceId {}", upstreamBeforeState.sourceId(), downstreamAfterState.sourceId());
                            downstreamAppender.accept(buffer, offset, length);
                        },
                (upstreamBeforeState, downstreamAfterState) -> {
                    commitStateRef.set(downstreamAfterState);
                    return stateMessageConsumer;
                },
                DownstreamWhileDoneThenUpstreamUntilDoneStep::new
        );

        regionRingFactory.onComplete();

        final Process processor = new Process("ReplayProcessor",
                () -> {}, () -> {},
                new BusySpinIdleStrategy()::idle,
                (s, e) -> LOGGER.error("{} {}", s, e, e),
                10, TimeUnit.SECONDS,
                false,
                () -> commitStateRef.get().sourceId() == replayToSourceId,
                queue.processorStep()
        );

        processor.start();
    }

    private static RegionRingFactory getRegionRingFactory(final String[] args) {
        final String errorMessage = "Please specify a type of mapping (ASYNC/SYNC) as first program argument";
        if (args.length < 1) {
            throw new IllegalArgumentException(errorMessage);
        }
        try {
            return RegionRingFactoryConfig.get(args[0]);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}