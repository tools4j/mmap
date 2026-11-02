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

import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.queue.config.QueueConfig;

import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Reader mappings of a queue that may not exist yet, delegating to reader mappings created once the queue exists,
 * since their configuration, for instance the region size, depends on the queue layout. Until then, {@link #header()}
 * returns null.
 * <p>
 * Like the mappings, this class is not thread-safe.
 */
final class LazyReaderMappings implements ReaderMappings {
    private final Supplier<? extends QueueConfig> queueConfig;
    private final Function<? super QueueConfig, ? extends ReaderMappings> mappingsFactory;
    private ReaderMappings delegate;
    private boolean closed;

    /**
     * Constructor with queue configuration supplier and factory for the delegate mappings.
     *
     * @param queueConfig     supplies the queue configuration in effect, or null if the queue does not exist yet
     * @param mappingsFactory creates the delegate mappings given the queue configuration in effect
     */
    LazyReaderMappings(final Supplier<? extends QueueConfig> queueConfig,
                       final Function<? super QueueConfig, ? extends ReaderMappings> mappingsFactory) {
        this.queueConfig = requireNonNull(queueConfig);
        this.mappingsFactory = requireNonNull(mappingsFactory);
    }

    @Override
    public ElasticMapping header() {
        final ReaderMappings mappings = delegate;
        return mappings != null ? mappings.header() : closed ? null : createDelegateHeader();
    }

    private ElasticMapping createDelegateHeader() {
        final QueueConfig cfg = queueConfig.get();
        if (cfg == null) {
            return null;
        }
        final ReaderMappings mappings = requireNonNull(mappingsFactory.apply(cfg));
        delegate = mappings;
        return mappings.header();
    }

    @Override
    public ElasticMapping payload(final int appenderId) {
        //NOTE: payload is mapped only after reading a header, hence the queue exists
        assert delegate != null : "queue must exist";
        return delegate.payload(appenderId);
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
        return "LazyReaderMappings:delegate=" + delegate + "|closed=" + closed;
    }
}
