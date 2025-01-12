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

import org.agrona.DirectBuffer;
import org.tools4j.mmap.queue.api.Direction;
import org.tools4j.mmap.queue.api.Reader;
import org.tools4j.mmap.queue.api.Reader.Entry;
import org.tools4j.mmap.queue.api.Reader.IterableContext;
import org.tools4j.mmap.queue.api.Reader.ReadingContext;
import org.tools4j.mmap.region.impl.EmptyBuffer;

import java.util.Collections;

import static java.util.Objects.requireNonNull;

class EmptyReadingContext<E extends Entry> implements ReadingContext, IterableContext {
    @Override
    public long index() {
        return Reader.NULL_INDEX;
    }

    @Override
    public DirectBuffer buffer() {
        return EmptyBuffer.INSTANCE;
    }

    @Override
    public boolean hasEntry() {
        return false;
    }

    @Override
    public void close() {
        //no op
    }

    @Override
    public long startIndex() {
        return Reader.NULL_INDEX;
    }

    @Override
    public Iterable<? extends Entry> iterate(final Direction direction) {
        requireNonNull(direction);
        return Collections.emptyList();
    }
}
