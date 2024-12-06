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

import org.tools4j.mmap.queue.api.Entry;
import org.tools4j.mmap.queue.api.Index;
import org.tools4j.mmap.queue.api.IterableContext;
import org.tools4j.mmap.queue.api.EntryReader;
import org.tools4j.mmap.queue.api.ReadingContext;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;

final class DefaultIterableContext implements IterableContext, Iterable<Entry>, Iterator<Entry> {
    interface MutableReadingContext extends ReadingContext {
        boolean tryInit(long index);
        Entry entry();
    }

    private long startIndex = Index.NULL;
    private long index = Index.NULL;
    private Entry entry;
    private boolean reverse;

    private final EntryReader reader;
    private final MutableReadingContext readingContext;

    DefaultIterableContext(final EntryReader reader, final MutableReadingContext readingContext) {
        this.reader = requireNonNull(reader);
        this.readingContext = requireNonNull(readingContext);
    }

    DefaultIterableContext init(final long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + index);
        }
        this.startIndex = index;
        this.index = Index.NULL;
        return this;
    }

    @Override
    public long startIndex() {
        return startIndex;
    }

    @Override
    public Iterator<Entry> iterator() {
        readingContext.close();
        return this;
    }

    @Override
    public IterableContext reverse() {
        reverse = !reverse;
        return this;
    }

    public long nextIndex() {
        final long curIndex = readingContext.index();
        if (curIndex != Index.NULL) {
            return reverse ? curIndex - 1 : curIndex + 1;
        }
        return startIndex();
    }

    @Override
    public boolean hasNext() {
        if (entry != null) {
            return true;
        }
        final long nextIndex = nextIndex();
        if (nextIndex >= 0 && readingContext.tryInit(nextIndex)) {
            entry = readingContext.entry();
            return true;
        }
        return false;
    }

    @Override
    public Entry next() {
        final Entry next = entry;
        if (next != null) {
            entry = null;
            return next;
        }
        throw new NoSuchElementException();
    }

    @Override
    public boolean isClosed() {
        return readingContext.isClosed();
    }

    @Override
    public void close() {
        startIndex = Index.NULL;
        reverse = false;
        entry = null;
        readingContext.close();
    }
}
