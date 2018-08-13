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
package org.tools4j.eventsourcing.appender;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.tools4j.eventsourcing.api.RegionAccessorSupplier;
import org.tools4j.eventsourcing.api.IndexedMessageConsumer;
import org.tools4j.eventsourcing.sbe.IndexDecoder;
import org.tools4j.eventsourcing.sbe.IndexEncoder;

import java.util.Objects;

/**
 * An appender that appends index and message details to two separate memory-mapped-files.
 * As the index has fixed length it enables random access to variable length messages located in another memory-mapped-file
 * by having two random access lookups:
 *  - first random access lookup of index and position/length of message located in another memory-mapped-file
 *  - second random access lookup of the message located at position/length in another memory-mapped-file
 *  Appendable message is represented as a buffer at offset with length.
 *  Length of a message is a first field in the index record which has a volatile semantic for thread synchronisation.
 */
public final class IndexedAppender implements IndexedMessageConsumer {
    private static final long NOT_INITIALISED = -1;
    private static final int LENGTH_OFFSET = 0;
    private static final int LENGTH_LENGTH = 4;
    private static final int INDEX_OFFSET = LENGTH_OFFSET + LENGTH_LENGTH;
    private static final int INDEX_LENGTH = LENGTH_LENGTH + IndexEncoder.ENCODED_LENGTH;

    private final RegionAccessorSupplier regionAccessorSupplier;

    private final UnsafeBuffer mappedIndexBuffer;
    private final UnsafeBuffer mappedMessageBuffer;

    private final IndexEncoder indexEncoder = new IndexEncoder();
    private final IndexDecoder indexDecoder = new IndexDecoder();

    private long currentIndexPosition = NOT_INITIALISED;
    private long currentMessagePosition = 0;

    public IndexedAppender(final RegionAccessorSupplier regionAccessorSupplier) {
        this.regionAccessorSupplier = Objects.requireNonNull(regionAccessorSupplier);

        this.mappedIndexBuffer = new UnsafeBuffer();
        this.mappedMessageBuffer = new UnsafeBuffer();
    }

    @Override
    public void accept(final int source, final long sourceId, final long eventTimeNanos, final DirectBuffer buffer, final int offset, final int length) {
        advanceIndexToLastAppendPosition();

        if (regionAccessorSupplier.messageAccessor().wrap(currentMessagePosition, mappedMessageBuffer)) {
            if (regionAccessorSupplier.indexAccessor().wrap(currentIndexPosition, mappedIndexBuffer)) {

                if (mappedMessageBuffer.capacity() < length) {
                    currentMessagePosition += mappedMessageBuffer.capacity();
                    if (!regionAccessorSupplier.messageAccessor().wrap(currentMessagePosition, mappedMessageBuffer)) {
                        throw new IllegalStateException("Failed to wrap message buffer for position " + currentMessagePosition);
                    }
                }
                buffer.getBytes(offset, mappedMessageBuffer, 0, length);

                indexEncoder.wrap(mappedIndexBuffer, INDEX_OFFSET)
                        .position(currentMessagePosition)
                        .source(source)
                        .sourceId(sourceId)
                        .eventTimeNanos(eventTimeNanos);

                mappedIndexBuffer.putIntOrdered(LENGTH_OFFSET, length);

                advanceIndexToNextAppendPosition(length);
            } else {
                throw new IllegalStateException("Failed to wrap index buffer for position " + currentIndexPosition);
            }
        } else {
            throw new IllegalStateException("Failed to wrap body buffer for position " + currentMessagePosition);
        }
    }

    private void advanceIndexToNextAppendPosition(int messageLength) {
        currentIndexPosition += INDEX_LENGTH;
        currentMessagePosition += messageLength;
    }

    private void advanceIndexToLastAppendPosition() {
        if (currentIndexPosition == NOT_INITIALISED) {
            currentIndexPosition = 0;
            int currentMessageLength;
            do {
                if (regionAccessorSupplier.indexAccessor().wrap(currentIndexPosition, mappedIndexBuffer)) {
                    if ((currentMessageLength = mappedIndexBuffer.getInt(LENGTH_OFFSET)) > 0) {
                        indexDecoder.wrap(mappedIndexBuffer, INDEX_OFFSET);
                        currentMessagePosition = indexDecoder.position();
                        advanceIndexToNextAppendPosition(currentMessageLength);
                    }
                } else {
                    throw new IllegalStateException("Failed to wrap index buffer to position " + currentIndexPosition);
                }
            } while (currentMessageLength != 0);
        }
    }
}
