/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.mmap.region.api.RegionCursor;
import org.tools4j.mmap.region.api.RegionMapper;
import org.tools4j.mmap.region.api.RegionMetrics;
import org.tools4j.mmap.region.api.TimeoutException;
import org.tools4j.mmap.region.api.TimeoutHandler;
import org.tools4j.mmap.region.api.WaitingPolicy;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.region.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.region.api.RegionMapper.CLOSED;
import static org.tools4j.mmap.region.api.RegionMapper.FAILED;

public final class ManagedRegionCursor implements RegionCursor {
    private final RegionMapper regionMapper;
    private final WaitingPolicy waitingPolicy;
    private final TimeoutHandler<? super RegionCursor> timeoutHandler;
    private final RegionMetrics regionMetrics;
    private final AtomicBuffer buffer = new UnsafeBuffer(0, 0);
    private long mappedPosition;
    private int mappingState;

    public ManagedRegionCursor(final RegionMapper regionMapper, final WaitingPolicy waitingPolicy) {
        this(regionMapper, waitingPolicy, TimeoutHandler.exception((cur, pol) -> {
            throw new TimeoutException("Readiness not achieved after " + pol.maxWaitTime() + " " + pol.timeUnit());
        }));
    }

    public ManagedRegionCursor(final RegionMapper regionMapper,
                               final WaitingPolicy waitingPolicy,
                               final TimeoutHandler<? super RegionCursor> timeoutHandler) {
        this.regionMapper = requireNonNull(regionMapper);
        this.waitingPolicy = requireNonNull(waitingPolicy);
        this.timeoutHandler = requireNonNull(timeoutHandler);
        this.regionMetrics = regionMapper.regionMetrics();
        this.mappedPosition = NULL_POSITION;
        this.mappingState = FAILED;
    }

    @Override
    public RegionMapper regionMapper() {
        return regionMapper;
    }

    public WaitingPolicy waitingPolicy() {
        return waitingPolicy;
    }

    @Override
    public RegionMetrics regionMetrics() {
        return regionMetrics;
    }

    @Override
    public AtomicBuffer buffer() {
        return buffer;
    }

    @Override
    public long position() {
        return mappedPosition;
    }

    @Override
    public int mappingState() {
        return mappingState;
    }

    @Override
    public boolean moveTo(final long position) {
        final int mappingState = regionMapper.map(position, buffer);
        if (mappingState > 0) {
            this.mappedPosition = position;
            this.mappingState = mappingState;
            return true;
        }
        if (mappingState == CLOSED) {
            this.mappedPosition = NULL_POSITION;
            this.mappingState = CLOSED;
            return false;
        }
        return awaitMapping(position, mappingState);
    }

    private boolean awaitMapping(final long position, final int mappingState) {
        final long maxWaitTimeNanos = waitingPolicy.maxWaitTime(TimeUnit.NANOSECONDS);
        int state = mappingState;
        if (maxWaitTimeNanos > 0) {
            final long startTimeNanos = System.nanoTime();
            final IdleStrategy idleStrategy = waitingPolicy.waitingStrategy();
            idleStrategy.reset();
            do {
                idleStrategy.idle();
                state = regionMapper.map(position, buffer);
            } while (state <= 0 && state != CLOSED && System.nanoTime() - startTimeNanos < maxWaitTimeNanos);
        }
        final boolean mapped = state > 0;
        this.mappedPosition = mapped ? position : NULL_POSITION;
        this.mappingState = state;
        return mapped;
    }

    @Override
    public void close() {
        regionMapper.close();
    }

    @Override
    public String toString() {
        return "ManagedRegionCursor:mappingState=" + mappingState() +
                "|start=" + regionStartPosition() +
                "|offset=" + offset() +
                "|bytesAvailable=" + bytesAvailable();
    }
}
