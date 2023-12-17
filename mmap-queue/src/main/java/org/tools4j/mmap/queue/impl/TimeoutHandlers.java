/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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
package org.tools4j.mmap.queue.impl;

import org.slf4j.Logger;
import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;

import static java.util.Objects.requireNonNull;

public enum TimeoutHandlers {
    ;
    public static TimeoutHandler<Region> noOp() {
        return TimeoutHandler.noOp();
    }

    public static TimeoutHandler<Region> log(final Logger logger, final String name, final boolean exception) {
        return exception ? logAndException(logger, name) : log(logger, name);
    }

    public static TimeoutHandler<Region> log(final Logger logger, final String name) {
        requireNonNull(logger);
        requireNonNull(name);
        return (viewport, waitingPolicy) -> {
            logger.error("Moving {} region to position {} failed: readiness not achieved after {} {}",
                    name, viewport.position(), waitingPolicy.maxWaitTime(), waitingPolicy.timeUnit()
            );
            return viewport;
        };
    }

    public static TimeoutHandler<Region> logAndException(final Logger logger, final String name) {
        return TimeoutHandler.consecutive(log(logger, name), exception(name));
    }

    public static TimeoutHandler<Region> exception(final String name) {
        requireNonNull(name);
        return TimeoutHandler.exception((viewport, policy) -> illegalStateException(name, viewport, policy));
    }

    public static IllegalStateException illegalStateException(final String name,
                                                              final Region region,
                                                              final WaitingPolicy waitingPolicy) {
        requireNonNull(name);
        requireNonNull(region);
        requireNonNull(waitingPolicy);
        return new IllegalStateException("Mapping " + name + " region to position " + region.position() +
                " failed: readiness not achieved after " + waitingPolicy.maxWaitTime() + " " + waitingPolicy.timeUnit()
        );
    }
}
