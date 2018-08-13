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
