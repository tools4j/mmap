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
package org.tools4j.mmap.queue.impl;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Appender;
import org.tools4j.mmap.queue.api.AppendingContext;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;

final class AppenderImpl implements Appender {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppenderImpl.class);

    private final String queueName;
    private final int appenderId;
    private final OffsetMapping header;
    private final OffsetMapping payload;
    private final AppendingContextImpl context = new AppendingContextImpl();
    private long currentIndex;
    private boolean closed;

    public AppenderImpl(final String queueName, final QueueMappings regions, final AppenderIdPool appenderIdPool) {
        this.queueName = requireNonNull(queueName);
        this.appenderId = appenderIdPool.acquire();
        this.header = requireNonNull(regions.header());
        this.payload = requireNonNull(regions.payload(appenderId));
        this.currentIndex = Index.NULL;
        initialMoveToEnd();
    }

    private static void checkIndexNotExceedingMax(final long index) {
        if (index > Index.MAX) {
            throw new IllegalStateException("Max index reached: " + Index.MAX);
        }
    }

    /** Binary search to move to the end starting from first entry */
    private void initialMoveToEnd() {
        final OffsetMapping hdr = header;
        final long lastIndex = Headers.binarySearchLastIndex(hdr, Index.FIRST);
        final long endIndex = lastIndex + 1;
        checkIndexNotExceedingMax(endIndex);
        if (lastIndex >= Index.FIRST) {
            currentIndex = lastIndex;
        }
    }

    /** Linear move to the end starting from current index */
    private void moveToEnd() {
        final OffsetMapping hdr = header;
        long endIndex = currentIndex;
        do {
            endIndex++;
        } while (Headers.hasNonEmptyHeaderAt(hdr, endIndex));
        checkIndexNotExceedingMax(endIndex);
        if (endIndex != currentIndex) {
            currentIndex = endIndex;
        }
    }

    @Override
    public long append(final DirectBuffer buffer, final int offset, final int length) {
        try (final AppendingContext context = appending()) {
            context.buffer().putBytes(0, buffer, offset, length);
            return context.commit(length);
        }
    }

    @Override
    public AppendingContext appending() {
        return context.init();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!isClosed()) {
            closed = true;
            currentIndex = Index.NULL;
            //FIXME currentHeader = NULL_HEADER;
            LOGGER.info("Appender closed, queue={}", queueName);
        }
    }

    @Override
    public String toString() {
        return "AppenderImpl:queue=" + queueName + "|closed=" + closed;
    }

    private final class AppendingContextImpl implements AppendingContext {

        MutableDirectBuffer buffer;

        AppendingContext init() {
            if (buffer != null) {
                abort();
                throw new IllegalStateException("Appending context not closed");
            }
            //FIXME
            return this;
        }

        @Override
        public MutableDirectBuffer buffer() {
            return null;
        }

        @Override
        public void abort() {

        }

        @Override
        public long commit(final int length) {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return false;
        }
    }
}
