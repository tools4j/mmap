package org.tools4j.eventsourcing.config;

import org.agrona.concurrent.BusySpinIdleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.region.api.RegionFactory;
import org.tools4j.mmap.region.api.RegionRingFactory;
import org.tools4j.process.MutableProcessStepChain;
import org.tools4j.process.Process;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RegionRingFactoryConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegionRingFactoryConfig.class);

    private static final Supplier<RegionRingFactory> ASYNC = () -> {
        final MutableProcessStepChain processStepChain = new MutableProcessStepChain();
        return RegionRingFactory.forAsync(RegionFactory.ASYNC_VOLATILE_STATE_MACHINE,
                processor -> processStepChain.thenStep(processor::process),
                () -> {
                    final Process regionMapper = new Process("RegionMapper",
                            () -> {}, () -> {},
                            new BusySpinIdleStrategy()::idle,
                            (s, e) -> LOGGER.error("{} {}", s, e, e),
                            10, TimeUnit.SECONDS,
                            true,
                            () -> false,
                            processStepChain.getOrNoop()
                    );
                    regionMapper.start();
                });
    };

    private static final Supplier<RegionRingFactory> SYNC = () -> RegionRingFactory.forSync(RegionFactory.SYNC);

    public static RegionRingFactory get(final String name) {
        switch (name) {
            case "SYNC": return SYNC.get();
            case "ASYNC" :return ASYNC.get();
            default: throw new IllegalArgumentException("Unknown regionRingFactory name " + name);
        }
    }
}
