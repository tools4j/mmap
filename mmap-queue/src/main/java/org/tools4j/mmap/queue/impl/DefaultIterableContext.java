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

import org.tools4j.mmap.queue.api.Direction;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.queue.api.Reader.Entry;
import org.tools4j.mmap.queue.api.Reader.IterableContext;
import org.tools4j.mmap.queue.api.Reader.ReadingContext;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;
import static org.tools4j.mmap.queue.api.Direction.BACKWARD;
import static org.tools4j.mmap.queue.api.Direction.FORWARD;
import static org.tools4j.mmap.queue.api.Reader.NULL_INDEX;

class DefaultIterableContext<E extends Reader.Entry> implements IterableContext, Iterable<E>, Iterator<E> {
    interface MutableReadingContext<E extends Entry> extends ReadingContext {
        boolean tryInit(long index);
        E entry();
    }

    private long startIndex = NULL_INDEX;
    private Direction direction = Direction.NONE;
    private E entry;

    private final Reader reader;
    private final MutableReadingContext<E> readingContext;

    DefaultIterableContext(final Reader reader, final MutableReadingContext<E> readingContext) {
        this.reader = requireNonNull(reader);
        this.readingContext = requireNonNull(readingContext);
    }

    DefaultIterableContext<E> init(final long index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative: " + index);
        }
        this.startIndex = index;
        return this;
    }

    @Override
    public long startIndex() {
        return startIndex != Long.MAX_VALUE ? startIndex : reader.lastIndex();
    }

    @Override
    public Iterable<E> iterate(final Direction direction) {
        this.direction = requireNonNull(direction);
        return this;
    }

    @Override
    public Iterator<E> iterator() {
        readingContext.close();
        return this;
    }

    public long nextIndex() {
        final long curIndex = readingContext.index();
        if (curIndex != NULL_INDEX) {
            return direction == FORWARD ? curIndex + 1 : direction == BACKWARD ? curIndex - 1 : NULL_INDEX;
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
    public E next() {
        final E next = entry;
        if (next != null) {
            entry = null;
            return next;
        }
        throw new NoSuchElementException();
    }

    @Override
    public void close() {
        startIndex = NULL_INDEX;
        direction = Direction.NONE;
        entry = null;
        readingContext.close();
    }
}
