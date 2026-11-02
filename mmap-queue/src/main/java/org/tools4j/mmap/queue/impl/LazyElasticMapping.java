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
import org.tools4j.mmap.queue.config.QueueConfig;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_ADDRESS;
import static org.tools4j.mmap.mapping.api.NullValues.NULL_POSITION;
import static org.tools4j.mmap.mapping.impl.Constraints.validatePosition;

/**
 * Read-only elastic mapping for a queue reader's header file, which may not exist yet. The delegate mapping is created
 * from the queue configuration once the queue exists, and until the header file exists moving fails as it does for a
 * file that does not exist yet. The mapping is {@linkplain #isOpened() opened} once the header file exists; from then
 * on, moves are delegated without checking for the file again.
 * <p>
 * The delegate is created on demand since its configuration, for instance the region size, depends on the queue
 * layout; region metrics are hence not available until then.
 * <p>
 * Like other mappings, this mapping is not thread-safe.
 */
final class LazyElasticMapping implements ElasticMapping {
    private final Supplier<? extends QueueConfig> queueConfig;
    private final Function<? super QueueConfig, ? extends ElasticMapping> mappingFactory;
    private final Predicate<? super QueueConfig> fileExists;
    private QueueConfig config;
    private ElasticMapping delegate;
    private boolean opened;
    private boolean closed;

    /**
     * Constructor with queue configuration supplier, delegate factory and file existence check.
     *
     * @param queueConfig    supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param mappingFactory creates the delegate given the queue configuration in effect
     * @param fileExists     returns true if the file to map exists given the queue configuration the delegate was
     *                       created with; invoked after every failed move until opened, hence it should not allocate
     */
    LazyElasticMapping(final Supplier<? extends QueueConfig> queueConfig,
                       final Function<? super QueueConfig, ? extends ElasticMapping> mappingFactory,
                       final Predicate<? super QueueConfig> fileExists) {
        this.queueConfig = requireNonNull(queueConfig);
        this.mappingFactory = requireNonNull(mappingFactory);
        this.fileExists = requireNonNull(fileExists);
    }

    /**
     * Returns true once the file to map exists, that is, after the first successful move, or after the first failed
     * move when the file exists, for instance when moving beyond its end.
     *
     * @return true if the file to map exists, and false while it is pending to be created
     */
    boolean isOpened() {
        return opened;
    }

    @Override
    public boolean moveTo(final long position) {
        if (opened) {
            return delegate.moveTo(position);
        }
        validatePosition(position);
        if (closed || (delegate == null && !createDelegate())) {
            return false;
        }
        if (delegate.moveTo(position)) {
            opened = true;
            return true;
        }
        opened = fileExists.test(config);
        return false;
    }

    private boolean createDelegate() {
        final QueueConfig cfg = queueConfig.get();
        if (cfg == null) {
            return false;
        }
        delegate = requireNonNull(mappingFactory.apply(cfg));
        config = cfg;
        return true;
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
        return "LazyElasticMapping:delegate=" + delegate + "|opened=" + opened + "|closed=" + closed;
    }
}
