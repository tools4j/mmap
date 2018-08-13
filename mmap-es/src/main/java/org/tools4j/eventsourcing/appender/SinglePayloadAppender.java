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

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.tools4j.eventsourcing.api.IndexedMessageConsumer;
import org.tools4j.eventsourcing.sbe.MessageHeaderEncoder;
import org.tools4j.eventsourcing.sbe.SinglePayloadEncoder;

import java.util.Objects;

/**
 * Appender that encodes given message with SBE SinglePayloadEncoder and delegates appending of encoded message to
 * delegateAppender.
 */
public final class SinglePayloadAppender implements IndexedMessageConsumer {

    private final IndexedMessageConsumer delegateAppender;
    private final MutableDirectBuffer messageEncodingBuffer;

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final SinglePayloadEncoder singlePayloadEncoder = new SinglePayloadEncoder();

    public SinglePayloadAppender(final IndexedMessageConsumer delegateAppender,
                                 final MutableDirectBuffer messageEncodingBuffer) {
        this.delegateAppender = Objects.requireNonNull(delegateAppender);
        this.messageEncodingBuffer = Objects.requireNonNull(messageEncodingBuffer);
    }

    @Override
    public void accept(final int source,
                       final long sourceId,
                       final long eventTimeNanos,
                       final DirectBuffer buffer,
                       final int offset,
                       final int length) {

        final int headerLength = messageHeaderEncoder.wrap(messageEncodingBuffer, 0)
                .blockLength(SinglePayloadEncoder.BLOCK_LENGTH)
                .schemaId(SinglePayloadEncoder.SCHEMA_ID)
                .version(SinglePayloadEncoder.SCHEMA_VERSION)
                .templateId(SinglePayloadEncoder.TEMPLATE_ID)
                .encodedLength();

        int messageLength = singlePayloadEncoder.wrap(messageEncodingBuffer, headerLength)
                .putValue(buffer, offset, length)
                .encodedLength() + headerLength;

        //messageLength = BitUtil.align(messageLength, 64);

        delegateAppender.accept(source, sourceId, eventTimeNanos, messageEncodingBuffer, 0, messageLength);
    }
}
