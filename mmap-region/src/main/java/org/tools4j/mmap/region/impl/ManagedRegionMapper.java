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
package org.tools4j.mmap.region.impl;

import org.tools4j.mmap.region.api.Region;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;

import static java.util.Objects.requireNonNull;

/**
 * A {@link RegionMapper} that uses a {@link WaitingPolicy} to wait for readiness when positions are mapped and invokes
 * a {@link TimeoutHandler} in case mapping is not achieved in time.
 */
public final class ManagedRegionMapper implements RegionMapper {
    private final RegionMapper regionMapper;
    private final WaitingPolicy waitingPolicy;
    private final TimeoutHandler<Region> timeoutHandler;

    public ManagedRegionMapper(final RegionMapper regionMapper, final WaitingPolicy waitingPolicy) {
        this(regionMapper, waitingPolicy, TimeoutHandler.exception(
                (region, policy) -> new IllegalStateException("Mapping region position " + region.position() +
                        " failed: readiness not achieved after " + policy.maxWaitTime() + " " + policy.timeUnit())
        ));
    }

    public ManagedRegionMapper(final RegionMapper regionMapper,
                               final WaitingPolicy waitingPolicy,
                               final TimeoutHandler<Region> timeoutHandler) {
        this.regionMapper = requireNonNull(regionMapper);
        this.waitingPolicy = requireNonNull(waitingPolicy);
        this.timeoutHandler = requireNonNull(timeoutHandler);
    }

    public RegionMapper regionMapper() {
        return regionMapper;
    }

    public WaitingPolicy waitingPolicy() {
        return waitingPolicy;
    }

    public TimeoutHandler<Region> timeoutHandler() {
        return timeoutHandler;
    }

    @Override
    public boolean isAsync() {
        return regionMapper.isAsync();
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMapper.regionMetrics();
    }

    @Override
    public Region map(final long position) {
        final Region region = regionMapper.map(position);
        if (region.awaitReadiness(waitingPolicy) || !region.isPending()) {
            return region;
        }
        return timeoutHandler.handleTimeout(region, waitingPolicy);
    }

    @Override
    public void close() {
        regionMapper.close();
    }

    @Override
    public String toString() {
        return "ManagedRegionMapper" +
                ":async=" + isAsync() +
                "|regionSize=" + regionMetrics().regionSize() +
                "|waitingPolicy=" + waitingPolicy +
                "|timeoutHandler=" + timeoutHandler;
    }

}
