/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2018 mmap (tools4j), Marco Terzer, Anton Anufriev
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
package org.tools4j.eventsourcing.queue;

import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.eventsourcing.api.*;
import org.tools4j.eventsourcing.appender.IndexedAppender;
import org.tools4j.eventsourcing.appender.MultiPayloadAppender;
import org.tools4j.mmap.region.api.RegionRingFactory;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DefaultIndexedTransactionalQueue implements IndexedTransactionalQueue {
    private final Transaction appender;
    private final IndexedPollerFactory pollerFactory;

    public DefaultIndexedTransactionalQueue(final String directory,
                                            final String filePrefix,
                                            final boolean clearFiles,
                                            final RegionRingFactory regionRingFactory,
                                            final int regionSize,
                                            final int regionRingSize,
                                            final int regionsToMapAhead,
                                            final long maxFileSize,
                                            final int encodingBufferSize) throws IOException {

        this.appender = new MultiPayloadAppender(
                            new IndexedAppender(
                                RegionAccessorSupplier.forReadWrite(
                                    directory,
                                    filePrefix,
                                    clearFiles,
                                    regionRingFactory,
                                    regionSize,
                                    regionRingSize,
                                    regionsToMapAhead,
                                    maxFileSize)),
                new UnsafeBuffer(ByteBuffer.allocateDirect(encodingBufferSize)));

        this.pollerFactory = new DefaultIndexedPollerFactory(
                directory,
                filePrefix,
                regionRingFactory,
                regionSize,
                regionRingSize,
                regionsToMapAhead);
    }

    @Override
    public Transaction appender() {
        return this.appender;
    }

    @Override
    public Poller createPoller(final Poller.IndexPredicate skipPredicate,
                               final Poller.IndexPredicate pausePredicate,
                               final Poller.IndexConsumer beforeIndexHandler,
                               final Poller.IndexConsumer afterIndexHandler) throws IOException {
        return pollerFactory.createPoller(
                skipPredicate,
                pausePredicate,
                beforeIndexHandler,
                afterIndexHandler);
    }
}
