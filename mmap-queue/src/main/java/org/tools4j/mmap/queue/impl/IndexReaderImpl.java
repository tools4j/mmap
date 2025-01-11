/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2025 tools4j.org (Marco Terzer, Anton Anufriev)
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
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IndexReader;
import org.tools4j.mmap.region.api.OffsetMapping;

import static java.util.Objects.requireNonNull;

final class IndexReaderImpl implements IndexReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexReaderImpl.class);

    private final String queueName;
    private final OffsetMapping header;

    IndexReaderImpl(final String queueName, final OffsetMapping header) {
        this.queueName = requireNonNull(queueName);
        this.header = requireNonNull(header);
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
            LOGGER.info("Index reader closed, queue={}", queueName);
        }
    }

    @Override
    public String toString() {
        return "IndexReaderImpl:queue=" + queueName + "|closed=" + isClosed();
    }
}
