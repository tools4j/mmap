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
