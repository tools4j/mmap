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
package org.tools4j.mmap.queue.api;

/**
 * Random access reading interface for entries in the {@link LongQueue}.
 */
public interface LongReader extends Reader {
    /**
     * Entry in the long-queue, with data accessible through {@link #value()}.
     */
    interface LongEntry extends Entry {
        /**
         * @return the entry value
         */
        long value();
    }

    /**
     * Reading context with access to entry data and index if the requested long-queue entry was available; otherwise
     * {@link #value()} will return {@link LongQueue#nullValue()}.
     */
    interface LongReadingContext extends LongEntry, ReadingContext {
        /**
         * @return non-negative index if entry is available, {@link #NULL_INDEX} otherwise
         */
        @Override
        long index();
        /**
         * @return the entry value if an entry is available, otherwise {@link LongQueue#nullValue()}
         */
        @Override
        long value();
    }

    /**
     * Iterable context to iterate over long-queue entries.
     */
    interface LongIterableContext extends IterableContext {
        @Override
        Iterable<? extends LongEntry> iterate(Direction direction);
    }

    @Override
    LongReadingContext reading(long index);

    @Override
    LongReadingContext readingFirst();

    @Override
    LongReadingContext readingLast();

    @Override
    LongIterableContext readingFrom(long index);

    @Override
    LongIterableContext readingFromFirst();

    @Override
    LongIterableContext readingFromLast();
}
