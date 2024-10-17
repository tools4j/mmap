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
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.queue.api.ReadingContext;
import org.tools4j.mmap.queue.impl.DefaultIterableContext.MutableReadingContext;
import org.tools4j.mmap.region.api.DynamicMapping;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.impl.Headers.HEADER_WORD;

final class ReaderImpl implements Reader {
    ReaderImpl(final String queueName, final QueueMappings regionCursors) {
    }


    @Override
    public long lastIndex() {
        return 0;
    }

    @Override
    public boolean hasEntry(final long index) {
        return false;
    }

    @Override
    public ReadingContext reading(final long index) {
        return null;
    }

    @Override
    public ReadingContext readingFirst() {
        return null;
    }

    @Override
    public ReadingContext readingLast() {
        return null;
    }

    @Override
    public IterableContext readingFrom(final long index) {
        return null;
    }

    @Override
    public IterableContext readingFromFirst() {
        return null;
    }

    @Override
    public IterableContext readingFromLast() {
        return null;
    }

    @Override
    public IterableContext readingFromEnd() {
        return null;
    }

    @Override
    public void close() {

    }
}
