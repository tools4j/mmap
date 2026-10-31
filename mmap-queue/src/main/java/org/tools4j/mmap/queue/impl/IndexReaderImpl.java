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

import org.agrona.CloseHelper;
import org.tools4j.mmap.mapping.api.Describable;
import org.tools4j.mmap.mapping.api.ElasticMapping;
import org.tools4j.mmap.mapping.api.LifecycleListener;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IndexReader;

import static java.util.Objects.requireNonNull;

final class IndexReaderImpl implements IndexReader, Describable {

    private final String queueName;
    private final ElasticMapping header;
    private final Runnable onClose;
    private final LifecycleListener lifecycleListener;

    IndexReaderImpl(final String queueName, final ElasticMapping header, final Runnable onClose,
                    final LifecycleListener lifecycleListener) {
        try {
            this.queueName = requireNonNull(queueName);
            this.header = requireNonNull(header);
            this.onClose = requireNonNull(onClose);
            this.lifecycleListener = requireNonNull(lifecycleListener);
        } catch (final Throwable t) {
            CloseHelper.quietClose(header);
            throw t;
        }
        lifecycleListener.onOpened(this);
    }

    @Override
    public long lastIndex() {
        checkNotClosed();
        return Headers.binarySearchLastIndex(header, Index.FIRST);
    }

    @Override
    public boolean hasEntry(final long index) {
        checkNotClosed();
        return Headers.hasNonEmptyHeaderAt(header, index);
    }

    @Override
    public boolean isClosed() {
        return header.isClosed();
    }

    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Index reader is closed");
        }
    }

    @Override
    public void close() {
        if (!isClosed()) {
            header.close();
            onClose.run();
            lifecycleListener.onClosed(this);
        }
    }

    @Override
    public StringBuilder describeTo(final StringBuilder sb) {
        return sb.append("IndexReaderImpl")
                .append(":queue=").append(queueName)
                .append("|closed=").append(isClosed());
    }

    @Override
    public String toString() {
        return Describable.describe(this);
    }
}
