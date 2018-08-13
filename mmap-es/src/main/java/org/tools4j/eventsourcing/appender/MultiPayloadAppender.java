package org.tools4j.eventsourcing.appender;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.tools4j.eventsourcing.api.IndexedMessageConsumer;
import org.tools4j.eventsourcing.api.Transaction;
import org.tools4j.eventsourcing.sbe.MessageHeaderEncoder;
import org.tools4j.eventsourcing.sbe.MultiPayloadEncoder;

import java.util.Objects;

/**
 * Appender that encodes multiple messages with SBE MultiPayloadEncoder and delegates appending of the encoded message to
 * delegateAppender.
 */
public final class MultiPayloadAppender implements Transaction {
    private static final int MAX_ENTRIES = 10;

    private final IndexedMessageConsumer delegateAppender;
    private final MutableDirectBuffer messageEncodingBuffer;

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MultiPayloadEncoder multiPayloadEncoder = new MultiPayloadEncoder();

    private int source;
    private long sourceId;
    private long eventTimeNanos;
    private int messageLength;
    private int entries;
    private int limitBeforeEntries;
    private MultiPayloadEncoder.EntriesEncoder entriesEncoder;

    public MultiPayloadAppender(final IndexedMessageConsumer delegateAppender,
                                final MutableDirectBuffer messageEncodingBuffer) {
        this.delegateAppender = Objects.requireNonNull(delegateAppender);
        this.messageEncodingBuffer = Objects.requireNonNull(messageEncodingBuffer);
    }

    @Override
    public void init(final int source, final long sourceId, final long eventTimeNanos) {
        this.entries = 0;
        this.source  = source;
        this.sourceId = sourceId;
        this.eventTimeNanos = eventTimeNanos;

        final int headerLength = messageHeaderEncoder.wrap(messageEncodingBuffer, 0)
                .blockLength(MultiPayloadEncoder.BLOCK_LENGTH)
                .schemaId(MultiPayloadEncoder.SCHEMA_ID)
                .version(MultiPayloadEncoder.SCHEMA_VERSION)
                .templateId(MultiPayloadEncoder.TEMPLATE_ID)
                .encodedLength();

        messageLength = headerLength;
        multiPayloadEncoder.wrap(messageEncodingBuffer, headerLength);
        limitBeforeEntries = multiPayloadEncoder.limit();

        entriesEncoder = multiPayloadEncoder.entriesCount(MAX_ENTRIES);
    }

    @Override
    public void commit() {
        if (entries > 0) {
            final int saveLimit = multiPayloadEncoder.limit();
            multiPayloadEncoder.limit(limitBeforeEntries);
            multiPayloadEncoder.entriesCount(entries);
            multiPayloadEncoder.limit(saveLimit);

            messageLength += multiPayloadEncoder.encodedLength();
            //messageLength = BitUtil.align(messageLength, 64);
            delegateAppender.accept(source, sourceId, eventTimeNanos, messageEncodingBuffer, 0, messageLength);
        }
    }

    @Override
    public void accept(final DirectBuffer buffer, final int offset, final int length) {
        entriesEncoder = entriesEncoder.next().putValue(buffer, offset, length);
        entries++;
    }
}
