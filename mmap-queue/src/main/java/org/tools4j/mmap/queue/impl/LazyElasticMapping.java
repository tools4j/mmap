/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2026 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.agrona.concurrent.AtomicBuffer;
import org.tools4j.mmap.mapping.api.AccessMode;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.RegionMetrics;
import org.tools4j.mmap.mapping.impl.EmptyBuffer;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.Constraints.validatePosition;

/**
 * Read-only elastic mapping for a queue that does not exist yet, delegating to a mapping that is created once the
 * queue exists. Until then, moving fails as it does for a file that does not exist yet.
 * <p>
 * The delegate is created on demand since its configuration, for instance the region size, depends on the queue
 * layout; region metrics are hence not available until then.
 * <p>
 * Like other mappings, this mapping is not thread-safe.
 */
final class LazyElasticMapping implements ElasticMapping {
    private final Supplier<? extends ElasticMapping> mappingFactory;
    private ElasticMapping delegate;
    private boolean closed;

    /**
     * Constructor with factory for the delegate mapping.
     *
     * @param mappingFactory factory for the delegate, returning null if the delegate cannot be created yet
     */
    LazyElasticMapping(final Supplier<? extends ElasticMapping> mappingFactory) {
        this.mappingFactory = requireNonNull(mappingFactory);
    }

    @Override
    public boolean moveTo(final long position) {
        if (delegate != null) {
            return delegate.moveTo(position);
        }
        validatePosition(position);
        return !closed && createDelegate() && delegate.moveTo(position);
    }

    private boolean createDelegate() {
        delegate = mappingFactory.get();
        return delegate != null;
    }

    @Override
    public boolean moveBy(final long delta) {
        return delegate != null ? delegate.moveBy(delta) : ElasticMapping.super.moveBy(delta);
    }

    @Override
    public AccessMode accessMode() {
        return AccessMode.READ_ONLY;
    }

    @Override
    public RegionMetrics regionMetrics() {
        if (delegate != null) {
            return delegate.regionMetrics();
        }
        throw new IllegalStateException("Region metrics are not available before the queue exists");
    }

    @Override
    public int regionOffset() {
        return delegate != null ? delegate.regionOffset() : 0;
    }

    @Override
    public long regionStartPosition() {
        return delegate != null ? delegate.regionStartPosition() : NULL_POSITION;
    }

    @Override
    public int positionStepSize() {
        return 1;
    }

    @Override
    public long position() {
        return delegate != null ? delegate.position() : NULL_POSITION;
    }

    @Override
    public long address() {
        return delegate != null ? delegate.address() : NULL_ADDRESS;
    }

    @Override
    public AtomicBuffer buffer() {
        return delegate != null ? delegate.buffer() : EmptyBuffer.INSTANCE;
    }

    @Override
    public boolean isMapped() {
        return delegate != null && delegate.isMapped();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (delegate != null) {
                delegate.close();
            }
        }
    }

    @Override
    public String toString() {
        return "LazyElasticMapping:delegate=" + delegate + "|closed=" + closed;
    }
}
