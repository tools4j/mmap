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
package org.tools4j.eventsourcing.poller;

import org.agrona.collections.Long2LongHashMap;
import org.tools4j.eventsourcing.api.EventProcessingState;
import org.tools4j.eventsourcing.api.Poller;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class DefaultEventProcessingState implements EventProcessingState, Poller.IndexConsumer {
    private final Long2LongHashMap sourceMap;
    private final LongSupplier systemNanoClock;

    private long id;
    private int source;
    private long sourceId;
    private long eventTimeNanos;
    private long ingestionTimeNanos;

    public DefaultEventProcessingState(final LongSupplier systemNanoClock) {
        this.systemNanoClock = Objects.requireNonNull(systemNanoClock);
        this.sourceMap = new Long2LongHashMap(-1);
        this.eventTimeNanos = 0;
    }

    @Override
    public long sourceId(final int source) {
        return sourceMap.get(source);
    }

    @Override
    public void accept(final long id, final int source, final long sourceId, final long eventTimeNanos) {
        sourceMap.put(source, sourceId);
        this.id = id;
        this.source = source;
        this.sourceId = sourceId;
        this.eventTimeNanos = eventTimeNanos;
        this.ingestionTimeNanos = systemNanoClock.getAsLong();
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public int source() {
        return source;
    }

    @Override
    public long sourceId() {
        return sourceId;
    }

    @Override
    public long eventTimeNanos() {
        return eventTimeNanos;
    }

    @Override
    public long ingestionTimeNanos() {
        return ingestionTimeNanos;
    }
}
