/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2023 tools4j.org (Marco Terzer, Anton Anufriev)
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

import org.tools4j.mmap.queue.impl.LongQueueBuilder;
import org.tools4j.mmap.region.api.RegionMapperFactory;

/**
 * A queue of long values.
 */
public interface LongQueue extends AutoCloseable {
    /**
     * Default value used for null entry value.
     */
    long DEFAULT_NULL_VALUE = 0;

    /**
     * Value used for null entries by this queue.
     * @return the null entry value
     */
    long nullValue();

    /**
     * Creates an appender.
     *
     * @return new instance of an appender
     */
    LongAppender createAppender();

    /**
     * Creates a poller.
     *
     * @return new instance of a poller.
     */
    LongPoller createPoller();

    /**
     * Creates a reader.
     *
     * @return new instance of a reader.
     */
    LongReader createReader();

    static LongQueueBuilder builder(final String name, final String directory, final RegionMapperFactory mapperFactory) {
        return new LongQueueBuilder(name, directory, mapperFactory);
    }

    /**
     * Closes the queue and all appender, pollers and readers.
     */
    @Override
    void close();
}
